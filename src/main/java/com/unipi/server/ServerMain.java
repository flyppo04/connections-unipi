package com.unipi.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import com.unipi.data.PuzzleReader;
import com.unipi.game.GameManager;
import com.unipi.game.UserManager;
import com.unipi.models.Game;
import com.unipi.models.User;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Punto di ingresso del server.
 *
 * Responsabilità di questa classe (SOLO infrastruttura):
 *   - leggere la configurazione da file (PDF §4)
 *   - inizializzare i componenti
 *   - gestire il loop NIO Selector su un thread dedicato
 *   - passare i messaggi al CommandHandler tramite thread pool (PDF §3)
 *   - inviare notifiche UDP asincrone di fine partita (PDF §3)
 *   - persistere gli utenti su JSON
 *   - shutdown ordinato
 *
 * Il parsing e la logica applicativa sono in CommandHandler.
 */
public class ServerMain {

    // -------------------------------------------------------------------------
    // Configurazione — letta da server.config (PDF §4)
    // -------------------------------------------------------------------------
    private final int    tcpPort;
    private final long   gameDurationSeconds;
    private final String puzzleFile;
    private final String usersFile;
    private final String gamesDir;
    private final int    bufferSize;
    private final int    numWorkers;

    // -------------------------------------------------------------------------
    // Componenti
    // -------------------------------------------------------------------------
    private final UserManager    userManager;
    private final GameManager    gameManager;
    private final CommandHandler commandHandler;
    private final PersistenceManager persistence;
    private final Gson           gson = new Gson();

    // -------------------------------------------------------------------------
    // Rete
    // -------------------------------------------------------------------------
    private ServerSocketChannel serverChannel;
    private Selector            selector;
    private DatagramSocket      udpSocket;

    // Usato solo nel thread del selectionLoop → nessuna sincronizzazione necessaria
    private ByteBuffer readBuffer;

    /**
     * Mappa canale TCP → porta UDP del client.
     * ConcurrentHashMap: acceduta dal selectionLoop (login/logout)
     * e dallo scheduler di GameManager (broadcastGameResult).
     */
    private final Map<SocketChannel, Integer> udpPorts = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    // =========================================================================
    // COSTRUTTORE
    // =========================================================================
    public ServerMain() throws IOException {
        Properties config = loadConfig("server.config");

        this.tcpPort             = Integer.parseInt(config.getProperty("server.tcp.port",              "5000"));
        this.gameDurationSeconds = Long.parseLong  (config.getProperty("server.game.duration.seconds", "120"));
        this.puzzleFile          =                  config.getProperty("server.puzzle.file",           "puzzles.json");
        this.usersFile           =                  config.getProperty("server.users.file",            "users.json");
        this.gamesDir            =                  config.getProperty("server.games.directory",       "./data/games");
        this.bufferSize          = Integer.parseInt(config.getProperty("server.buffer.size",           "4096"));
        this.numWorkers          = Integer.parseInt(config.getProperty("server.workers",               "4"));

        List<User> persisted = loadUsers();
        this.userManager = persisted.isEmpty()
                ? UserManager.createNew()
                : UserManager.fromPersistedUsers(persisted);

        PuzzleReader puzzleReader = new PuzzleReader(puzzleFile);
        this.persistence = new PersistenceManager(usersFile, gamesDir, userManager);
        this.gameManager = new GameManager(puzzleReader, userManager, gameDurationSeconds, persistence);
        this.gameManager.setGameEndCallback(this::onGameEnd);
        this.gameManager.setNewGameCallback(this::onNewGame); 

        // CommandHandler condiviso tra tutti i worker thread
        this.commandHandler = new CommandHandler(userManager, gameManager, udpPorts, persistence);
    }

    // =========================================================================
    // AVVIO
    // =========================================================================
    public void start() throws IOException {
        readBuffer = ByteBuffer.allocate(bufferSize);

        ExecutorService workerPool = Executors.newFixedThreadPool(numWorkers);

        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(tcpPort));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Nessun bind: questo socket serve solo per inviare notifiche UDP
        udpSocket = new DatagramSocket();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            workerPool.shutdown();
            try { workerPool.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { workerPool.shutdownNow(); }
            gameManager.shutdown();
            saveUsers();
            try { if (selector      != null) selector.close();      } catch (IOException ignored) {}
            try { if (serverChannel != null) serverChannel.close(); } catch (IOException ignored) {}
            if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
            System.out.println("[SERVER] Shutdown completato.");
        }));

        gameManager.startNewGame();

        System.out.println("[SERVER] TCP porta " + tcpPort
                + " | UDP pronto | Workers: " + numWorkers);

        // Loop NIO — unico thread che tocca selector e readBuffer
        while (running) {
            selector.select(1000);
            if (!running) break;

            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (!key.isValid()) continue;

                if      (key.isAcceptable()) handleAccept(key);
                else if (key.isReadable())   handleRead(key, workerPool);
            }
        }
    }

    // =========================================================================
    // NIO — accept e read
    // =========================================================================

    private static class ClientSession {
        final StringBuilder partialMessage = new StringBuilder();
    }


    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel client = ((ServerSocketChannel) key.channel()).accept();
        if (client == null) return;
        client.configureBlocking(false);
        
        ClientSession session = new ClientSession();
        client.register(selector, SelectionKey.OP_READ, session);  // ← 1 riga!
        
        System.out.println("[ACCEPT] " + client.getRemoteAddress());
    }

    /**
     * Legge il messaggio e lo consegna al worker pool.
     *
     * NIO pattern: il selectionLoop fa SOLO la read (veloce).
     * L'elaborazione (parse JSON + logica) va su un worker thread.
     *
     * gestione con sistema per-client con delimitatore \n.
     */
    private void handleRead(SelectionKey key, ExecutorService workerPool) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();

        readBuffer.clear();

        int bytesRead;
        try {
            bytesRead = client.read(readBuffer);
        } catch (IOException e) {
            disconnect(key, client);
            return;
        }

        if (bytesRead == -1) {
            disconnect(key, client);
            return;
        }

        if (bytesRead == 0) {
            return;
        }

        readBuffer.flip();
        byte[] data = new byte[readBuffer.remaining()];
        readBuffer.get(data);

        String chunk = new String(data, StandardCharsets.UTF_8);
        session.partialMessage.append(chunk);

        String message;
        while ((message = extractCompleteMessage(session.partialMessage)) != null) {
            if (message.isEmpty()) continue;

            System.out.println("[IN] " + message);

            final String msg = message;
            workerPool.submit(() -> {
                String response = commandHandler.process(msg, client);
                sendTcp(client, response);
            });
        }
    }

    private String extractCompleteMessage(StringBuilder buffer) {
        int newlineIndex = buffer.indexOf("\n");
        if (newlineIndex == -1) {
            return null;
        }

        String message = buffer.substring(0, newlineIndex).trim();
        buffer.delete(0, newlineIndex + 1);
        return message;
    }

    private void disconnect(SelectionKey key, SocketChannel channel) {
        System.out.println("[DISCONNECT] " + safeAddr(channel));
        userManager.logout(channel);
        udpPorts.remove(channel);
        key.cancel();
        try { channel.close(); } catch (IOException ignored) {}
    }

    // =========================================================================
    // UDP — notifica fine partita
    // =========================================================================

    /**
     * Callback chiamato dallo scheduler di GameManager su un thread separato.
     * Invia via UDP la notifica di fine partita + soluzione a tutti i client
     * che hanno registrato un endpoint UDP al momento del login (PDF §3).
     */
    private void onGameEnd(Game endedGame) {
        System.out.println("[GAME END] Partita " + endedGame.getGameId());
        persistence.saveCompletedGame(endedGame); 
        broadcastUdp(endedGame);
        saveUsers();
    }

    private void onNewGame(Game newGame) {
        for (SocketChannel ch : userManager.getLoggedInChannels()) {

            User u = userManager.getUserByChannel(ch);

            if (u == null) continue;

            newGame.addPlayer(u.getUsername());

            sendNewGameNotification(ch, u, newGame);

        }
    }

    private void broadcastUdp(Game game) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",           "GAME_END");
        msg.addProperty("gameId",          game.getGameId());
        msg.addProperty("totalPlayers",    game.getTotalPlayers());
        msg.addProperty("playersFinished", game.getFinishedPlayersCount());
        msg.addProperty("playersWon",      game.getWinnerPlayersCount());
        msg.addProperty("averageScore",    game.getAverageScore());

        JsonArray winners = new JsonArray();
        game.getWinnerPlayers().forEach(winners::add);
        msg.add("winners", winners);

        JsonObject solution = new JsonObject();
        game.getConnections().forEach((theme, words) -> {
            JsonArray wa = new JsonArray();
            words.forEach(wa::add);
            solution.add(theme, wa);
        });
        msg.add("solution", solution);

        byte[] data = msg.toString().getBytes();

        for (SocketChannel ch : userManager.getLoggedInChannels()) {
            Integer port = udpPorts.get(ch);
            if (port == null) continue;
            try {
                InetSocketAddress tcpAddr = (InetSocketAddress) ch.getRemoteAddress();
                udpSocket.send(new DatagramPacket(
                        data, data.length, tcpAddr.getAddress(), port));
            } catch (IOException e) {
                System.err.println("[UDP ERR] " + e.getMessage());
            }
        }
    }

    private void sendNewGameNotification(SocketChannel ch, User user, Game game) {
        try {
            Integer udpPort = udpPorts.get(ch);
            if (udpPort == null) {
                System.out.println("[UDP] porta non registrata per " + user.getUsername());
                return;
            }

            InetSocketAddress remote = (InetSocketAddress) ch.getRemoteAddress();
            InetAddress clientAddress = remote.getAddress();

            String message = buildNewGameNotification(user, game);
            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress, udpPort);
            udpSocket.send(packet);

            System.out.println("[UDP NEW GAME] inviato a " + user.getUsername() +
                    " " + clientAddress.getHostAddress() + ":" + udpPort);

        } catch (IOException e) {
            System.err.println("[UDP ERROR] impossibile notificare nuova partita: " + e.getMessage());
        }
    }

    private String buildNewGameNotification(User user, Game game) {
        List<String> words = game.getWords(); 
        // oppure game.getWords(), in base a come hai modellato Game

        long remainingSeconds = game.getRemainingTimeInSeconds();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"newGame\",");
        sb.append("\"gameId\":").append(game.getGameId()).append(",");
        sb.append("\"remainingTime\":").append(remainingSeconds).append(",");
        sb.append("\"errors\":0,");
        sb.append("\"score\":0,");
        sb.append("\"correctGroups\":0,");
        sb.append("\"words\":[");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(words.get(i))).append("\"");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================================
    // TCP — invio risposta
    // =========================================================================

    /**
     * synchronized(channel): necessario perché più worker thread potrebbero
     * tentare di scrivere sullo stesso canale contemporaneamente.
     */
    private void sendTcp(SocketChannel channel, String response) {
        try {
            ByteBuffer buf = ByteBuffer.wrap((response + "\n").getBytes());
            synchronized (channel) {
                channel.write(buf);

                if (buf.hasRemaining()) {
                    System.err.println("[TCP WARN] Scrittura parziale verso " + safeAddr(channel));   
                }
            }
            System.out.println("[OUT] " + response);
        } catch (IOException e) {
            System.err.println("[TCP ERR] " + e.getMessage());
        }
    }

    // =========================================================================
    // CONFIGURAZIONE E PERSISTENZA
    // =========================================================================

    /**
     * Carica server.config (formato Properties Java standard).
     * Se il file non esiste usa i valori di default dichiarati nelle chiamate
     * a getProperty(..., "default"), quindi il server parte comunque.
     *
     * PDF §4: "i parametri devono essere letti da appositi file di configurazione"
     *
     * Esempio server.config:
     *   server.tcp.port=5000
     *   server.game.duration.seconds=120
     *   server.puzzle.file=puzzles.json
     *   server.users.file=users.json
     *   server.buffer.size=4096
     *   server.workers=4
     */
    private static Properties loadConfig(String path) {
        System.out.println("Java sta cercando file nella cartella: " + new File(".").getAbsolutePath());
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(path)) {
            p.load(in);
            System.out.println("[CONFIG] Caricata da " + path);
        } catch (FileNotFoundException e) {
            System.out.println("[CONFIG] " + path + " non trovato, uso default.");
        } catch (IOException e) {
            System.err.println("[CONFIG] Errore lettura: " + e.getMessage());
        }
        return p;
    }

    private List<User> loadUsers() {
        File f = new File(usersFile);
        if (!f.exists()) { System.out.println("[PERSIST] Primo avvio."); return new ArrayList<>(); }
        try (Reader r = new FileReader(f)) {
            Type t = new TypeToken<List<User>>() {}.getType();
            List<User> list = gson.fromJson(r, t);
            System.out.println("[PERSIST] Caricati " + (list != null ? list.size() : 0) + " utenti.");
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[PERSIST] Errore lettura utenti: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveUsers() {
        List<User> snapshot = new ArrayList<>(userManager.getAllUsers());
        try (Writer w = new FileWriter(usersFile)) {
            gson.toJson(snapshot, w);
            System.out.println("[PERSIST] Salvati " + snapshot.size() + " utenti.");
        } catch (IOException e) {
            System.err.println("[PERSIST] Errore salvataggio: " + e.getMessage());
        }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private String safeAddr(SocketChannel ch) {
        try { return ch.getRemoteAddress().toString(); }
        catch (IOException e) { return "unknown"; }
    }

    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) {
        try {
            new ServerMain().start();
        } catch (RuntimeException e) {
            System.err.println("[FATAL] Errore di avvio server: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("[FATAL] Errore I/O server: " + e.getMessage());
            System.exit(1);
        }
    }
}