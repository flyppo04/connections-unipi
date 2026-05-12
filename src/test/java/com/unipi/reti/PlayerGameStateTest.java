package com.unipi.reti;

import com.unipi.models.PlayerGameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per PlayerGameState.
 * Verifica: punteggio, errori, vittoria, sconfitta, timeout, idempotenza.
 */
@DisplayName("PlayerGameState — logica di gioco del singolo giocatore")
class PlayerGameStateTest {

    private PlayerGameState state;

    @BeforeEach
    void setUp() {
        state = new PlayerGameState("alice");
    }

    // ─── Stato iniziale ──────────────────────────────────────────────

    @Test
    @DisplayName("Stato iniziale: nessun errore, nessun gruppo trovato, non finito")
    void initialState_allDefaultValues() {
        assertEquals(0, state.getMistakes());
        assertEquals(0, state.getScore());
        assertFalse(state.hasFinished());
        assertFalse(state.isWinner());
        assertTrue(state.getCorrectGroupsFound().isEmpty());
        assertTrue(state.getWordsAlreadyFound().isEmpty());
    }

    // ─── Proposta corretta ────────────────────────────────────────────

    @Test
    @DisplayName("addCorrectGroup: punteggio aumenta di +6 per ogni gruppo")
    void addCorrectGroup_scoreIncreasesBy6() {
        state.addCorrectGroup("T1", List.of("A", "B", "C", "D"));
        assertEquals(6, state.getScore());

        state.addCorrectGroup("T2", List.of("E", "F", "G", "H"));
        assertEquals(12, state.getScore());
    }

    @Test
    @DisplayName("addCorrectGroup: le parole usate vengono aggiunte a wordsAlreadyFound")
    void addCorrectGroup_wordsAreTracked() {
        state.addCorrectGroup("T1", List.of("ALPHA", "BETA", "GAMMA", "DELTA"));
        assertTrue(state.getWordsAlreadyFound().containsAll(List.of("ALPHA", "BETA", "GAMMA", "DELTA")));
    }

    @Test
    @DisplayName("Vittoria: hasFinished e isWinner diventano true dopo 3 gruppi corretti")
    void addCorrectGroup_threeGroupsTriggersWin() {
        state.addCorrectGroup("T1", List.of("A", "B", "C", "D"));
        state.addCorrectGroup("T2", List.of("E", "F", "G", "H"));

        assertFalse(state.hasFinished(), "Dopo 2 gruppi non deve essere finita");

        state.addCorrectGroup("T3", List.of("I", "J", "K", "L"));

        assertTrue(state.hasFinished());
        assertTrue(state.isWinner());
        assertEquals(18, state.getScore());
    }

    @Test
    @DisplayName("addCorrectGroup: aggiungere un gruppo già trovato non ha effetto (idempotenza)")
    void addCorrectGroup_duplicateGroupIgnored() {
        state.addCorrectGroup("T1", List.of("A", "B", "C", "D"));
        state.addCorrectGroup("T1", List.of("A", "B", "C", "D")); // secondo tentativo

        assertEquals(1, state.getCorrectGroupsFound().size());
        assertEquals(6, state.getScore()); // punteggio non raddoppia
    }

    // ─── Errori ───────────────────────────────────────────────────────

    @Test
    @DisplayName("addMistake: punteggio scende di -4 per ogni errore")
    void addMistake_scoredDecreasesBy4() {
        state.addMistake();
        assertEquals(-4, state.getScore());

        state.addMistake();
        assertEquals(-8, state.getScore());
    }

    @Test
    @DisplayName("Sconfitta: hasFinished diventa true al 4° errore, isWinner resta false")
    void addMistake_fourMistakesTriggersLoss() {
        state.addMistake();
        state.addMistake();
        state.addMistake();
        assertFalse(state.hasFinished(), "Al 3° errore non deve ancora essere finita");

        state.addMistake();
        assertTrue(state.hasFinished());
        assertFalse(state.isWinner());
        assertEquals(-16, state.getScore());
    }

    @Test
    @DisplayName("addMistake non ha effetto dopo che la partita è già finita")
    void addMistake_ignoredAfterGameOver() {
        // Finisco per 4 errori
        state.addMistake(); state.addMistake();
        state.addMistake(); state.addMistake(); // fine partita

        state.addMistake(); // questo NON deve essere conteggiato

        assertEquals(4, state.getMistakes());
        assertEquals(-16, state.getScore());
    }

    // ─── Punteggio misto ─────────────────────────────────────────────

    @Test
    @DisplayName("Esempio dal PDF: 1 proposta sbagliata, 1 corretta, 1 sbagliata → score = -2")
    void mixedScenario_wrongCorrectWrong() {
        state.addMistake();
        state.addCorrectGroup("T1", List.of("A", "B", "C", "D"));
        state.addMistake();

        // -4 + 6 - 4 = -2
        assertEquals(-2, state.getScore());
    }

    @Test
    @DisplayName("Esempio dal PDF: vince con 3 errori → score = 18 - 12 = +6")
    void mixedScenario_winWithThreeMistakes() {
        state.addMistake(); state.addMistake(); state.addMistake(); // -12
        state.addCorrectGroup("T1", List.of("A","B","C","D"));
        state.addCorrectGroup("T2", List.of("E","F","G","H"));
        state.addCorrectGroup("T3", List.of("I","J","K","L")); // +18, vittoria

        assertEquals(6, state.getScore());
        assertTrue(state.isWinner());
    }

    // ─── Timeout ─────────────────────────────────────────────────────

    @Test
    @DisplayName("forceTimeOut: hasFinished diventa true, isWinner resta false")
    void forceTimeOut_finishesWithoutWinning() {
        state.addCorrectGroup("T1", List.of("A", "B", "C", "D")); // aveva trovato 1
        state.forceTimeOut();

        assertTrue(state.hasFinished());
        assertFalse(state.isWinner());
    }

    @Test
    @DisplayName("forceTimeOut non sovrascrive una vittoria già avvenuta")
    void forceTimeOut_doesNotOverrideWin() {
        state.addCorrectGroup("T1", List.of("A","B","C","D"));
        state.addCorrectGroup("T2", List.of("E","F","G","H"));
        state.addCorrectGroup("T3", List.of("I","J","K","L")); // ha già vinto

        state.forceTimeOut(); // il timer scatta lo stesso, non deve cambiare niente

        assertTrue(state.isWinner()); // deve restare vincitore
    }
}
