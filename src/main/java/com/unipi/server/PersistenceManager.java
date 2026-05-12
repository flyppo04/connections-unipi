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
import java.io.FileReader;
import java.io.FileWriter;
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
//import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;

public class PersistenceManager {

    private final String usersFile;
    private final String gamesDir;
    private final UserManager userManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private volatile boolean usersDirty = false;
    private ScheduledExecutorService scheduler;

    public PersistenceManager(String usersFile, String gamesDir, UserManager userManager) {
        this.usersFile = usersFile;
        this.gamesDir = gamesDir;
        this.userManager = userManager;
        new File(gamesDir).mkdirs();
    }

    /* public void startPeriodicSave(int intervalMinutes) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "persistence-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            if (usersDirty) {
                saveUsers();
                usersDirty = false;
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

        System.out.println("[PERSIST] Salvataggio periodico utenti ogni " + intervalMinutes + " minuti avviato.");
    } */

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        saveUsers();
        System.out.println("[PERSIST] Salvataggio finale completato.");
    }

    public void markUsersDirty() {
        usersDirty = true;
    }

    public void saveUsers() {
        List<User> snapshot = new ArrayList<>(userManager.getAllUsers());
        writeJson(usersFile, snapshot);
        System.out.println("[PERSIST] Salvati " + snapshot.size() + " utenti.");
    }

    public List<User> loadUsers() {
        List<User> users = readJson(usersFile, new TypeToken<List<User>>() {}.getType());
        return users != null ? users : new ArrayList<>();
    }

    public synchronized void saveCompletedGame(Game game) {
        String file = getGamesFileForGameId(game.getGameId());
        List<Game> games = loadCompletedGames(file);

        games.removeIf(g -> g.getGameId() == game.getGameId());
        games.add(game);

        writeJson(file, games);

        System.out.println("[PERSIST] Salvata partita #" + game.getGameId() + " su " + file);
    }

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

    private List<Game> loadCompletedGames(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) return new ArrayList<>();

        try (Reader r = new FileReader(f)) {
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

    private String getStringOrDefault(JsonObject obj, String key, String defaultValue) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : defaultValue;
    }

    private int getIntOrDefault(JsonObject obj, String key, int defaultValue) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsInt() : defaultValue;
    }

    private boolean getBooleanOrDefault(JsonObject obj, String key, boolean defaultValue) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsBoolean() : defaultValue;
    }

    private String getGamesFileForGameId(int gameId) {
        int start = ((gameId - 1) / 100) * 100 + 1;
        int end = start + 99;
        return gamesDir + File.separator + "games_" + start + "_" + end + ".json";
    }

    private void writeJson(String filePath, Object data) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
            gson.toJson(data, w);
        } catch (IOException e) {
            System.err.println("[PERSIST] Errore scrittura " + filePath + ": " + e.getMessage());
        }
    }

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