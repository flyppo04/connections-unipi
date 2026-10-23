package com.unipi.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/* Thread daemon che ascolta le notifiche UDP asincrone dal server
Gestisce due tipi di messaggi (coerenti con ServerMain):
  - "GAME_END"  → fine partita: soluzione, vincitori, statistiche
  - "newGame"   → nuova partita iniziata: nuove parole, gameId, tempo
Stampa le notifiche a schermo in modo non invasivo
La porta UDP viene comunicata al server durante il login */

public class UdpListener implements Runnable {

    private static final int BUFFER_SIZE = 8192;

    private       DatagramSocket socket;
    private volatile boolean     running = true;

    // Callback per aggiornare lo stato del CLI quando arriva newGame
    private volatile NewGameHandler newGameHandler;

    @FunctionalInterface
    public interface NewGameHandler {
        void onNewGame(JsonObject gameData);
    }

    public UdpListener(int udpPort) throws SocketException {
        this.socket  = new DatagramSocket(udpPort);
        System.out.println("[UDP] In ascolto sulla porta " + udpPort);
    }

    public void setNewGameHandler(NewGameHandler handler) {
        this.newGameHandler = handler;
    }

    @Override
    public void run() {
        byte[] buf = new byte[BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet); // bloccante — va bene perché è un thread dedicato

                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                JsonObject msg = JsonParser.parseString(raw).getAsJsonObject();

                String type = msg.has("type") ? msg.get("type").getAsString() : "";

                switch (type) {
                    case "GAME_END" -> handleGameEnd(msg);
                    case "newGame"  -> handleNewGame(msg);
                    default         -> System.out.println("\n[UDP] Messaggio sconosciuto: " + raw);
                }

            } catch (SocketException e) {
                if (running) System.err.println("[UDP] Socket chiuso: " + e.getMessage());
            } catch (IOException e) {
                if (running) System.err.println("[UDP] Errore ricezione: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[UDP] Errore parsing: " + e.getMessage());
            }
        }
    }

    private void handleGameEnd(JsonObject msg) {
        System.out.println("\n========================================");
        System.out.println(  "          PARTITA TERMINATA!            ");
        System.out.println(  "========================================");

        if (msg.has("totalPlayers"))    System.out.println("  Giocatori totali  : " + msg.get("totalPlayers").getAsInt());
        if (msg.has("playersFinished")) System.out.println("  Hanno finito      : " + msg.get("playersFinished").getAsInt());
        if (msg.has("playersWon"))      System.out.println("  Vincitori         : " + msg.get("playersWon").getAsInt());
        if (msg.has("averageScore"))    System.out.printf( "  Punteggio medio   : %.1f%n", msg.get("averageScore").getAsFloat());

        if (msg.has("winners") && msg.get("winners").getAsJsonArray().size() > 0) {
            System.out.print("  Vincitori         : ");
            msg.get("winners").getAsJsonArray().forEach(w -> System.out.print(w.getAsString() + " "));
            System.out.println();
        }

        if (msg.has("solution")) {
            System.out.println("  Soluzione:");
            msg.get("solution").getAsJsonObject().entrySet().forEach(entry -> {
                System.out.print("    [" + entry.getKey() + "] → ");
                entry.getValue().getAsJsonArray().forEach(w -> System.out.print(w.getAsString() + " "));
                System.out.println();
            });
        }
        System.out.println();
    }

    private void handleNewGame(JsonObject msg) {
        System.out.println("\n========================================");
        System.out.println(  "        NUOVA PARTITA INIZIATA!         ");
        System.out.println(  "========================================");
        if (msg.has("gameId"))        System.out.println("  Game ID           : " + msg.get("gameId").getAsInt());
        if (msg.has("remainingTime")) System.out.println("  Tempo disponibile : " + msg.get("remainingTime").getAsLong() + "s");
        if (msg.has("words")) {
            System.out.print("  Parole            : ");
            msg.get("words").getAsJsonArray().forEach(w -> System.out.print(w.getAsString() + " "));
            System.out.println();
        }
        System.out.println();

        // Notifica il CLI per aggiornare lo stato locale
        NewGameHandler handler = newGameHandler;
        if (handler != null) handler.onNewGame(msg);
    }

    public void shutdown() {
        running = false;
        socket.close();
    }
}
