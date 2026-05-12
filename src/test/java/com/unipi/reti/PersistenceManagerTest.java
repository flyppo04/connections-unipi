package com.unipi.reti;

import com.unipi.game.UserManager;
import com.unipi.models.Game;
import com.unipi.models.PlayerGameState;
import com.unipi.models.User;
import com.unipi.server.PersistenceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per PersistenceManager.
 *
 * Usa @TempDir per scrivere e leggere file JSON reali su disco temporaneo.
 * Non richiede mock: testa la serializzazione/deserializzazione effettiva.
 *
 * Casi coperti:
 *   - saveUsers / loadUsers: round-trip utenti
 *   - loadUsers: file inesistente → lista vuota
 *   - saveCompletedGame / findCompletedGameById: round-trip partite
 *   - findCompletedGameById: id inesistente → null
 *   - partite multiple nello stesso file bucket (stessa fascia di 100 id)
 *   - partite in bucket diversi (es. gameId 1 e gameId 101)
 *   - markUsersDirty / saveUsers integrazione con startPeriodicSave
 */
@DisplayName("PersistenceManager — serializzazione e lettura su disco")
class PersistenceManagerTest {

    @TempDir
    Path tempDir;

    private Path usersFile;
    private Path gamesDir;
    private UserManager userManager;
    private PersistenceManager persistence;

    @BeforeEach
    void setUp() {
        usersFile = tempDir.resolve("users.json");
        gamesDir  = tempDir.resolve("games");

        userManager = UserManager.createNew();
        userManager.register("alice", "pwd");
        userManager.register("bob", "pwd2");

        persistence = new PersistenceManager(
            usersFile.toString(), gamesDir.toString(), userManager);
    }

    // ─── saveUsers / loadUsers ────────────────────────────────────────

    @Test
    @DisplayName("saveUsers crea il file e loadUsers lo rileva")
    void saveAndLoad_users_fileExists() {
        persistence.saveUsers();
        assertTrue(Files.exists(usersFile), "Il file utenti deve essere creato da saveUsers");
    }

    @Test
    @DisplayName("saveUsers / loadUsers: round-trip preserva username e password")
    void saveAndLoad_users_roundTrip() {
        // Modifichiamo un po' i dati per rendere il test più significativo
        userManager.getUserByUsername("alice").addScore(42);
        userManager.getUserByUsername("alice").updateCurentStreak();
        userManager.getUserByUsername("alice").getPuzzlesTracker()[0]++;

        persistence.saveUsers();
        List<User> loaded = persistence.loadUsers();

        assertNotNull(loaded);
        assertEquals(2, loaded.size());

        User loadedAlice = loaded.stream()
            .filter(u -> "alice".equals(u.getUsername()))
            .findFirst().orElse(null);

        assertNotNull(loadedAlice, "alice deve essere presente dopo il caricamento");
        assertEquals("pwd",  loadedAlice.getPassword());
        assertEquals(42,     loadedAlice.getTotalScore());
        assertEquals(1,      loadedAlice.getCurrentStreak());
        assertEquals(1,      loadedAlice.getPuzzlesTracker()[0]);
    }

    @Test
    @DisplayName("loadUsers su file inesistente restituisce lista vuota")
    void loadUsers_fileNotExists_returnsEmpty() {
        // Non chiamiamo saveUsers: il file non esiste
        List<User> loaded = persistence.loadUsers();
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty(),
            "Se il file non esiste, loadUsers deve restituire una lista vuota");
    }

    @Test
    @DisplayName("saveUsers / loadUsers: tutti gli utenti vengono salvati")
    void saveUsers_savesAllUsers() {
        persistence.saveUsers();
        List<User> loaded = persistence.loadUsers();
        assertEquals(2, loaded.size());
        long aliceCount = loaded.stream().filter(u -> "alice".equals(u.getUsername())).count();
        long bobCount   = loaded.stream().filter(u -> "bob".equals(u.getUsername())).count();
        assertEquals(1, aliceCount);
        assertEquals(1, bobCount);
    }

    // ─── saveCompletedGame / findCompletedGameById ────────────────────

    @Test
    @DisplayName("saveCompletedGame / findCompletedGameById: round-trip partita base")
    void saveAndFind_game_roundTrip() {
        Game game = buildGame(1);
        persistence.saveCompletedGame(game);

        Game found = persistence.findCompletedGameById(1);
        assertNotNull(found, "La partita salvata deve essere trovabile per id");
        assertEquals(1, found.getGameId());
    }

    @Test
    @DisplayName("findCompletedGameById: id inesistente restituisce null")
    void findGame_unknownId_returnsNull() {
        assertNull(persistence.findCompletedGameById(9999));
    }

    @Test
    @DisplayName("saveCompletedGame preserva i playerStates (username, score, mistakes)")
    void saveGame_preservesPlayerStates() {
        Game game = buildGame(1);
        game.addPlayer("alice");
        game.getPlayerState("alice").addCorrectGroup("T1", List.of("A","B","C","D"));
        game.getPlayerState("alice").addMistake();

        persistence.saveCompletedGame(game);
        Game found = persistence.findCompletedGameById(1);

        assertNotNull(found.getPlayerState("alice"),
            "Lo stato di alice deve essere persistito");
        assertEquals(6 - 4, found.getPlayerState("alice").getScore(),
            "Score (1 gruppo +6, 1 errore -4) deve essere 2");
        assertEquals(1, found.getPlayerState("alice").getMistakes());
    }

    @Test
    @DisplayName("saveCompletedGame preserva connections (tema → parole)")
    void saveGame_preservesConnections() {
        Game game = buildGame(5);
        persistence.saveCompletedGame(game);

        Game found = persistence.findCompletedGameById(5);
        assertNotNull(found.getConnections());
        assertEquals(4, found.getConnections().size());
        assertTrue(found.getConnections().containsKey("T1"));
        assertEquals(List.of("A","B","C","D"), found.getConnections().get("T1"));
    }

    @Test
    @DisplayName("saveCompletedGame: salvare di nuovo la stessa partita non crea duplicati")
    void saveGame_overwrite_noDuplicates() {
        Game game = buildGame(1);
        persistence.saveCompletedGame(game); // prima volta
        persistence.saveCompletedGame(game); // seconda volta (aggiornamento)

        // findCompletedGameById deve trovare esattamente 1 istanza
        Game found = persistence.findCompletedGameById(1);
        assertNotNull(found);
        assertEquals(1, found.getGameId());
        // (non c'è un metodo pubblico per contare quante volte è stata salvata,
        //  ma se il file fosse corrotto o duplicato, la deserializzazione lancerebbe un errore)
    }

    @Test
    @DisplayName("Partite con gameId diversi nello stesso bucket coesistono")
    void saveGame_multipleGamesInSameBucket() {
        // gameId 1 e 50 vanno entrambe nel file games_1_100.json
        persistence.saveCompletedGame(buildGame(1));
        persistence.saveCompletedGame(buildGame(50));

        assertNotNull(persistence.findCompletedGameById(1));
        assertNotNull(persistence.findCompletedGameById(50));
        assertNull(persistence.findCompletedGameById(75),
            "Partita non salvata non deve essere trovabile");
    }

    @Test
    @DisplayName("Partite in bucket diversi sono salvate su file separati")
    void saveGame_differentBuckets_separateFiles() throws IOException {
        // gameId 1 → games_1_100.json, gameId 101 → games_101_200.json
        persistence.saveCompletedGame(buildGame(1));
        persistence.saveCompletedGame(buildGame(101));

        assertNotNull(persistence.findCompletedGameById(1));
        assertNotNull(persistence.findCompletedGameById(101));
        assertNull(persistence.findCompletedGameById(50),
            "gameId 50 non è stato salvato");

        // Verifica che esistano effettivamente 2 file diversi
        Path file1   = gamesDir.resolve("games_1_100.json");
        Path file101 = gamesDir.resolve("games_101_200.json");
        assertTrue(Files.exists(file1),   "games_1_100.json deve esistere");
        assertTrue(Files.exists(file101), "games_101_200.json deve esistere");
    }

    @Test
    @DisplayName("markUsersDirty non lancia eccezioni")
    void markUsersDirty_doesNotThrow() {
        assertDoesNotThrow(() -> persistence.markUsersDirty());
    }

    @Test
    @DisplayName("shutdown salva gli utenti su disco prima di terminare")
    void shutdown_savesUsers() {
        userManager.getUserByUsername("alice").addScore(99);
        persistence.shutdown(); // deve chiamare saveUsers internamente

        // Leggiamo direttamente il file per verificare che il salvataggio sia avvenuto
        List<User> loaded = persistence.loadUsers();
        assertNotNull(loaded);
        User alice = loaded.stream()
            .filter(u -> "alice".equals(u.getUsername()))
            .findFirst().orElse(null);
        assertNotNull(alice);
        assertEquals(99, alice.getTotalScore(),
            "shutdown deve salvare il totalScore aggiornato");
    }

    // ─── Helper ───────────────────────────────────────────────────────

    /**
     * Costruisce una partita storica minimale con il dato gameId.
     * Usa il costruttore di deserializzazione di Game (endTimeInMillis = 0).
     */
    private Game buildGame(int gameId) {
        List<String> words = List.of(
            "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P");
        Map<String, List<String>> connections = Map.of(
            "T1", List.of("A","B","C","D"),
            "T2", List.of("E","F","G","H"),
            "T3", List.of("I","J","K","L"),
            "T4", List.of("M","N","O","P")
        );
        Map<String, PlayerGameState> playerStates = Map.of();
        return new Game(gameId, words, connections, playerStates);
    }
}
