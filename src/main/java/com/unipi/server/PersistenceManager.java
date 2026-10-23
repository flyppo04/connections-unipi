package com.unipi.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.unipi.game.UserManager;
import com.unipi.models.Game;
import com.unipi.models.PlayerGameState;
import com.unipi.models.User;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;


/* Gestisce la persistenza su disco: utenti, partite completate e gameId
Le partite vengono distribuite in file da 100 elementi ciascuno (es. games_1_100.json)
La deserializzazione è manuale per gestire correttamente i tipi generici di Gson */

public class PersistenceManager {

    private final String usersFile;
    private final String gamesDir;
    private final UserManager userManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String GAME_ID_FILE = "gameId.json"; // file con l'ultimo gameId usato
    private ScheduledExecutorService scheduler; // usato da chi estende questa classe per salvataggi periodici

    public PersistenceManager(String usersFile, String gamesDir, UserManager userManager) {
        this.usersFile = usersFile;
        this.gamesDir = gamesDir;
        this.userManager = userManager;
        new File(gamesDir).mkdirs();
    }

    // Arresta lo scheduler (se attivo) e forza un salvataggio finale degli utenti
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        saveUsers();
        System.out.println("[PERSIST] Salvataggio finale completato.");
    }

    // Serializza tutti gli utenti e li scrive su disco
    public synchronized void saveUsers() {
        List<User> snapshot = new ArrayList<>(userManager.getAllUsers());
        writeJson(usersFile, snapshot);
        System.out.println("[PERSIST] Salvati " + snapshot.size() + " utenti.");
    }

    // Carica la lista degli utenti dal file; restituisce lista vuota se il file non esiste
    public List<User> loadUsers() {
        List<User> users = readJson(usersFile, new TypeToken<List<User>>() {}.getType());
        return users != null ? users : new ArrayList<>();
    }

    // Aggiunge o aggiorna la partita nell'apposito file di bucket (sincronizzato)
    public synchronized void saveCompletedGame(Game game) {
        String file = getGamesFileForGameId(game.getGameId());
        List<Game> games = loadCompletedGames(file);

        games.removeIf(g -> g.getGameId() == game.getGameId());
        games.add(game);

        writeJson(file, games);

        System.out.println("[PERSIST] Salvata partita #" + game.getGameId() + " su " + file);
    }

    // Cerca una partita completata per id nel file di bucket corrispondente (sincronizzato)
    public synchronized Game findCompletedGameById(int gameId) {
        String file = getGamesFileForGameId(gameId);
        List<Game> games = loadCompletedGames(file);

        for (Game g : games) {
            if (g.getGameId() == gameId) {
                return g;
            }
        }
        return null;
    }

    // Scrive il gameId corrente su file, così il server può riprenderlo al riavvio
    public void saveGameId(int gameId) {
        JsonObject obj = new JsonObject();
        obj.addProperty("GameId", gameId);
        writeJson(GAME_ID_FILE, obj);
    }

    // Legge il gameId salvato; restituisce 1 al primo avvio o in caso di errore
    public int loadGameId() {
        File f = new File(GAME_ID_FILE);
        if (!f.exists()) return 1; // primo avvio
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonObject obj = gson.fromJson(r, JsonObject.class);
            return obj != null && obj.has("GameId") ? obj.get("GameId").getAsInt() : 1;
        } catch (IOException e) {
            System.err.println("[PERSIST] Errore lettura gameId: " + e.getMessage());
            return 1;
        }
    }

    // Carica le partite dal file indicato; deserializza manualmente per via dei tipi generici
    private List<Game> loadCompletedGames(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) return new ArrayList<>();

        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonArray array = gson.fromJson(r, JsonArray.class);
            if (array == null) return new ArrayList<>();

            List<Game> games = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                games.add(deserializeHistoricalGame(obj));
            }
            return games;

        } catch (IOException e) {
            System.err.println("[PERSIST] Errore lettura " + filePath + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // Ricostruisce un oggetto Game da un JsonObject storico
    private Game deserializeHistoricalGame(JsonObject obj) {
        int gameId = obj.get("gameId").getAsInt();

        Type wordsType = new TypeToken<List<String>>() {}.getType();
        List<String> words = gson.fromJson(obj.get("words"), wordsType);
        if (words == null) words = new ArrayList<>();

        Type connectionsType = new TypeToken<Map<String, List<String>>>() {}.getType();
        Map<String, List<String>> connections = gson.fromJson(obj.get("connections"), connectionsType);
        if (connections == null) connections = new HashMap<>();

        Map<String, PlayerGameState> playerStates = deserializePlayerStates(obj.getAsJsonObject("playerStates"));

        return new Game(gameId, words, connections, playerStates);
    }

    // Ricostruisce la mappa username → PlayerGameState da un JsonObject
    // Usa getStringOrDefault/getIntOrDefault per tollerare campi mancanti
    private Map<String, PlayerGameState> deserializePlayerStates(JsonObject playerStatesObj) {
        Map<String, PlayerGameState> playerStates = new HashMap<>();
        if (playerStatesObj == null) return playerStates;

        for (Map.Entry<String, JsonElement> entry : playerStatesObj.entrySet()) {
            String usernameKey = entry.getKey();
            JsonObject stateObj = entry.getValue().getAsJsonObject();

            String username = getStringOrDefault(stateObj, "username", usernameKey);
            int mistakes = getIntOrDefault(stateObj, "mistakes", 0);
            int score = getIntOrDefault(stateObj, "score", 0);
            boolean hasFinished = getBooleanOrDefault(stateObj, "hasFinished", false);
            boolean isWinner = getBooleanOrDefault(stateObj, "isWinner", false);

            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> correctGroupsFound = gson.fromJson(stateObj.get("correctGroupsFound"), listType);
            List<String> wordsAlreadyFound = gson.fromJson(stateObj.get("wordsAlreadyFound"), listType);

            if (correctGroupsFound == null) correctGroupsFound = new ArrayList<>();
            if (wordsAlreadyFound == null) wordsAlreadyFound = new ArrayList<>();

            PlayerGameState state = new PlayerGameState(
                    username,
                    mistakes,
                    score,
                    correctGroupsFound,
                    wordsAlreadyFound,
                    hasFinished,
                    isWinner
            );

            playerStates.put(usernameKey, state);
        }

        return playerStates;
    }

    // Calcola il file di bucket per un dato gameId (gruppi da 100: 1-100, 101-200, ...)
    private String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : defaultValue;
    }

    // Scrive l'oggetto serializzato nel file indicato (UTF-8, pretty print)
    private int getIntOrDefault(JsonObject obj, String key, int defaultValue) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsInt() : defaultValue;
    }

    // Legge e deserializza un oggetto dal file indicato; null se il file non esiste
    private boolean getBooleanOrDefault(JsonObject obj, String key, boolean defaultValue) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsBoolean() : defaultValue;
    }

    // Calcola il file di bucket per un dato gameId (gruppi da 100: 1-100, 101-200, ...)
    private String getGamesFileForGameId(int gameId) {
        int start = ((gameId - 1) / 100) * 100 + 1;
        int end = start + 99;
        return gamesDir + File.separator + "games_" + start + "_" + end + ".json";
    }

    // Scrive l'oggetto serializzato nel file indicato (UTF-8, pretty print)
    private void writeJson(String filePath, Object data) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
            gson.toJson(data, w);
        } catch (IOException e) {
            System.err.println("[PERSIST] Errore scrittura " + filePath + ": " + e.getMessage());
        }
    }

    // Legge e deserializza un oggetto dal file indicato; null se il file non esiste
    private <T> T readJson(String filePath, Type type) {
        File f = new File(filePath);
        if (!f.exists()) return null;

        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            return gson.fromJson(r, type);
        } catch (IOException e) {
            System.err.println("[PERSIST] Errore lettura " + filePath + ": " + e.getMessage());
            return null;
        }
    }
}