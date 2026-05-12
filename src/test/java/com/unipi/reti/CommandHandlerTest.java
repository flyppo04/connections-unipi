package com.unipi.reti;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unipi.data.GroupData;
import com.unipi.data.PuzzleData;
import com.unipi.data.PuzzleReader;
import com.unipi.game.GameManager;
import com.unipi.game.UserManager;
import com.unipi.models.Game;
import com.unipi.server.CommandHandler;
import com.unipi.server.PersistenceManager;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di integrazione per CommandHandler.
 *
 * CommandHandler è stateless: tutti i test usano lo stesso setup.
 * Ogni test apre/chiude il proprio SocketChannel dove necessario.
 *
 * Strategia di verifica: parsiamo la risposta JSON e controlliamo
 *   "status": "ok" | "error"
 *   e i campi specifici dell'operazione.
 *
 * Abbreviazione usata nei test:
 *   ch  = SocketChannel del client
 *   res = JSON di risposta parsato
 */
@DisplayName("CommandHandler — protocollo JSON end-to-end")
class CommandHandlerTest {

    // ─── Stub PuzzleReader ────────────────────────────────────────────
    static class FakePuzzleReader extends PuzzleReader {
        FakePuzzleReader() { super(null); }

        @Override
        public PuzzleData getNextPuzzle() {
            return new PuzzleData(1, List.of(
                new GroupData("T1", List.of("A","B","C","D")),
                new GroupData("T2", List.of("E","F","G","H")),
                new GroupData("T3", List.of("I","J","K","L")),
                new GroupData("T4", List.of("M","N","O","P"))
            ));
        }
    }

    // ─── Stub PersistenceManager ──────────────────────────────────────
    static class NoPersistenceManager extends PersistenceManager {
        NoPersistenceManager(UserManager um) { super("/dev/null", "/dev/null", um); }
        @Override public void saveUsers() {}
        @Override public void markUsersDirty() {}
        @Override public synchronized void saveCompletedGame(Game game) {}
        @Override public synchronized Game findCompletedGameById(int gameId) { return null; }
    }

    private UserManager userManager;
    private GameManager gameManager;
    private CommandHandler handler;
    private Map<SocketChannel, Integer> udpPorts;

    @BeforeEach
    void setUp() {
        userManager = UserManager.createNew();
        udpPorts = new ConcurrentHashMap<>();

        NoPersistenceManager pm = new NoPersistenceManager(userManager);
        gameManager = new GameManager(new FakePuzzleReader(), userManager, 60, pm);
        gameManager.startNewGame();

        handler = new CommandHandler(userManager, gameManager, udpPorts, pm);
    }

    @AfterEach
    void tearDown() {
        gameManager.shutdown();
    }

    // ─── Utility ──────────────────────────────────────────────────────

    /** Parsa la risposta JSON restituita da CommandHandler. */
    private JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** Asserisce che la risposta abbia status "ok". */
    private void assertOk(JsonObject res) {
        assertEquals("ok", res.get("status").getAsString(),
            "Risposta attesa OK ma è: " + res);
    }

    /** Asserisce che la risposta abbia status "error" con il codice dato. */
    private void assertError(JsonObject res, String expectedCode) {
        assertEquals("error", res.get("status").getAsString(),
            "Risposta attesa ERROR ma è: " + res);
        assertEquals(expectedCode, res.get("errorCode").getAsString(),
            "Codice errore atteso: " + expectedCode + ", ottenuto: " + res);
    }

    /**
     * Apre un canale, esegue il login di un utente e restituisce il canale.
     * Il chiamante è responsabile di chiuderlo nel finally.
     */
    private SocketChannel loginAs(String username, String password) throws IOException {
        userManager.register(username, password);
        SocketChannel ch = SocketChannel.open();
        handler.process(
            "{\"operation\":\"login\",\"username\":\"" + username + "\",\"psw\":\"" + password + "\"}",
            ch);
        return ch;
    }

    // =========================================================================
    // OPERAZIONI BASE — JSON invalido / operazione sconosciuta
    // =========================================================================

    @Test
    @DisplayName("JSON non valido → INVALID_JSON")
    void process_invalidJson() {
        SocketChannel ch;
        try { ch = SocketChannel.open(); } catch (IOException e) { fail(); return; }
        try {
            JsonObject res = parse(handler.process("non-json-string", ch));
            assertError(res, "INVALID_JSON");
        } finally { try { ch.close(); } catch (IOException ignored) {} }
    }

    @Test
    @DisplayName("Campo 'operation' mancante → MISSING_OPERATION")
    void process_missingOperation() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process("{\"field\":\"value\"}", ch));
            assertError(res, "MISSING_OPERATION");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("Operazione sconosciuta → UNKNOWN_OPERATION")
    void process_unknownOperation() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process("{\"operation\":\"fooBar\"}", ch));
            assertError(res, "UNKNOWN_OPERATION");
        } finally { ch.close(); }
    }

    // =========================================================================
    // REGISTER
    // =========================================================================

    @Test
    @DisplayName("register: successo con credenziali valide")
    void register_success() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"register\",\"name\":\"mario\",\"psw\":\"1234\"}", ch));
            assertOk(res);
            assertEquals("register", res.get("operation").getAsString());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("register: username già registrato → USERNAME_TAKEN")
    void register_duplicateUsername() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            handler.process("{\"operation\":\"register\",\"name\":\"mario\",\"psw\":\"1234\"}", ch);
            JsonObject res = parse(handler.process(
                "{\"operation\":\"register\",\"name\":\"mario\",\"psw\":\"altra\"}", ch));
            assertError(res, "USERNAME_TAKEN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("register: campo 'name' mancante → MISSING_PARAMS")
    void register_missingName() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"register\",\"psw\":\"1234\"}", ch));
            assertError(res, "MISSING_PARAMS");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("register: campo 'psw' mancante → MISSING_PARAMS")
    void register_missingPsw() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"register\",\"name\":\"mario\"}", ch));
            assertError(res, "MISSING_PARAMS");
        } finally { ch.close(); }
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    @Test
    @DisplayName("login: successo — risposta contiene parole e gameId")
    void login_success_withGameInfo() throws IOException {
        userManager.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"login\",\"username\":\"alice\",\"psw\":\"pwd\"}", ch));
            assertOk(res);
            // Con partita attiva deve includere le parole e il gameId
            assertTrue(res.has("gameId"), "Risposta login deve contenere gameId");
            assertTrue(res.has("words"),  "Risposta login deve contenere words");
            assertEquals(16, res.getAsJsonArray("words").size());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("login: utente non trovato → USER_NOT_FOUND")
    void login_userNotFound() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"login\",\"username\":\"ghost\",\"psw\":\"pwd\"}", ch));
            assertError(res, "USER_NOT_FOUND");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("login: password errata → WRONG_PASSWORD")
    void login_wrongPassword() throws IOException {
        userManager.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"login\",\"username\":\"alice\",\"psw\":\"sbagliata\"}", ch));
            assertError(res, "WRONG_PASSWORD");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("login: già loggato → ALREADY_LOGGED_IN")
    void login_alreadyLoggedIn() throws IOException {
        userManager.register("alice", "pwd");
        SocketChannel ch1 = SocketChannel.open();
        SocketChannel ch2 = SocketChannel.open();
        try {
            handler.process("{\"operation\":\"login\",\"username\":\"alice\",\"psw\":\"pwd\"}", ch1);
            JsonObject res = parse(handler.process(
                "{\"operation\":\"login\",\"username\":\"alice\",\"psw\":\"pwd\"}", ch2));
            assertError(res, "ALREADY_LOGGED_IN");
        } finally { ch1.close(); ch2.close(); }
    }

    @Test
    @DisplayName("login: registra porta UDP se udpPort > 0")
    void login_registersUdpPort() throws IOException {
        userManager.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            handler.process(
                "{\"operation\":\"login\",\"username\":\"alice\",\"psw\":\"pwd\",\"udpPort\":6000}", ch);
            assertTrue(udpPorts.containsKey(ch), "La porta UDP deve essere registrata");
            assertEquals(6000, udpPorts.get(ch));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("login: udpPort assente → non registra porta UDP")
    void login_noUdpPort_notRegistered() throws IOException {
        userManager.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            handler.process(
                "{\"operation\":\"login\",\"username\":\"alice\",\"psw\":\"pwd\"}", ch);
            assertFalse(udpPorts.containsKey(ch), "Senza udpPort non deve esserci una voce in udpPorts");
        } finally { ch.close(); }
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    @Test
    @DisplayName("logout: successo dopo login")
    void logout_success() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process("{\"operation\":\"logout\"}", ch));
            assertOk(res);
            assertFalse(userManager.isLoggedIn("alice"));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("logout: senza login → NOT_LOGGED_IN")
    void logout_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process("{\"operation\":\"logout\"}", ch));
            assertError(res, "NOT_LOGGED_IN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("logout: rimuove la porta UDP registrata")
    void logout_removesUdpPort() throws IOException {
        userManager.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            handler.process(
                "{\"operation\":\"login\",\"username\":\"alice\",\"psw\":\"pwd\",\"udpPort\":6000}", ch);
            handler.process("{\"operation\":\"logout\"}", ch);
            assertFalse(udpPorts.containsKey(ch), "La porta UDP deve essere rimossa al logout");
        } finally { ch.close(); }
    }

    // =========================================================================
    // UPDATE CREDENTIALS
    // =========================================================================

    @Test
    @DisplayName("updateCredentials: senza login → NOT_LOGGED_IN")
    void updateCredentials_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"updateCredentials\",\"oldPsw\":\"pwd\",\"newPsw\":\"new\"}", ch));
            assertError(res, "NOT_LOGGED_IN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("updateCredentials: cambio password — successo")
    void updateCredentials_changePassword_success() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"updateCredentials\",\"oldPsw\":\"pwd\",\"newPsw\":\"newpwd\"}", ch));
            assertOk(res);
            assertEquals("newpwd", userManager.getUserByUsername("alice").getPassword());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("updateCredentials: cambio username — successo")
    void updateCredentials_changeUsername_success() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"updateCredentials\",\"oldPsw\":\"pwd\",\"newName\":\"alice2\"}", ch));
            assertOk(res);
            assertNull(userManager.getUserByUsername("alice"), "alice non deve più esistere");
            assertNotNull(userManager.getUserByUsername("alice2"), "alice2 deve esistere");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("updateCredentials: vecchia password errata → WRONG_PASSWORD")
    void updateCredentials_wrongPassword() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"updateCredentials\",\"oldPsw\":\"sbagliata\",\"newPsw\":\"new\"}", ch));
            assertError(res, "WRONG_PASSWORD");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("updateCredentials: nuovo username già occupato → USERNAME_TAKEN")
    void updateCredentials_newUsernameTaken() throws IOException {
        userManager.register("bob", "pwd");
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"updateCredentials\",\"oldPsw\":\"pwd\",\"newName\":\"bob\"}", ch));
            assertError(res, "USERNAME_TAKEN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("updateCredentials: campo 'oldPsw' mancante → MISSING_PARAMS")
    void updateCredentials_missingOldPsw() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"updateCredentials\",\"newPsw\":\"new\"}", ch));
            assertError(res, "MISSING_PARAMS");
        } finally { ch.close(); }
    }

    // =========================================================================
    // SUBMIT PROPOSAL
    // =========================================================================

    @Test
    @DisplayName("submitProposal: senza login → NOT_LOGGED_IN")
    void submitProposal_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\",\"D\"]}", ch));
            assertError(res, "NOT_LOGGED_IN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("submitProposal: proposta corretta → ok con correct=true")
    void submitProposal_correct() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\",\"D\"]}", ch));
            assertOk(res);
            assertTrue(res.get("correct").getAsBoolean());
            assertEquals(6, res.get("score").getAsInt());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("submitProposal: proposta sbagliata → ok con correct=false e mistakes incrementato")
    void submitProposal_wrong() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\",\"E\"]}", ch));
            assertOk(res);
            assertFalse(res.get("correct").getAsBoolean());
            assertEquals(1, res.get("mistakes").getAsInt());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("submitProposal: parole fuori board → MALFORMED")
    void submitProposal_malformed() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"submitProposal\",\"words\":[\"X\",\"Y\",\"Z\",\"W\"]}", ch));
            assertError(res, "MALFORMED");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("submitProposal: meno di 4 parole nel JSON → MISSING_PARAMS")
    void submitProposal_tooFewWords_missingParams() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\"]}", ch));
            assertError(res, "MISSING_PARAMS");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("submitProposal: giocatore che ha già vinto → GAME_OVER")
    void submitProposal_gameOver() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\",\"D\"]}", ch);
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"E\",\"F\",\"G\",\"H\"]}", ch);
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"I\",\"J\",\"K\",\"L\"]}", ch);

            JsonObject res = parse(handler.process(
                "{\"operation\":\"submitProposal\",\"words\":[\"M\",\"N\",\"O\",\"P\"]}", ch));
            assertError(res, "GAME_OVER");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("submitProposal: vincita — risposta contiene hasFinished=true e isWinner=true")
    void submitProposal_win_responseFields() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\",\"D\"]}", ch);
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"E\",\"F\",\"G\",\"H\"]}", ch);
            JsonObject res = parse(handler.process(
                "{\"operation\":\"submitProposal\",\"words\":[\"I\",\"J\",\"K\",\"L\"]}", ch));

            assertOk(res);
            assertTrue(res.get("hasFinished").getAsBoolean(), "hasFinished deve essere true");
            assertTrue(res.get("isWinner").getAsBoolean(),    "isWinner deve essere true");
            assertEquals(18, res.get("score").getAsInt());
        } finally { ch.close(); }
    }

    // =========================================================================
    // REQUEST GAME INFO
    // =========================================================================

    @Test
    @DisplayName("requestGameInfo: senza login → NOT_LOGGED_IN")
    void requestGameInfo_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestGameInfo\",\"gameId\":-1}", ch));
            assertError(res, "NOT_LOGGED_IN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestGameInfo: partita corrente (gameId=-1) — risposta con parole rimanenti")
    void requestGameInfo_currentGame() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestGameInfo\",\"gameId\":-1}", ch));
            assertOk(res);
            assertTrue(res.get("inProgress").getAsBoolean());
            assertTrue(res.has("timeLeft"));
            assertTrue(res.has("wordsRemaining"));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestGameInfo: dopo vittoria — risposta con solution completa")
    void requestGameInfo_afterWin_showsSolution() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\",\"D\"]}", ch);
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"E\",\"F\",\"G\",\"H\"]}", ch);
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"I\",\"J\",\"K\",\"L\"]}", ch);

            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestGameInfo\",\"gameId\":-1}", ch));
            assertOk(res);
            assertFalse(res.get("inProgress").getAsBoolean());
            assertTrue(res.get("isWinner").getAsBoolean());
            assertTrue(res.has("solution"), "La soluzione deve essere inclusa dopo la fine");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestGameInfo: gameId inesistente → GAME_NOT_FOUND")
    void requestGameInfo_unknownGame() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestGameInfo\",\"gameId\":9999}", ch));
            assertError(res, "GAME_NOT_FOUND");
        } finally { ch.close(); }
    }

    // =========================================================================
    // REQUEST GAME STATS
    // =========================================================================

    @Test
    @DisplayName("requestGameStats: partita corrente — risposta con totalPlayers e timeLeft")
    void requestGameStats_currentGame() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestGameStats\",\"gameId\":-1}", ch));
            assertOk(res);
            assertTrue(res.get("inProgress").getAsBoolean());
            assertTrue(res.has("totalPlayers"));
            assertTrue(res.has("timeLeft"));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestGameStats: senza login → NOT_LOGGED_IN")
    void requestGameStats_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestGameStats\",\"gameId\":-1}", ch));
            assertError(res, "NOT_LOGGED_IN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestGameStats: gameId inesistente → GAME_NOT_FOUND")
    void requestGameStats_unknownGame() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestGameStats\",\"gameId\":9999}", ch));
            assertError(res, "GAME_NOT_FOUND");
        } finally { ch.close(); }
    }

    // =========================================================================
    // REQUEST LEADERBOARD
    // =========================================================================

    @Test
    @DisplayName("requestLeaderboard: classifica completa")
    void requestLeaderboard_full() throws IOException {
        userManager.register("bob", "pwd");
        userManager.getUserByUsername("bob").addScore(10);
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestLeaderboard\"}", ch));
            assertOk(res);
            assertTrue(res.has("leaderboard"));
            assertTrue(res.getAsJsonArray("leaderboard").size() >= 2);
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestLeaderboard: top K")
    void requestLeaderboard_topK() throws IOException {
        userManager.register("bob", "pwd");
        userManager.register("carol", "pwd");
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestLeaderboard\",\"topPlayers\":2}", ch));
            assertOk(res);
            assertEquals(2, res.getAsJsonArray("leaderboard").size());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestLeaderboard: rank di un giocatore specifico")
    void requestLeaderboard_playerRank() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestLeaderboard\",\"playerName\":\"alice\"}", ch));
            assertOk(res);
            assertTrue(res.has("rank"));
            assertTrue(res.has("totalScore"));
            assertEquals("alice", res.get("playerName").getAsString());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestLeaderboard: giocatore inesistente → PLAYER_NOT_FOUND")
    void requestLeaderboard_playerNotFound() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestLeaderboard\",\"playerName\":\"ghost\"}", ch));
            assertError(res, "PLAYER_NOT_FOUND");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestLeaderboard: senza login → NOT_LOGGED_IN")
    void requestLeaderboard_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestLeaderboard\"}", ch));
            assertError(res, "NOT_LOGGED_IN");
        } finally { ch.close(); }
    }

    // =========================================================================
    // REQUEST PLAYER STATS
    // =========================================================================

    @Test
    @DisplayName("requestPlayerStats: restituisce le statistiche dell'utente loggato")
    void requestPlayerStats_success() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestPlayerStats\"}", ch));
            assertOk(res);
            assertEquals("alice", res.get("username").getAsString());
            assertTrue(res.has("puzzlesCompleted"));
            assertTrue(res.has("winRate"));
            assertTrue(res.has("lossRate"));
            assertTrue(res.has("currentStreak"));
            assertTrue(res.has("maxStreak"));
            assertTrue(res.has("perfectPuzzles"));
            assertTrue(res.has("totalScore"));
            assertTrue(res.has("mistakeHistogram"));
            assertEquals(6, res.getAsJsonArray("mistakeHistogram").size(),
                "mistakeHistogram deve avere 6 bucket (indici 0-5)");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestPlayerStats: senza login → NOT_LOGGED_IN")
    void requestPlayerStats_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestPlayerStats\"}", ch));
            assertError(res, "NOT_LOGGED_IN");
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("requestPlayerStats: dopo una vittoria riflette il punteggio aggiornato")
    void requestPlayerStats_afterWin_reflectsScore() throws IOException {
        SocketChannel ch = loginAs("alice", "pwd");
        try {
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"A\",\"B\",\"C\",\"D\"]}", ch);
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"E\",\"F\",\"G\",\"H\"]}", ch);
            handler.process("{\"operation\":\"submitProposal\",\"words\":[\"I\",\"J\",\"K\",\"L\"]}", ch);

            JsonObject res = parse(handler.process(
                "{\"operation\":\"requestPlayerStats\"}", ch));
            assertOk(res);
            assertEquals(18, res.get("totalScore").getAsInt());
            assertEquals(1,  res.get("puzzlesCompleted").getAsInt());
            assertEquals(1,  res.get("currentStreak").getAsInt());
        } finally { ch.close(); }
    }
}
