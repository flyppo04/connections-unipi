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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per GameManager.
 * Usa un PuzzleReader "fake" (stub) per non dipendere dal file JSON reale.
 * Usa un PersistenceManager stub che non fa nulla (no I/O su disco).
 *
 * Puzzle fisso del test:
 *   T1 → A, B, C, D
 *   T2 → E, F, G, H
 *   T3 → I, J, K, L
 *   T4 → M, N, O, P
 *
 * FIX: costruttore GameManager aggiornato a 4 parametri (aggiunto PersistenceManager).
 */
@DisplayName("GameManager — logica di gioco e aggiornamento statistiche")
class GameManagerTest {

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
    /**
     * Sottoclasse che sovrascrive i metodi di scrittura per renderli no-op.
     * Permette di testare GameManager senza dipendenza dal filesystem.
     */
    static class NoPersistenceManager extends PersistenceManager {
        NoPersistenceManager(UserManager um) {
            super("/dev/null", "/dev/null", um);
        }
        @Override public void saveUsers() {}
        @Override public void markUsersDirty() {}
        @Override public synchronized void saveCompletedGame(Game game) {}
        @Override public synchronized Game findCompletedGameById(int gameId) { return null; }
    }

    private UserManager userManager;
    private GameManager gameManager;

    @BeforeEach
    void setUp() {
        userManager = UserManager.createNew();
        userManager.register("alice", "pwd");
        userManager.register("bob", "pwd");

        NoPersistenceManager pm = new NoPersistenceManager(userManager);
        gameManager = new GameManager(new FakePuzzleReader(), userManager, 60, pm);
        gameManager.startNewGame();
    }

    @AfterEach
    void tearDown() {
        gameManager.shutdown();
    }

    // ─── hasActiveGame ────────────────────────────────────────────────

    @Test
    @DisplayName("hasActiveGame restituisce true subito dopo startNewGame")
    void hasActiveGame_trueAfterStart() {
        assertTrue(gameManager.hasActiveGame());
    }

    // ─── MALFORMED ────────────────────────────────────────────────────

    @Test
    @DisplayName("submitProposal: meno di 4 parole → MALFORMED")
    void submitProposal_tooFewWords() {
        assertEquals(GameManager.ProposalResult.MALFORMED,
            gameManager.submitProposal("alice", List.of("A","B","C")));
    }

    @Test
    @DisplayName("submitProposal: più di 4 parole → MALFORMED")
    void submitProposal_tooManyWords() {
        assertEquals(GameManager.ProposalResult.MALFORMED,
            gameManager.submitProposal("alice", List.of("A","B","C","D","E")));
    }

    @Test
    @DisplayName("submitProposal: parole duplicate → MALFORMED")
    void submitProposal_duplicateWords() {
        assertEquals(GameManager.ProposalResult.MALFORMED,
            gameManager.submitProposal("alice", List.of("A","A","C","D")));
    }

    @Test
    @DisplayName("submitProposal: parole non presenti sulla board → MALFORMED")
    void submitProposal_wordsNotOnBoard() {
        assertEquals(GameManager.ProposalResult.MALFORMED,
            gameManager.submitProposal("alice", List.of("MELA","PERA","BANANA","KIWI")));
    }

    @Test
    @DisplayName("submitProposal: parole fuori board NON scalano errori")
    void submitProposal_offBoardWords_doNotCostMistakes() {
        gameManager.submitProposal("alice", List.of("MELA","PERA","BANANA","KIWI"));
        Game game = gameManager.getCurrentGame();
        game.addPlayer("alice");
        assertEquals(0, game.getPlayerState("alice").getMistakes());
    }

    // ─── WRONG ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submitProposal: parole valide ma gruppo sbagliato → WRONG")
    void submitProposal_wrong() {
        assertEquals(GameManager.ProposalResult.WRONG,
            gameManager.submitProposal("alice", List.of("A","B","C","E")));
    }

    @Test
    @DisplayName("submitProposal: errore incrementa mistakes nel PlayerGameState")
    void submitProposal_wrongIncrementsMistakes() {
        gameManager.submitProposal("alice", List.of("A","B","C","E"));
        assertEquals(1, gameManager.getCurrentGame().getPlayerState("alice").getMistakes());
    }

    // ─── CORRECT ─────────────────────────────────────────────────────

    @Test
    @DisplayName("submitProposal: gruppo corretto → CORRECT")
    void submitProposal_correct() {
        assertEquals(GameManager.ProposalResult.CORRECT,
            gameManager.submitProposal("alice", List.of("A","B","C","D")));
    }

    @Test
    @DisplayName("submitProposal: gruppo corretto incrementa score di +6")
    void submitProposal_correctIncreasesScore() {
        gameManager.submitProposal("alice", List.of("A","B","C","D"));
        assertEquals(6, gameManager.getCurrentGame().getPlayerState("alice").getScore());
    }

    @Test
    @DisplayName("submitProposal: confronto case-insensitive (minuscolo accettato)")
    void submitProposal_caseInsensitive() {
        assertEquals(GameManager.ProposalResult.CORRECT,
            gameManager.submitProposal("alice", List.of("a","b","c","d")));
    }

    // ─── MALFORMED su parole già trovate ─────────────────────────────

    @Test
    @DisplayName("submitProposal: riproporre parole già raggruppate → MALFORMED")
    void submitProposal_alreadyFoundWords() {
        gameManager.submitProposal("alice", List.of("A","B","C","D"));
        assertEquals(GameManager.ProposalResult.MALFORMED,
            gameManager.submitProposal("alice", List.of("A","B","C","D")));
    }

    // ─── GAME_ALREADY_OVER ────────────────────────────────────────────

    @Test
    @DisplayName("submitProposal: giocatore che ha vinto → GAME_ALREADY_OVER")
    void submitProposal_gameAlreadyOver_winner() {
        gameManager.submitProposal("alice", List.of("A","B","C","D"));
        gameManager.submitProposal("alice", List.of("E","F","G","H"));
        gameManager.submitProposal("alice", List.of("I","J","K","L"));
        assertEquals(GameManager.ProposalResult.GAME_ALREADY_OVER,
            gameManager.submitProposal("alice", List.of("M","N","O","P")));
    }

    @Test
    @DisplayName("submitProposal: giocatore che ha perso → GAME_ALREADY_OVER")
    void submitProposal_gameAlreadyOver_loser() {
        gameManager.submitProposal("alice", List.of("A","B","C","E"));
        gameManager.submitProposal("alice", List.of("A","B","C","F"));
        gameManager.submitProposal("alice", List.of("A","B","C","G"));
        gameManager.submitProposal("alice", List.of("A","B","C","H"));
        assertEquals(GameManager.ProposalResult.GAME_ALREADY_OVER,
            gameManager.submitProposal("alice", List.of("A","B","C","D")));
    }

    // ─── NO_ACTIVE_GAME ───────────────────────────────────────────────

    @Test
    @DisplayName("submitProposal senza startNewGame → NO_ACTIVE_GAME")
    void submitProposal_noActiveGame_beforeStart() {
        NoPersistenceManager pm = new NoPersistenceManager(userManager);
        GameManager fresh = new GameManager(new FakePuzzleReader(), userManager, 60, pm);
        assertEquals(GameManager.ProposalResult.NO_ACTIVE_GAME,
            fresh.submitProposal("alice", List.of("A","B","C","D")));
        fresh.shutdown();
    }

    // ─── Aggiornamento statistiche utente ────────────────────────────

    @Test
    @DisplayName("Vincere aggiorna puzzlesTracker[mistakes] e streak")
    void win_updatesPuzzlesTrackerAndStreak() {
        gameManager.submitProposal("alice", List.of("A","B","C","D"));
        gameManager.submitProposal("alice", List.of("E","F","G","H"));
        gameManager.submitProposal("alice", List.of("I","J","K","L"));

        User alice = userManager.getUserByUsername("alice");
        assertEquals(1, alice.getPuzzlesTracker()[0]);
        assertEquals(1, alice.getCurrentStreak());
    }

    @Test
    @DisplayName("Vincere aggiorna il totalScore dell'utente")
    void win_updatesTotalScore() {
        gameManager.submitProposal("alice", List.of("A","B","C","D"));
        gameManager.submitProposal("alice", List.of("E","F","G","H"));
        gameManager.submitProposal("alice", List.of("I","J","K","L"));
        assertEquals(18, userManager.getUserByUsername("alice").getTotalScore());
    }

    @Test
    @DisplayName("Perdere per 4 errori aggiorna tracker[4] e azzera streak")
    void loss_updatesPuzzlesTrackerAndStreak() {
        gameManager.submitProposal("alice", List.of("A","B","C","E"));
        gameManager.submitProposal("alice", List.of("A","B","C","F"));
        gameManager.submitProposal("alice", List.of("A","B","C","G"));
        gameManager.submitProposal("alice", List.of("A","B","C","H"));

        User alice = userManager.getUserByUsername("alice");
        assertEquals(1, alice.getPuzzlesTracker()[4]);
        assertEquals(0, alice.getCurrentStreak());
    }

    @Test
    @DisplayName("Perdere aggiorna il totalScore con penalità negative")
    void loss_updatesNegativeScore() {
        gameManager.submitProposal("alice", List.of("A","B","C","E"));
        gameManager.submitProposal("alice", List.of("A","B","C","F"));
        gameManager.submitProposal("alice", List.of("A","B","C","G"));
        gameManager.submitProposal("alice", List.of("A","B","C","H"));
        assertEquals(-16, userManager.getUserByUsername("alice").getTotalScore());
    }

    @Test
    @DisplayName("Vincere con 2 errori: score e tracker corretti")
    void win_withMistakes_correctScoreAndTracker() {
        gameManager.submitProposal("alice", List.of("A","B","C","E")); // -4
        gameManager.submitProposal("alice", List.of("A","B","C","F")); // -4
        gameManager.submitProposal("alice", List.of("A","B","C","D")); // +6
        gameManager.submitProposal("alice", List.of("E","F","G","H")); // +12
        gameManager.submitProposal("alice", List.of("I","J","K","L")); // +18, vince

        User alice = userManager.getUserByUsername("alice");
        assertEquals(10, alice.getTotalScore());
        assertEquals(1, alice.getPuzzlesTracker()[2]); // vinta con 2 errori
    }

    @Test
    @DisplayName("Due giocatori nella stessa partita non si influenzano")
    void twoPlayers_independentStates() {
        gameManager.submitProposal("alice", List.of("A","B","C","E")); // errore alice
        gameManager.submitProposal("bob",   List.of("A","B","C","D")); // corretto bob

        assertEquals(1, gameManager.getCurrentGame().getPlayerState("alice").getMistakes());
        assertEquals(0, gameManager.getCurrentGame().getPlayerState("bob").getMistakes());
        assertEquals(6, gameManager.getCurrentGame().getPlayerState("bob").getScore());
    }

    // ─── getGame ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getGame(id corrente): restituisce la partita attiva")
    void getGame_currentGame() {
        int id = gameManager.getCurrentGame().getGameId();
        assertSame(gameManager.getCurrentGame(), gameManager.getGame(id));
    }

    @Test
    @DisplayName("getGame(id inesistente): restituisce null")
    void getGame_unknownId() {
        assertNull(gameManager.getGame(9999));
    }

    // ─── Callback ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GameEndCallback viene invocato quando la partita termina")
    void gameEndCallback_isCalledOnEnd() {
        java.util.concurrent.atomic.AtomicBoolean called =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        gameManager.setGameEndCallback(g -> called.set(true));

        try {
            java.lang.reflect.Method m =
                GameManager.class.getDeclaredMethod("endCurrentGame");
            m.setAccessible(true);
            m.invoke(gameManager);
        } catch (Exception e) {
            fail("Impossibile invocare endCurrentGame: " + e.getMessage());
        }

        assertTrue(called.get(), "GameEndCallback non è stato invocato");
    }

    @Test
    @DisplayName("NewGameCallback riceve la nuova partita (non la vecchia)")
    void newGameCallback_receivesNewGame() {
        int oldId = gameManager.getCurrentGame().getGameId();
        java.util.concurrent.atomic.AtomicInteger receivedId =
            new java.util.concurrent.atomic.AtomicInteger(-1);

        gameManager.setNewGameCallback(g -> receivedId.set(g.getGameId()));

        try {
            java.lang.reflect.Method m =
                GameManager.class.getDeclaredMethod("endCurrentGame");
            m.setAccessible(true);
            m.invoke(gameManager);
        } catch (Exception e) {
            fail("Impossibile invocare endCurrentGame: " + e.getMessage());
        }

        // Il callback deve ricevere il gameId della NUOVA partita, non della vecchia
        assertNotEquals(oldId, receivedId.get(),
            "BUG: onNewGame sta ricevendo la vecchia partita invece della nuova");
        assertEquals(gameManager.getCurrentGame().getGameId(), receivedId.get());
    }

    // ─── Shutdown ────────────────────────────────────────────────────

    @Test
    @DisplayName("shutdown non lancia eccezioni")
    void shutdown_doesNotThrow() {
        assertDoesNotThrow(() -> gameManager.shutdown());
    }
}
