package com.unipi.reti;

import com.unipi.models.Game;
import com.unipi.models.PlayerGameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per la classe Game (versione thread-safe con ConcurrentHashMap).
 * Verifica: gestione giocatori, timer, contatori, punteggi aggregati.
 */
@DisplayName("Game — gestione partita e giocatori")
class GameTest {

    // Puzzle fisso usato in tutti i test
    private static final List<String> WORDS = List.of(
        "A","B","C","D",
        "E","F","G","H",
        "I","J","K","L",
        "M","N","O","P"
    );
    private static final Map<String, List<String>> CONNECTIONS = Map.of(
        "T1", List.of("A","B","C","D"),
        "T2", List.of("E","F","G","H"),
        "T3", List.of("I","J","K","L"),
        "T4", List.of("M","N","O","P")
    );

    private Game game;

    @BeforeEach
    void setUp() {
        // Durata generosa: 30 secondi
        game = new Game(1, WORDS, CONNECTIONS, 30_000);
    }

    // ─── Stato iniziale ──────────────────────────────────────────────

    @Test
    @DisplayName("Nuova partita: nessun giocatore, non scaduta")
    void newGame_noPlayersNotExpired() {
        assertEquals(0, game.getTotalPlayers());
        assertFalse(game.isExpired());
        assertTrue(game.getRemainingTimeInSeconds() > 0);
    }

    @Test
    @DisplayName("getWords restituisce le 16 parole della partita")
    void getWords_returns16Words() {
        assertEquals(16, game.getWords().size());
    }

    @Test
    @DisplayName("getConnections contiene esattamente 4 gruppi")
    void getConnections_hasFourGroups() {
        assertEquals(4, game.getConnections().size());
    }

    // ─── Gestione giocatori ───────────────────────────────────────────

    @Test
    @DisplayName("addPlayer aggiunge un nuovo giocatore con stato iniziale")
    void addPlayer_createsNewState() {
        game.addPlayer("alice");

        assertEquals(1, game.getTotalPlayers());
        assertNotNull(game.getPlayerState("alice"));
    }

    @Test
    @DisplayName("addPlayer è idempotente: aggiungere lo stesso utente due volte non crea duplicati")
    void addPlayer_idempotent() {
        game.addPlayer("alice");
        game.addPlayer("alice"); // secondo tentativo
        game.addPlayer("alice"); // terzo tentativo

        // Deve esserci UN solo PlayerGameState per alice
        assertEquals(1, game.getTotalPlayers());
    }

    @Test
    @DisplayName("getPlayerState restituisce null per un utente mai aggiunto")
    void getPlayerState_nullForUnknownUser() {
        assertNull(game.getPlayerState("unknown"));
    }

    // ─── Contatori giocatori ──────────────────────────────────────────

    @Test
    @DisplayName("getFinishedPlayersCount conta solo chi ha terminato")
    void getFinishedPlayersCount_onlyFinished() {
        game.addPlayer("alice");
        game.addPlayer("bob");

        // Alice vince (3 gruppi corretti)
        PlayerGameState aliceState = game.getPlayerState("alice");
        aliceState.addCorrectGroup("T1", List.of("A","B","C","D"));
        aliceState.addCorrectGroup("T2", List.of("E","F","G","H"));
        aliceState.addCorrectGroup("T3", List.of("I","J","K","L")); // ha vinto

        assertEquals(1, game.getFinishedPlayersCount());
        assertEquals(1, game.getWinnerPlayersCount());
    }

    @Test
    @DisplayName("getWinnerPlayers restituisce solo gli username dei vincitori")
    void getWinnerPlayers_onlyWinners() {
        game.addPlayer("alice");
        game.addPlayer("bob");

        // Alice vince
        PlayerGameState aliceState = game.getPlayerState("alice");
        aliceState.addCorrectGroup("T1", List.of("A","B","C","D"));
        aliceState.addCorrectGroup("T2", List.of("E","F","G","H"));
        aliceState.addCorrectGroup("T3", List.of("I","J","K","L"));

        // Bob perde per timeout
        game.getPlayerState("bob").forceTimeOut();

        List<String> winners = game.getWinnerPlayers();
        assertEquals(1, winners.size());
        assertTrue(winners.contains("alice"));
        assertFalse(winners.contains("bob"));
    }

    // ─── Punteggi aggregati ───────────────────────────────────────────

    @Test
    @DisplayName("getTotalScore somma i punteggi di tutti i giocatori")
    void getTotalScore_sumsAll() {
        game.addPlayer("alice");
        game.addPlayer("bob");

        // Alice: 2 gruppi corretti = +12
        game.getPlayerState("alice").addCorrectGroup("T1", List.of("A","B","C","D"));
        game.getPlayerState("alice").addCorrectGroup("T2", List.of("E","F","G","H"));

        // Bob: 1 errore = -4
        game.getPlayerState("bob").addMistake();

        assertEquals(8, game.getTotalScore()); // 12 + (-4)
    }

    @Test
    @DisplayName("getAverageScore restituisce 0 se non ci sono giocatori")
    void getAverageScore_zeroWhenNoPlayers() {
        assertEquals(0f, game.getAverageScore());
    }

    @Test
    @DisplayName("getAverageScore calcola correttamente la media")
    void getAverageScore_correctAverage() {
        game.addPlayer("alice");
        game.addPlayer("bob");

        // Alice: +12, Bob: 0 → media = 6
        game.getPlayerState("alice").addCorrectGroup("T1", List.of("A","B","C","D"));
        game.getPlayerState("alice").addCorrectGroup("T2", List.of("E","F","G","H"));

        assertEquals(6f, game.getAverageScore(), 0.01f);
    }

    // ─── Timer ────────────────────────────────────────────────────────

    @Test
    @DisplayName("isExpired restituisce false subito dopo la creazione")
    void isExpired_falseOnCreation() {
        assertFalse(game.isExpired());
    }

    @Test
    @DisplayName("isExpired restituisce true dopo la scadenza del timer")
    void isExpired_trueAfterTimeout() throws InterruptedException {
        // Creo una partita con durata molto breve: 100ms
        Game shortGame = new Game(99, WORDS, CONNECTIONS, 100);

        assertFalse(shortGame.isExpired());
        Thread.sleep(200); // attendo la scadenza
        assertTrue(shortGame.isExpired());
    }

    @Test
    @DisplayName("getRemainingTimeInSeconds restituisce 0 dopo la scadenza")
    void getRemainingTime_zeroAfterExpiry() throws InterruptedException {
        Game shortGame = new Game(99, WORDS, CONNECTIONS, 100);
        Thread.sleep(200);
        assertEquals(0, shortGame.getRemainingTimeInSeconds());
    }

    // ─── Immutabilità ────────────────────────────────────────────────

    @Test
    @DisplayName("getWords restituisce una lista non modificabile")
    void getWords_isUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
            () -> game.getWords().add("EXTRA"));
    }

    @Test
    @DisplayName("getConnections restituisce una mappa non modificabile")
    void getConnections_isUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
            () -> game.getConnections().put("T5", List.of("Q","R","S","T")));
    }
}
