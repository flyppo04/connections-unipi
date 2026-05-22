package com.unipi.server;

import com.google.gson.*;
import com.unipi.game.GameManager;
import com.unipi.game.UserManager;
import com.unipi.messages.request.*;
import com.unipi.models.Game;
import com.unipi.models.PlayerGameState;
import com.unipi.models.User;

import java.nio.channels.SocketChannel;
import java.util.*;

/*Gestisce il parsing e il dispatch di tutti i comandi JSON.
ServerMain si occupa di NIO e rete.
CommandHandler si occupa di protocollo e logica applicativa.
È stateless rispetto alle singole richieste (nessun campo
cambia durante process()), quindi è thread-safe e può essere
condiviso tra tutti i thread del worker pool*/

public class CommandHandler {

    private final UserManager userManager;
    private final GameManager gameManager;

    private final PersistenceManager persistence;
    private final Gson gson = new Gson();

    // Mappa canale → porta UDP: serve qui per registrarla al login
    // e rimuoverla al logout. Passata per riferimento da ServerMain.
    private final Map<SocketChannel, Integer> udpPorts;

    public CommandHandler(UserManager userManager, GameManager gameManager, 
                          Map<SocketChannel, Integer> udpPorts, PersistenceManager persistence) {
        this.userManager = userManager;
        this.gameManager = gameManager;
        this.udpPorts    = udpPorts;
        this.persistence = persistence;

    }

    // ENTRY POINT — chiamato dal worker pool per ogni messaggio ricevuto

    /*Deserializza il messaggio JSON, legge il campo "operation" e smista al metodo handler corrispondente.
    @param message  stringa JSON ricevuta dal client
    @param channel  canale TCP del client (serve per identificare la sessione)
    @return         stringa JSON di risposta da inviare al client*/

    public String process(String message, SocketChannel channel) {
        JsonObject peek;
        try {
            peek = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            return error("INVALID_JSON", "Il messaggio non è un JSON valido");
        }

        if (!peek.has("operation")) {
            return error("MISSING_OPERATION", "Campo 'operation' obbligatorio");
        }

        String op = peek.get("operation").getAsString();

        return switch (op) {
            case "register"           -> handleRegister(message);
            case "login"              -> handleLogin(message, channel);
            case "logout"             -> handleLogout(channel);
            case "updateCredentials"  -> handleUpdate(message, channel);
            case "submitProposal"     -> handlePropose(message, channel);
            case "requestGameInfo"    -> handleGameInfo(message, channel);
            case "requestGameStats"   -> handleGameStats(message, channel);
            case "requestLeaderboard" -> handleLeaderboard(message, channel);
            case "requestPlayerStats" -> handlePlayerStats(channel);
            default -> error("UNKNOWN_OPERATION", "Operazione sconosciuta: " + op);
        };
    }

    // HELPER — costruttori di risposta JSON

    // {"status":"ok","operation":"nomeOp"} e il chiamante aggiunge i campi extra 
    private JsonObject ok(String operation) {
        JsonObject o = new JsonObject();
        o.addProperty("status",    "ok");
        o.addProperty("operation", operation);
        return o;
    }

    // {"status":"error","errorCode":"...","message":"..."} come stringa
    private String error(String code, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("status",    "error");
        o.addProperty("errorCode", code);
        o.addProperty("message",   msg);
        return o.toString();
    }

    // HANDLER — gestione utente

    // {"operation":"register","name":"mario","psw":"1234"}
    private String handleRegister(String message) {
        RegisterRequest req = gson.fromJson(message, RegisterRequest.class);

        if (req.getName() == null || req.getName().isBlank() || req.getPsw() == null || req.getPsw().isBlank()) {
            return error("MISSING_PARAMS", "Campi 'name' e 'psw' obbligatori");
        }

        return switch (userManager.register(req.getName(), req.getPsw())) {
            case SUCCESS                 -> {persistence.saveUsers(); yield ok("register").toString();}
            case USERNAME_ALREADY_EXISTS -> error("USERNAME_TAKEN",   "Username già registrato");
            case INVALID_INPUT           -> error("INVALID_INPUT",    "Parametri non validi");
        };
    }

    // {"operation":"login","username":"mario","psw":"1234"} + opzionale: "udpPort": 6000
    
    /* Il login include automaticamente lo stato della partita corrente
    Se il client passa udpPort, il server lo salva in udpPorts per le notifiche asincrone
    LoginRequest deve avere il campo: int udpPort (Gson lo lascia a 0 se assente)*/

    private String handleLogin(String message, SocketChannel channel) {
        LoginRequest req = gson.fromJson(message, LoginRequest.class);

        if (req.getUsername() == null || req.getPsw() == null) {
            return error("MISSING_PARAMS", "Campi 'username' e 'psw' obbligatori");
        }

        UserManager.LoginResult result = userManager.login(req.getUsername(), req.getPsw(), channel);

        if (result != UserManager.LoginResult.SUCCESS) {
            return switch (result) {
                case USER_NOT_FOUND    -> error("USER_NOT_FOUND",    "Utente non trovato");
                case WRONG_PASSWORD   -> error("WRONG_PASSWORD",    "Password errata");
                case ALREADY_LOGGED_IN -> error("ALREADY_LOGGED_IN", "Utente già loggato");
                default              -> error("LOGIN_FAILED",      "Login fallito");
            };
        }

        // Registra la porta UDP se presente
        if (req.getUdpPort() > 0) {
            udpPorts.put(channel, req.getUdpPort());
        }

        // Costruisce la risposta con lo stato completo della partita
        JsonObject res = ok("login");
        res.addProperty("udpRegistered", req.getUdpPort() > 0);

        if (gameManager.hasActiveGame()) {
            Game game = gameManager.getCurrentGame();
            // addPlayer usa putIfAbsent: idempotente, preserva stato al ri-login
            game.addPlayer(req.getUsername());

            res.addProperty("gameId",   game.getGameId());
            res.addProperty("timeLeft", game.getRemainingTimeInSeconds());

            JsonArray words = new JsonArray();
            game.getWords().forEach(words::add);
            res.add("words", words);

            PlayerGameState state = game.getPlayerState(req.getUsername());
            if (state != null) {
                res.addProperty("mistakes",     state.getMistakes());
                res.addProperty("currentScore", state.getScore());
                res.addProperty("hasFinished",  state.hasFinished());
                res.addProperty("isWinner",     state.isWinner());

                JsonArray found = new JsonArray();
                state.getCorrectGroupsFound().forEach(found::add);
                res.add("correctGroupsFound", found);
            }
        } else {
            res.addProperty("gameActive", false);
        }

        return res.toString();
    }

    // {"operation":"logout"}
    private String handleLogout(SocketChannel channel) {
        udpPorts.remove(channel); // rimuovi endpoint UDP
        return switch (userManager.logout(channel)) {
            case SUCCESS         -> ok("logout").toString();
            case USER_NOT_LOGGED_IN -> error("NOT_LOGGED_IN", "Nessun utente loggato su questa connessione");
        };
    }

    //{"operation":"updateCredentials","oldPsw":"...","newName":"...","newPsw":"..."}
    /* newName e newPsw sono opzionali (almeno uno deve essere valorizzato)
    UpdateCredentialsRequest: campi oldPsw (req), newName (opt), newPsw (opt)
    Il vecchio username viene preso dalla sessione TCP per sicurezza*/

    private String handleUpdate(String message, SocketChannel channel) {
        User user = userManager.getUserByChannel(channel);
        if (user == null) return error("NOT_LOGGED_IN", "Devi essere loggato");

        UpdateCredentialsRequest req = gson.fromJson(message, UpdateCredentialsRequest.class);
        if (req.getOldPsw() == null || req.getOldPsw().isBlank()) {
            return error("MISSING_PARAMS", "Campo 'oldPsw' obbligatorio");
        }

        String newName = blankToNull(req.getNewName());
        String newPsw  = blankToNull(req.getNewPsw());

        return switch (userManager.updateCredentials(user.getUsername(),
                req.getOldPsw(), newName, newPsw)) {
            case SUCCESS          -> {persistence.saveUsers(); yield ok("updateCredentials").toString();}
            case USER_NOT_FOUND     -> error("USER_NOT_FOUND",   "Utente non trovato");
            case WRONG_PASSWORD    -> error("WRONG_PASSWORD",   "Password corrente errata");
            case NEW_USERNAME_TAKEN -> error("USERNAME_TAKEN",   "Nuovo username già in uso");
            case INVALID_INPUT     -> error("INVALID_INPUT",    "Almeno un campo tra newName e newPsw deve essere valorizzato");
        };
    }

    // HANDLER — partita

    //{"operation":"submitProposal","words":["W1","W2","W3","W4"]}

    /* Proposta MALFORMATA notificata come errore ma NON cambia lo stato della partita 
    SubmitProposalRequest: campo List<String> words */

    private String handlePropose(String message, SocketChannel channel) {
        User user = userManager.getUserByChannel(channel);
        if (user == null) return error("NOT_LOGGED_IN", "Devi essere loggato");

        SubmitProposalRequest req = gson.fromJson(message, SubmitProposalRequest.class);
        if (req.getWords() == null || req.getWords().size() != 4) {
            return error("MISSING_PARAMS", "Il campo 'words' deve contenere esattamente 4 parole");
        }

        GameManager.ProposalResult result = gameManager.submitProposal(user.getUsername(), req.getWords());

        PlayerGameState state = currentState(user.getUsername());

        return switch (result) {
            case CORRECT -> {
                JsonObject r = ok("submitProposal");
                r.addProperty("correct",     true);
                r.addProperty("score",       state != null ? state.getScore() : 0);
                r.addProperty("hasFinished", state != null && state.hasFinished());
                r.addProperty("isWinner",    state != null && state.isWinner());
                yield r.toString();
            }
            case WRONG -> {
                JsonObject r = ok("submitProposal");
                r.addProperty("correct",     false);
                r.addProperty("mistakes",    state != null ? state.getMistakes() : 0);
                r.addProperty("score",       state != null ? state.getScore()    : 0);
                r.addProperty("hasFinished", state != null && state.hasFinished());
                yield r.toString();
            }
            case ALREADY_FOUND    -> error("ALREADY_FOUND",  "Gruppo già trovato");
            case MALFORMED       -> error("MALFORMED",       "Proposta malformata: parole non valide o già raggruppate");
            case GAME_ALREADY_OVER -> error("GAME_OVER",       "Hai già concluso questa partita");
            case NO_ACTIVE_GAME    -> error("NO_ACTIVE_GAME",  "Nessuna partita attiva");
        };
    }

    
    //{"operation":"requestGameInfo","gameId":-1}   → partita corrente
    //{"operation":"requestGameInfo","gameId":42}   → partita storica

    private String handleGameInfo(String message, SocketChannel channel) { 
        User user = userManager.getUserByChannel(channel);
        if (user == null) return error("NOT_LOGGED_IN", "Devi essere loggato");

        GameInfoRequest req = gson.fromJson(message, GameInfoRequest.class);
        Game game = resolveGame(req.getGameId());
        if (game == null) return error("GAME_NOT_FOUND", "Partita non trovata: " + req.getGameId());

        JsonObject res = ok("requestGameInfo");
        res.addProperty("gameId", game.getGameId());

        PlayerGameState state = game.getPlayerState(user.getUsername());

        if (!game.isExpired()) {
            // La partita è ancora in corso globalmente
            res.addProperty("inProgress", true);
            res.addProperty("timeLeft", game.getRemainingTimeInSeconds());
            res.addProperty("mistakes", state != null ? state.getMistakes() : 0);
            res.addProperty("currentScore", state != null ? state.getScore() : 0);

            JsonArray found = new JsonArray();
            if (state != null) state.getCorrectGroupsFound().forEach(found::add);
            res.add("correctGroupsFound", found);

            List<String> remaining = new ArrayList<>(game.getWords());
            if (state != null) remaining.removeAll(state.getWordsAlreadyFound());
            JsonArray remArr = new JsonArray();
            remaining.forEach(remArr::add);
            res.add("wordsRemaining", remArr);

        } else {
            // La partita è conclusa
            res.addProperty("inProgress", false);

            if (state != null) {
                // L'utente ha partecipato → mostra i suoi dati personali
                res.addProperty("isWinner", state.isWinner());
                res.addProperty("mistakes", state.getMistakes());
                res.addProperty("finalScore", state.getScore());
                res.addProperty("correctGroupsCount", state.getCorrectGroupsFound().size());
            } else {
                // L'utente non ha partecipato a questa partita
                res.addProperty("participated", false);
            }

            // La soluzione completa si mostra sempre (partita finita)
            JsonObject solution = new JsonObject();
            game.getConnections().forEach((theme, wordList) -> {
                JsonArray wa = new JsonArray();
                wordList.forEach(wa::add);
                solution.add(theme, wa);
            });
            res.add("solution", solution);
        }

        return res.toString();
    }

    // {"operation":"requestGameStats","gameId":-1}

    private String handleGameStats(String message, SocketChannel channel) {
        User user = userManager.getUserByChannel(channel);
        if (user == null) return error("NOT_LOGGED_IN", "Devi essere loggato");

        GameStatsRequest req = gson.fromJson(message, GameStatsRequest.class);
        Game game = resolveGame(req.getGameId());
        if (game == null) return error("GAME_NOT_FOUND", "Partita non trovata: " + req.getGameId());

        JsonObject res = ok("requestGameStats");
        res.addProperty("gameId", game.getGameId());

        if (!game.isExpired()) {
            res.addProperty("inProgress",         true);
            res.addProperty("timeLeft",            game.getRemainingTimeInSeconds());
            res.addProperty("totalPlayers",        game.getTotalPlayers());
            res.addProperty("playersStillPlaying", game.getTotalPlayers() - game.getFinishedPlayersCount());
            res.addProperty("playersFinished",     game.getFinishedPlayersCount());
            res.addProperty("playersWon",          game.getWinnerPlayersCount());
        } else {
            res.addProperty("inProgress",     false);
            res.addProperty("totalPlayers",   game.getTotalPlayers());
            res.addProperty("playersFinished", game.getFinishedPlayersCount());
            res.addProperty("playersWon",     game.getWinnerPlayersCount());
            res.addProperty("averageScore",   game.getAverageScore());
        }

        return res.toString();
    }

    // HANDLER — classifica e statistiche

    //{"operation":"requestLeaderboard"}                     → intera classifica
    //{"operation":"requestLeaderboard","topPlayers":10}     → top K
    //{"operation":"requestLeaderboard","playerName":"mario"} → rank di un giocatore

    //LeaderboardRequest: campi String playerName (opt), int topPlayers (opt, 0 se assente)

    private String handleLeaderboard(String message, SocketChannel channel) {
        if (userManager.getUserByChannel(channel) == null) {
            return error("NOT_LOGGED_IN", "Devi essere loggato");
        }

        LeaderboardRequest req = gson.fromJson(message, LeaderboardRequest.class);
        JsonObject res = ok("requestLeaderboard");

        if (req.getPlayerName() != null && !req.getPlayerName().isBlank()) {
            User target = userManager.getUserByUsername(req.getPlayerName());
            if (target == null) {
                return error("PLAYER_NOT_FOUND", "Giocatore non trovato: " + req.getPlayerName());
            }
            res.addProperty("playerName", target.getUsername());
            res.addProperty("rank",        userManager.getRank(target.getUsername()));
            res.addProperty("totalScore",  target.getTotalScore());
        } else {
            Integer topPlayers = req.getTopPlayers();

            List<User> list = (topPlayers != null && topPlayers > 0)
                    ? userManager.getTopK(topPlayers)
                    : userManager.getLeaderboard();

            JsonArray arr = new JsonArray();
            for (int i = 0; i < list.size(); i++) {
                User u = list.get(i);
                JsonObject entry = new JsonObject();
                entry.addProperty("rank",       i + 1);
                entry.addProperty("username",   u.getUsername());
                entry.addProperty("totalScore", u.getTotalScore());
                entry.addProperty("winRate",    u.getWinRate());
                arr.add(entry);
            }
            res.add("leaderboard", arr);
        }

        return res.toString();
    }

    //{"operation":"requestPlayerStats"}
    //mistakeHistogram[i] = numero di partite concluse con i errori
    //0 = perfect, 1-3 = vinte con errori, 4 = perse per errori, 5 = perse per timeout

    private String handlePlayerStats(SocketChannel channel) {
        User user = userManager.getUserByChannel(channel);
        if (user == null) return error("NOT_LOGGED_IN", "Devi essere loggato");

        JsonObject res = ok("requestPlayerStats");
        res.addProperty("username",         user.getUsername());
        res.addProperty("puzzlesCompleted", user.getPuzzlesCompleted());
        res.addProperty("winRate",          user.getWinRate());
        res.addProperty("lossRate",         user.getLossRate());
        res.addProperty("currentStreak",    user.getCurrentStreak());
        res.addProperty("maxStreak",        user.getMaxStreak());
        res.addProperty("perfectPuzzles",   user.getPerfectWins());
        res.addProperty("totalScore",       user.getTotalScore());

        JsonArray histogram = new JsonArray();
        for (int count : user.getPuzzlesTracker()) histogram.add(count);
        res.add("mistakeHistogram", histogram);

        return res.toString();
    }

    // UTILITY

    // Risolve gameId: -1 = partita corrente, altro = storico
    private Game resolveGame(int gameId) {
        return (gameId == -1)
                ? gameManager.getCurrentGame()
                : gameManager.getGame(gameId);
    }

    // Restituisce lo stato del giocatore nella partita corrente e dovrebbe essere Null-safe
    private PlayerGameState currentState(String username) {
        Game game = gameManager.getCurrentGame();
        return (game != null) ? game.getPlayerState(username) : null;
    }

    //Converte stringa blank a null (usato per i campi opzionali)
    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}