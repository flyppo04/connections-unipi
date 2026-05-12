package com.unipi.reti;

import com.unipi.data.GroupData;
import com.unipi.data.PuzzleData;
import com.unipi.data.PuzzleReader;
import com.unipi.game.GameManager;
import com.unipi.game.UserManager;
import com.unipi.models.Game;
import com.unipi.models.User;
import com.unipi.server.PersistenceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test aggiuntivi — casi limite e interazioni tra classi.
 *
 * FIX rispetto alla versione originale:
 *   - GameManager ora usa il costruttore a 4 parametri (aggiunto NoPersistenceManager stub).
 *   - Il test del NewGameCallback verifica che la nuova partita sia passata, non la vecchia.
 */
@DisplayName("Test aggiuntivi — casi limite e interazioni")
class ExtraTests {

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

    // ─── Stub PersistenceManager — nessuna I/O su disco ──────────────
    static class NoPersistenceManager extends PersistenceManager {
        NoPersistenceManager(UserManager um) { super("/dev/null", "/dev/null", um); }
        @Override public void saveUsers() {}
        @Override public void markUsersDirty() {}
        @Override public synchronized void saveCompletedGame(Game game) {}
        @Override public synchronized Game findCompletedGameById(int gameId) { return null; }
    }

    private UserManager um;
    private GameManager gm;

    @BeforeEach
    void setUp() {
        um = UserManager.createNew();
        um.register("alice", "pwd");
        um.register("bob", "pwd");
        gm = new GameManager(new FakePuzzleReader(), um, 60, new NoPersistenceManager(um));
        gm.startNewGame();
    }

    @AfterEach
    void tearDown() {
        gm.shutdown();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEZIONE 1: Verifica fix già applicati
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("[FIX] Parole fuori board NON scalano errori al giocatore")
    void fix_offBoardWords_doNotCostMistakes() {
        gm.submitProposal("alice", List.of("A","B","C","E"));          // 1 errore reale
        gm.submitProposal("alice", List.of("CANE","GATTO","PESCE","MELA")); // MALFORMED
        gm.submitProposal("alice", List.of("X","Y","Z","W"));          // MALFORMED

        assertEquals(1, gm.getCurrentGame().getPlayerState("alice").getMistakes());
    }

    @Test
    @DisplayName("[FIX] Vincere aggiorna il totalScore dell'utente")
    void fix_win_addScore() {
        gm.submitProposal("alice", List.of("A","B","C","D"));
        gm.submitProposal("alice", List.of("E","F","G","H"));
        gm.submitProposal("alice", List.of("I","J","K","L"));

        assertNotEquals(0, um.getUserByUsername("alice").getTotalScore());
        assertEquals(18, um.getUserByUsername("alice").getTotalScore());
    }

    @Test
    @DisplayName("[FIX] Perdere aggiorna il totalScore con penalità")
    void fix_loss_addScore() {
        gm.submitProposal("alice", List.of("A","B","C","E"));
        gm.submitProposal("alice", List.of("A","B","C","F"));
        gm.submitProposal("alice", List.of("A","B","C","G"));
        gm.submitProposal("alice", List.of("A","B","C","H"));

        assertNotEquals(0, um.getUserByUsername("alice").getTotalScore());
        assertEquals(-16, um.getUserByUsername("alice").getTotalScore());
    }

    @Test
    @DisplayName("[FIX] NewGameCallback riceve la nuova partita, non la vecchia")
    void fix_newGameCallback_receivesNewGame() {
        int oldId = gm.getCurrentGame().getGameId();
        java.util.concurrent.atomic.AtomicInteger receivedId =
            new java.util.concurrent.atomic.AtomicInteger(-1);

        gm.setNewGameCallback(g -> receivedId.set(g.getGameId()));

        try {
            java.lang.reflect.Method m =
                GameManager.class.getDeclaredMethod("endCurrentGame");
            m.setAccessible(true);
            m.invoke(gm);
        } catch (Exception e) {
            fail("Impossibile invocare endCurrentGame: " + e.getMessage());
        }

        assertNotEquals(oldId, receivedId.get(),
            "BUG: onNewGame riceve la vecchia partita");
        assertEquals(gm.getCurrentGame().getGameId(), receivedId.get(),
            "onNewGame deve ricevere la nuova partita attiva");
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEZIONE 2: Casi limite non coperti altrove
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Vincere con 2 errori: score end-to-end e tracker corretti")
    void win_withMistakes_endToEnd() {
        gm.submitProposal("alice", List.of("A","B","C","E")); // -4
        gm.submitProposal("alice", List.of("A","B","C","F")); // -4
        gm.submitProposal("alice", List.of("A","B","C","D")); // +6
        gm.submitProposal("alice", List.of("E","F","G","H")); // +12
        gm.submitProposal("alice", List.of("I","J","K","L")); // +18, vince

        User alice = um.getUserByUsername("alice");
        assertEquals(10, alice.getTotalScore());
        assertEquals(1, alice.getPuzzlesTracker()[2]);
    }

    @Test
    @DisplayName("Vittoria perfetta (0 errori): solo puzzlesTracker[0] incrementato")
    void win_perfect_onlyBucket0() {
        gm.submitProposal("alice", List.of("A","B","C","D"));
        gm.submitProposal("alice", List.of("E","F","G","H"));
        gm.submitProposal("alice", List.of("I","J","K","L"));

        User alice = um.getUserByUsername("alice");
        assertEquals(1, alice.getPuzzlesTracker()[0]);
        for (int i = 1; i <= 5; i++) {
            assertEquals(0, alice.getPuzzlesTracker()[i],
                "puzzlesTracker[" + i + "] deve essere 0 per vittoria perfetta");
        }
    }

    @Test
    @DisplayName("getGame(id corrente): la partita è reperibile per id")
    void getGame_currentGameById() {
        int id = gm.getCurrentGame().getGameId();
        assertNotNull(gm.getGame(id));
        assertSame(gm.getCurrentGame(), gm.getGame(id));
    }

    @Test
    @DisplayName("getGame(id inesistente): restituisce null")
    void getGame_unknownId() {
        assertNull(gm.getGame(9999));
    }

    @Test
    @DisplayName("submitProposal senza startNewGame → NO_ACTIVE_GAME")
    void submitProposal_noActiveGame() {
        GameManager fresh = new GameManager(
            new FakePuzzleReader(), um, 60, new NoPersistenceManager(um));
        assertEquals(GameManager.ProposalResult.NO_ACTIVE_GAME,
            fresh.submitProposal("alice", List.of("A","B","C","D")));
        fresh.shutdown();
    }

    @Test
    @DisplayName("Due giocatori nella stessa partita sono indipendenti")
    void twoPlayers_independentStates() {
        gm.submitProposal("alice", List.of("A","B","C","E")); // errore alice
        gm.submitProposal("bob",   List.of("A","B","C","D")); // corretto bob

        assertEquals(1, gm.getCurrentGame().getPlayerState("alice").getMistakes());
        assertEquals(0, gm.getCurrentGame().getPlayerState("bob").getMistakes());
        assertEquals(6, gm.getCurrentGame().getPlayerState("bob").getScore());
    }

    @Test
    @DisplayName("Leaderboard riflette totalScore aggiornato dopo partita")
    void leaderboard_reflectsScoreAfterGame() {
        gm.submitProposal("alice", List.of("A","B","C","D"));
        gm.submitProposal("alice", List.of("E","F","G","H"));
        gm.submitProposal("alice", List.of("I","J","K","L")); // +18

        gm.submitProposal("bob", List.of("A","B","C","E"));
        gm.submitProposal("bob", List.of("A","B","C","F"));
        gm.submitProposal("bob", List.of("A","B","C","G"));
        gm.submitProposal("bob", List.of("A","B","C","H")); // -16

        var lb = um.getLeaderboard();
        assertEquals("alice", lb.get(0).getUsername());
        assertEquals("bob",   lb.get(1).getUsername());
    }

    @Test
    @DisplayName("GameEndCallback viene invocato alla fine della partita")
    void gameEndCallback_isCalledOnEnd() {
        java.util.concurrent.atomic.AtomicBoolean called =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        gm.setGameEndCallback(g -> called.set(true));

        try {
            java.lang.reflect.Method m =
                GameManager.class.getDeclaredMethod("endCurrentGame");
            m.setAccessible(true);
            m.invoke(gm);
        } catch (Exception e) {
            fail("Impossibile invocare endCurrentGame: " + e.getMessage());
        }

        assertTrue(called.get(), "GameEndCallback non è stato invocato");
    }

    // ─── UserManager edge cases ───────────────────────────────────────

    @Test
    @DisplayName("updateCredentials mentre loggato aggiorna session maps")
    void updateCredentials_whileLoggedIn_updatesSession() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            um.login("alice", "pwd", ch);
            um.updateCredentials("alice", "pwd", "alice2", null);

            assertFalse(um.isLoggedIn("alice"), "'alice' non deve più risultare loggata");
            assertTrue(um.isLoggedIn("alice2"), "'alice2' deve risultare loggata");
            User found = um.getUserByChannel(ch);
            assertNotNull(found);
            assertEquals("alice2", found.getUsername());
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("getUserByChannel restituisce null per canale non loggato")
    void getUserByChannel_nullIfNotLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            assertNull(um.getUserByChannel(ch));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("getAllUsers restituisce una collection non modificabile")
    void getAllUsers_isUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
            () -> um.getAllUsers().add(new User("hacker", "pwd")));
    }

    // ─── User streak ──────────────────────────────────────────────────

    @Test
    @DisplayName("Streak cresce con vittorie consecutive")
    void user_streak_growsWithConsecutiveWins() {
        User u = new User("test", "pwd");
        u.updateCurentStreak();
        u.updateCurentStreak();
        u.updateCurentStreak();
        assertEquals(3, u.getCurrentStreak());
        assertEquals(3, u.getMaxStreak());
    }

    @Test
    @DisplayName("Streak si azzera dopo sconfitta, maxStreak non cala")
    void user_streak_resetsAfterLoss() {
        User u = new User("test", "pwd");
        u.updateCurentStreak();
        u.updateCurentStreak();
        u.updateCurentStreak();
        u.resetCurrentStreak();
        u.updateCurentStreak();
        assertEquals(1, u.getCurrentStreak());
        assertEquals(3, u.getMaxStreak());
    }
}
