package com.unipi.client;

import com.google.gson.*;

import java.io.IOException;
import java.util.*;

/* Interfaccia a linea di comando del client Connections
Gestisce tutto il flusso interattivo: menu, input utente,
formattazione risposte. Usa ServerConnection per il TCP
e UdpListener per le notifiche asincrone
Stato locale mantenuto:
  - loggedInUser: username corrente (null se non loggato)
  - currentGameId: id partita in corso (-1 = nessuna)
  - currentWords: parole rimanenti da raggruppare
  - udpPort: porta UDP registrata al login  */

public class ClientCLI {

    private final ServerConnection connection;
    private final UdpListener      udpListener;
    private final Scanner          scanner = new Scanner(System.in);
    private final int              udpPort;

    // Stato locale del client
    private String       loggedInUser  = null;
    private int          currentGameId = -1;
    private List<String> currentWords  = new ArrayList<>();
    private int          currentErrors = 0;
    private int          currentScore  = 0;

    public ClientCLI(ServerConnection connection, UdpListener udpListener, int udpPort) {
        this.connection  = connection;
        this.udpListener = udpListener;
        this.udpPort     = udpPort;

        // Quando arriva una nuova partita via UDP, aggiorna lo stato locale
        udpListener.setNewGameHandler(this::applyNewGameState);
    }

    // LOOP PRINCIPALE

    public void run() {
        printBanner();
        boolean active = true;

        while (active) {
            try {
                if (loggedInUser == null) {
                    active = menuNotLogged();
                } else {
                    active = menuLogged();
                }
            } catch (IOException e) {
                System.err.println("[ERRORE] Connessione persa: " + e.getMessage());
                active = false;
            }
        }

        connection.close();
        udpListener.shutdown();
        System.out.println("Arrivederci!");
    }

    // MENU NON LOGGATO

    private boolean menuNotLogged() throws IOException {
        System.out.println("\n--- MENU PRINCIPALE ---");
        System.out.println("1. Registrati");
        System.out.println("2. Login");
        System.out.println("3. Aggiorna credenziali");
        System.out.println("0. Esci");
        System.out.print("> ");

        String choice = scanner.nextLine().trim();
        return switch (choice) {
            case "1" -> { doRegister();           yield true; }
            case "2" -> { doLogin();              yield true; }
            case "3" -> { doUpdateCredentials();  yield true; }
            case "0" -> false;
            default  -> { System.out.println("Scelta non valida."); yield true; }
        };
    }

    // MENU LOGGATO

    private boolean menuLogged() throws IOException {
        System.out.println("\n--- MENU GIOCO [" + loggedInUser + "] ---");
        System.out.println("1.   Invia proposta");
        System.out.println("2.   Stato personale partita corrente");
        System.out.println("3.   Info personale partita passata (per ID)");
        System.out.println("4.   Statistiche generali partita corrente");
        System.out.println("5.   Statistiche generali partita passata (per ID)");
        System.out.println("6.   Classifica completa");
        System.out.println("7.   Top K giocatori");
        System.out.println("8.   Posizione in classifica di un giocatore");
        System.out.println("9.   Le mie statistiche");
        System.out.println("10.  Aggiorna credenziali");
        System.out.println("11.  Logout");
        System.out.println("0.   Esci");
        System.out.print("> ");

        String choice = scanner.nextLine().trim();
        return switch (choice) {
            case "1" -> { doSubmitProposal();       yield true; }
            case "2" -> { doGameInfo(-1);           yield true; }
            case "3" -> { doGameInfoById();         yield true; }
            case "4" -> { doGameStats(-1);          yield true; }
            case "5" -> { doGameStatsById();        yield true; }
            case "6" -> { doLeaderboard(null, 0);   yield true; }
            case "7" -> { doTopK();                 yield true; }
            case "8" -> { doPlayerRank();           yield true; }
            case "9" -> { doPlayerStats();          yield true; }
            case "10" -> { doUpdateCredentials();    yield true; }
            case "11" -> { doLogout();               yield true; }
            case "0" -> { doLogout();               yield false; }
            default  -> { System.out.println("Scelta non valida."); yield true; }
        };
    }

    // HANDLER — gestione utente

    private void doRegister() throws IOException {
        System.out.print("Username: ");
        String name = scanner.nextLine().trim();
        System.out.print("Password: ");
        String psw = scanner.nextLine().trim();

        JsonObject req = new JsonObject();
        req.addProperty("operation", "register");
        req.addProperty("name",      name);
        req.addProperty("psw",       psw);

        JsonObject res = connection.sendAndReceive(req);
        if (isOk(res)) {
            System.out.println("Registrazione completata. Ora puoi fare il login.");
        } else {
            printError(res);
        }
    }

    private void doLogin() throws IOException {
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String psw = scanner.nextLine().trim();

        JsonObject req = new JsonObject();
        req.addProperty("operation", "login");
        req.addProperty("username",  username);
        req.addProperty("psw",       psw);
        req.addProperty("udpPort",   udpPort); // registra porta UDP per notifiche asincrone

        JsonObject res = connection.sendAndReceive(req);
        if (isOk(res)) {
            loggedInUser = username;
            System.out.println("Login effettuato come " + username);
            applyLoginState(res);
        } else {
            printError(res);
        }
    }

    private void doLogout() throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("operation", "logout");

        JsonObject res = connection.sendAndReceive(req);
        if (isOk(res)) {
            System.out.println("Logout effettuato.");
        } else {
            printError(res);
        }
        // Reset stato locale in ogni caso
        loggedInUser  = null;
        currentGameId = -1;
        currentWords  = new ArrayList<>();
        currentErrors = 0;
        currentScore  = 0;
    }

    private void doUpdateCredentials() throws IOException {
        System.out.print("Password attuale: ");
        String oldPsw = scanner.nextLine().trim();
        System.out.print("Nuovo username (invio per non cambiare): ");
        String newName = scanner.nextLine().trim();
        System.out.print("Nuova password (invio per non cambiare): ");
        String newPsw = scanner.nextLine().trim();

        if (newName.isEmpty() && newPsw.isEmpty()) {
            System.out.println("Nessuna modifica richiesta.");
            return;
        }

        JsonObject req = new JsonObject();
        req.addProperty("operation", "updateCredentials");
        req.addProperty("oldPsw",    oldPsw);
        if (!newName.isEmpty()) req.addProperty("newName", newName);
        if (!newPsw.isEmpty())  req.addProperty("newPsw",  newPsw);

        JsonObject res = connection.sendAndReceive(req);
        if (isOk(res)) {
            System.out.println("✓ Credenziali aggiornate.");
            if (!newName.isEmpty()) loggedInUser = newName; // aggiorna stato locale
        } else {
            printError(res);
        }
    }


    // HANDLER — partita

    private void doSubmitProposal() throws IOException {
        if (currentWords.isEmpty()) {
            System.out.println("Nessuna parola disponibile. Stato partita:");
            doGameInfo(-1);
            return;
        }

        System.out.println("Parole rimaste: " + currentWords);
        System.out.println("Inserisci 4 parole separate da virgola:");
        System.out.print("> ");
        String input = scanner.nextLine().trim();
        String[] parts = input.split(",");

        if (parts.length != 4) {
            System.out.println("Devi inserire esattamente 4 parole.");
            return;
        }

        JsonArray words = new JsonArray();
        for (String p : parts) words.add(p.trim().toUpperCase());

        JsonObject req = new JsonObject();
        req.addProperty("operation", "submitProposal");
        req.add("words", words);

        JsonObject res = connection.sendAndReceive(req);
        if (isOk(res)) {
            boolean correct = res.has("correct") && res.get("correct").getAsBoolean();
            if (correct) {
                System.out.println(" Proposta CORRETTA! per visualizzare i temi indovinati visualizza stato partita (2).");
                // Rimuovi le parole trovate dalla lista locale
                for (JsonElement w : words) {
                    currentWords.remove(w.getAsString());
                }
            } else {
                currentErrors = getInt(res, "mistakes", currentErrors);
                System.out.println(" Proposta ERRATA. Errori: " + currentErrors);
            }
            currentScore = getInt(res, "score", currentScore);
            System.out.printf("  Punteggio corrente: %d%n", currentScore);

            if (res.has("hasFinished") && res.get("hasFinished").getAsBoolean()) {
                boolean winner = res.has("isWinner") && res.get("isWinner").getAsBoolean();
                System.out.println(winner ? " Hai VINTO la partita!" : " Hai PERSO la partita (4 errori).");
                System.out.println("Attendi la fine della partita globale per la prossima.");
            }
        } else {
            printError(res);
        }
    }

    private void doGameInfo(int gameId) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("operation", "requestGameInfo");
        req.addProperty("gameId",    gameId);

        JsonObject res = connection.sendAndReceive(req);
        if (!isOk(res)) { printError(res); return; }

        System.out.println("\n--- INFO PARTITA #" + getInt(res, "gameId", gameId) + " ---");
        boolean inProgress = res.has("inProgress") && res.get("inProgress").getAsBoolean();

        if (inProgress) {
            System.out.println("  Stato           : IN CORSO");
            System.out.println("  Tempo rimasto   : " + getLong(res, "timeLeft", 0) + "s");
            System.out.println("  Errori          : " + getInt(res, "mistakes", 0));
            System.out.println("  Punteggio       : " + getInt(res, "currentScore", 0));
            if (res.has("correctGroupsFound")) {
                System.out.print("  Gruppi trovati  : ");
                res.get("correctGroupsFound").getAsJsonArray().forEach(g -> System.out.print(g.getAsString() + " | "));
                System.out.println();
            }
            if (res.has("wordsRemaining")) {
                System.out.print("  Parole rimaste  : ");
                List<String> remaining = new ArrayList<>();
                res.get("wordsRemaining").getAsJsonArray().forEach(w -> remaining.add(w.getAsString()));
                currentWords = remaining;
                remaining.forEach(w -> System.out.print(w + " "));
                System.out.println();
            }
        } else {
            System.out.println("  Stato           : CONCLUSA");
            System.out.println("  Hai vinto       : " + (res.has("isWinner") && res.get("isWinner").getAsBoolean() ? "Sì" : "No"));
            System.out.println("  Errori          : " + getInt(res, "mistakes", 0));
            System.out.println("  Punteggio finale: " + getInt(res, "finalScore", 0));
            System.out.println("  Gruppi trovati  : " + getInt(res, "correctGroupsCount", 0));
            if (res.has("solution")) {
                System.out.println("  Soluzione:");
                res.get("solution").getAsJsonObject().entrySet().forEach(e -> {
                    System.out.print("    [" + e.getKey() + "] → ");
                    e.getValue().getAsJsonArray().forEach(w -> System.out.print(w.getAsString() + " "));
                    System.out.println();
                });
            }
        }
    }

    private void doGameInfoById() throws IOException {
        System.out.print("ID partita (-1 per corrente): ");
        int id = readInt(-1);
        doGameInfo(id);
    }

    private void doGameStatsById() throws IOException {
        System.out.print("ID partita (-1 per corrente): ");
        int id = readInt(-1);
        doGameStats(id);
    }

    private void doGameStats(int gameId) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("operation", "requestGameStats");
        req.addProperty("gameId",    gameId);

        JsonObject res = connection.sendAndReceive(req);
        if (!isOk(res)) { printError(res); return; }

        System.out.println("\n--- STATISTICHE PARTITA #" + getInt(res, "gameId", gameId) + " ---");
        boolean inProgress = res.has("inProgress") && res.get("inProgress").getAsBoolean();

        if (inProgress) {
            System.out.println("  Stato                  : IN CORSO");
            System.out.println("  Tempo rimasto          : " + getLong(res, "timeLeft", 0) + "s");
            System.out.println("  Giocatori totali       : " + getInt(res, "totalPlayers", 0));
            System.out.println("  Ancora in gioco        : " + getInt(res, "playersStillPlaying", 0));
            System.out.println("  Hanno finito           : " + getInt(res, "playersFinished", 0));
            System.out.println("  Vincitori              : " + getInt(res, "playersWon", 0));
        } else {
            System.out.println("  Stato                  : CONCLUSA");
            System.out.println("  Giocatori totali       : " + getInt(res, "totalPlayers", 0));
            System.out.println("  Hanno finito           : " + getInt(res, "playersFinished", 0));
            System.out.println("  Vincitori              : " + getInt(res, "playersWon", 0));
            System.out.printf( "  Punteggio medio        : %.1f%n", getFloat(res, "averageScore", 0f));
        }
    }

    // HANDLER — classifica e statistiche

    private void doLeaderboard(String playerName, int topK) throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("operation", "requestLeaderboard");
        if (playerName != null && !playerName.isBlank()) {
            req.addProperty("playerName", playerName);
        } else if (topK > 0) {
            req.addProperty("topPlayers", topK);
        }

        JsonObject res = connection.sendAndReceive(req);
        if (!isOk(res)) { printError(res); return; }

        if (res.has("rank")) {
            System.out.printf("  %s → posizione #%d | Punteggio: %d%n",
                    res.get("playerName").getAsString(),
                    res.get("rank").getAsInt(),
                    getInt(res, "totalScore", 0));
        } else if (res.has("leaderboard")) {
            System.out.println("\n--- CLASSIFICA ---");
            System.out.printf("  %-5s %-20s %-10s %-8s%n", "Rank", "Username", "Punteggio", "Win%");
            System.out.println("  " + "-".repeat(50));
            res.get("leaderboard").getAsJsonArray().forEach(entry -> {
                JsonObject e = entry.getAsJsonObject();
                System.out.printf("  %-5d %-20s %-10d %.1f%%%n",
                        e.get("rank").getAsInt(),
                        e.get("username").getAsString(),
                        e.get("totalScore").getAsInt(),
                        e.get("winRate").getAsFloat());
            });
        }
    }

    private void doTopK() throws IOException {
        System.out.print("Quanti giocatori vuoi vedere? ");
        int k = readInt(10);
        doLeaderboard(null, k);
    }

    private void doPlayerRank() throws IOException {
        System.out.print("Username giocatore: ");
        String name = scanner.nextLine().trim();
        doLeaderboard(name, 0);
    }

    private void doPlayerStats() throws IOException {
        JsonObject req = new JsonObject();
        req.addProperty("operation", "requestPlayerStats");

        JsonObject res = connection.sendAndReceive(req);
        if (!isOk(res)) { printError(res); return; }

        System.out.println("\n--- STATISTICHE DI " + res.get("username").getAsString() + " ---");
        System.out.println("  Puzzles Completed : " + getInt(res, "puzzlesCompleted", 0));
        System.out.printf( "  Win Rate          : %.1f%%%n", getFloat(res, "winRate",   0f));
        System.out.printf( "  Loss Rate         : %.1f%%%n", getFloat(res, "lossRate",  0f));
        System.out.println("  Current Streak    : " + getInt(res, "currentStreak", 0));
        System.out.println("  Max Streak        : " + getInt(res, "maxStreak",     0));
        System.out.println("  Perfect Puzzles   : " + getInt(res, "perfectPuzzles",0));
        System.out.println("  Total Score       : " + getInt(res, "totalScore",    0));

        if (res.has("mistakeHistogram")) {
            System.out.println("\n  Mistake Histogram:");
            JsonArray hist = res.get("mistakeHistogram").getAsJsonArray();
            String[] labels = {"0 err (perfect)", "1 errore", "2 errori", "3 errori",
                               "4 err (perso)", "Timeout"};
            for (int i = 0; i < hist.size() && i < labels.length; i++) {
                int count = hist.get(i).getAsInt();
                String bar = "█".repeat(Math.min(count, 30)) + (count > 30 ? "+" : "");
                System.out.printf("  %-18s | %s %d%n", labels[i], bar, count);
            }
        }
    }

    // STATO LOCALE — applicazione risposte server

    /* Applica lo stato della partita ricevuto dal server al login
    Coerente con handleLogin() in CommandHandler */

    private void applyLoginState(JsonObject res) {
        if (res.has("gameActive") && !res.get("gameActive").getAsBoolean()) {
            System.out.println("  Nessuna partita attiva al momento.");
            return;
        }
        if (!res.has("gameId")) return;

        currentGameId = res.get("gameId").getAsInt();
        System.out.println("  Partita in corso  : #" + currentGameId);
        System.out.println("  Tempo rimasto     : " + getLong(res, "timeLeft", 0) + "s");
        currentErrors = getInt(res, "mistakes",     0);
        currentScore  = getInt(res, "currentScore", 0);
        System.out.println("  Errori            : " + currentErrors);
        System.out.println("  Punteggio         : " + currentScore);

        currentWords = new ArrayList<>();
        if (res.has("words")) {
            res.get("words").getAsJsonArray().forEach(w -> currentWords.add(w.getAsString()));
            System.out.println("  Parole            : " + currentWords);
        }
        if (res.has("correctGroupsFound")) {
            System.out.print("  Gruppi già trovati: ");
            res.get("correctGroupsFound").getAsJsonArray().forEach(g -> System.out.print(g.getAsString() + " "));
            System.out.println();
        }
        if (res.has("hasFinished") && res.get("hasFinished").getAsBoolean()) {
            System.out.println("  Hai già terminato questa partita. Attendi la prossima.");
        }
    }

    /*    Callback invocato dal UdpListener quando arriva una notifica "newGame"
    Aggiorna lo stato locale per la nuova partita */

    private void applyNewGameState(JsonObject msg) {
        currentGameId = msg.has("gameId") ? msg.get("gameId").getAsInt() : -1;
        currentErrors = 0;
        currentScore  = 0;
        currentWords  = new ArrayList<>();
        if (msg.has("words")) {
            msg.get("words").getAsJsonArray().forEach(w -> currentWords.add(w.getAsString()));
        }
    }

    // UTILITY

    private boolean isOk(JsonObject res) {
        return res != null && res.has("status") && "ok".equals(res.get("status").getAsString());
    }

    private void printError(JsonObject res) {
        if (res == null) { System.out.println("Errore: nessuna risposta."); return; }
        String code = res.has("errorCode") ? res.get("errorCode").getAsString() : "?";
        String msg  = res.has("message")   ? res.get("message").getAsString()   : res.toString();
        System.out.println("Errore [" + code + "]: " + msg);
    }

    private int readInt(int defaultValue) {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int   getInt  (JsonObject o, String k, int   d) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt()   : d; }
    private long  getLong (JsonObject o, String k, long  d) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong()  : d; }
    private float getFloat(JsonObject o, String k, float d) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsFloat() : d; }

    private void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     CONNECTIONS - UniPi Edition      ║");
        System.out.println("╚══════════════════════════════════════╝");
    }
}
