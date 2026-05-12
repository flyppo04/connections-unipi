package com.unipi.reti;

import com.unipi.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per la classe User.
 * Verifica: statistiche di partita, streak, win/loss rate, punteggio.
 */
@DisplayName("User — statistiche e punteggio")
class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("alice", "pwd123");
    }

    // ─── Stato iniziale ──────────────────────────────────────────────

    @Test
    @DisplayName("Nuovo utente ha tutte le statistiche a zero")
    void newUser_allStatsAreZero() {
        assertEquals(0, user.getPuzzlesCompleted());
        assertEquals(0, user.getTotalScore());
        assertEquals(0, user.getCurrentStreak());
        assertEquals(0, user.getMaxStreak());
        assertEquals(0.0f, user.getWinRate());
        assertEquals(0.0f, user.getLossRate());
        assertEquals(0, user.getPerfectWins());
    }

    // ─── PuzzlesTracker ───────────────────────────────────────────────

    @Test
    @DisplayName("getPuzzlesCompleted somma tutti i bucket del tracker")
    void getPuzzlesCompleted_sumsAllBuckets() {
        // Vinte con 0,1,2,3 errori (indici 0-3), perse per errori (4), perse per tempo (5)
        user.getPuzzlesTracker()[0]++;  // 1 partita vinta con 0 errori
        user.getPuzzlesTracker()[2]++;  // 1 partita vinta con 2 errori
        user.getPuzzlesTracker()[4]++;  // 1 partita persa per 4 errori
        user.getPuzzlesTracker()[5]++;  // 1 partita persa per tempo

        assertEquals(4, user.getPuzzlesCompleted());
    }

    // ─── WinRate / LossRate ───────────────────────────────────────────

    @Test
    @DisplayName("getWinRate restituisce 0 se nessuna partita giocata")
    void getWinRate_zeroWhenNoPuzzles() {
        assertEquals(0.0f, user.getWinRate());
    }

    @Test
    @DisplayName("getWinRate: 2 vinte su 4 totali = 50%")
    void getWinRate_halfWon() {
        user.getPuzzlesTracker()[0]++;  // vinta
        user.getPuzzlesTracker()[1]++;  // vinta
        user.getPuzzlesTracker()[4]++;  // persa per errori
        user.getPuzzlesTracker()[5]++;  // persa per tempo

        assertEquals(50.0f, user.getWinRate(), 0.01f);
    }

    @Test
    @DisplayName("getLossRate: 1 persa per errori su 4 totali = 25%")
    void getLossRate_oneOutOfFour() {
        user.getPuzzlesTracker()[0]++;  // vinta
        user.getPuzzlesTracker()[1]++;  // vinta
        user.getPuzzlesTracker()[2]++;  // vinta
        user.getPuzzlesTracker()[4]++;  // persa per errori

        assertEquals(25.0f, user.getLossRate(), 0.01f);
    }

    @Test
    @DisplayName("WinRate + LossRate = 100% se ogni partita ha un esito")
    void winRateAndLossRate_sumTo100() {
        user.getPuzzlesTracker()[0]++;  // vinta
        user.getPuzzlesTracker()[4]++;  // persa per errori
        user.getPuzzlesTracker()[5]++;  // persa per tempo

        float total = user.getWinRate() + user.getLossRate();
        assertEquals(100.0f, total, 0.01f);
    }

    // ─── PerfectWins ──────────────────────────────────────────────────

    @Test
    @DisplayName("getPerfectWins conta solo le partite vinte con 0 errori")
    void getPerfectWins_onlyBucket0() {
        user.getPuzzlesTracker()[0] += 3;  // 3 vittorie perfette
        user.getPuzzlesTracker()[1]++;     // vittoria con 1 errore (non perfetta)

        assertEquals(3, user.getPerfectWins());
    }

    // ─── Streak ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCurrentStreak incrementa la streak e aggiorna il massimo")
    void updateCurrentStreak_incrementsAndUpdatesMax() {
        user.updateCurentStreak();
        user.updateCurentStreak();
        user.updateCurentStreak();

        assertEquals(3, user.getCurrentStreak());
        assertEquals(3, user.getMaxStreak());
    }

    @Test
    @DisplayName("resetCurrentStreak azzera la streak ma non il massimo")
    void resetCurrentStreak_doesNotResetMax() {
        user.updateCurentStreak();
        user.updateCurentStreak();  // maxStreak = 2
        user.resetCurrentStreak();

        assertEquals(0, user.getCurrentStreak());
        assertEquals(2, user.getMaxStreak());  // maxStreak deve restare 2
    }

    @Test
    @DisplayName("maxStreak non cala se la streak attuale è inferiore al massimo storico")
    void maxStreak_neverDecreases() {
        user.updateCurentStreak();
        user.updateCurentStreak();
        user.updateCurentStreak();  // max = 3
        user.resetCurrentStreak();
        user.updateCurentStreak();  // streak = 1, max deve restare 3

        assertEquals(3, user.getMaxStreak());
    }

    // ─── Punteggio ───────────────────────────────────────────────────

    @Test
    @DisplayName("addScore accumula correttamente il punteggio totale")
    void addScore_accumulates() {
        user.addScore(18);
        user.addScore(-8);

        assertEquals(10, user.getTotalScore());
    }

    @Test
    @DisplayName("addScore con valori negativi può portare il punteggio sotto zero")
    void addScore_canBeNegative() {
        user.addScore(-4);
        user.addScore(-4);
        user.addScore(-4);
        user.addScore(-4);

        assertEquals(-16, user.getTotalScore());
    }

    // ─── Setter credenziali ───────────────────────────────────────────

    @Test
    @DisplayName("setUsername aggiorna correttamente il nome utente")
    void setUsername_updatesCorrectly() {
        user.setUsername("alice2");
        assertEquals("alice2", user.getUsername());
    }

    @Test
    @DisplayName("setPassword aggiorna correttamente la password")
    void setPassword_updatesCorrectly() {
        user.setPassword("newpass");
        assertEquals("newpass", user.getPassword());
    }
}
