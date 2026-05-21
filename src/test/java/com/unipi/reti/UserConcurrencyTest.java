package com.unipi.reti;

import com.unipi.models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di concorrenza per User.
 * Verifica che synchronized protegga correttamente da race condition.
 */
@DisplayName("User — Test di concorrenza")
class UserConcurrencyTest {

    @Test
    @DisplayName("Race condition: 100 thread incrementano currentStreak — deve arrivare a 100")
    void concurrentStreakUpdate_noDataLoss() throws InterruptedException {
        User user = new User("test", "pwd");
        int numThreads = 100;
        CountDownLatch latch = new CountDownLatch(numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                user.updateCurentStreak();
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(100, user.getCurrentStreak(),
            "Senza synchronized, alcuni incrementi andrebbero persi");
        assertEquals(100, user.getMaxStreak());
    }

    @Test
    @DisplayName("Race condition: streak update + reset concorrenti")
    void concurrentStreakUpdateAndReset() throws InterruptedException {
        User user = new User("test", "pwd");
        int iterations = 1000;
        CountDownLatch latch = new CountDownLatch(iterations * 2);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Thread A: incrementa streak
        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                user.updateCurentStreak();
                latch.countDown();
            });
        }

        // Thread B: resetta streak
        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                user.resetCurrentStreak();
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Non possiamo predire il valore finale, ma deve essere valido (non negativo)
        assertTrue(user.getCurrentStreak() >= 0,
            "currentStreak non deve mai essere negativo");
        assertTrue(user.getMaxStreak() > 0,
            "maxStreak deve aver registrato qualche valore > 0");
    }

    @Test
    @DisplayName("Race condition: scritture concorrenti su puzzlesTracker")
    void concurrentPuzzleTrackerWrites() throws InterruptedException {
        User user = new User("test", "pwd");
        int numWins = 50;
        int numLosses = 30;
        int numTimeouts = 20;

        CountDownLatch latch = new CountDownLatch(numWins + numLosses + numTimeouts);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Vittorie con 0-3 errori
        for (int i = 0; i < numWins; i++) {
            final int mistakes = i % 4; // cicla 0,1,2,3
            executor.submit(() -> {
                user.recordWinWithMistakes(mistakes);
                latch.countDown();
            });
        }

        // Sconfitte per errori
        for (int i = 0; i < numLosses; i++) {
            executor.submit(() -> {
                user.recordLossByMistakes();
                latch.countDown();
            });
        }

        // Timeout
        for (int i = 0; i < numTimeouts; i++) {
            executor.submit(() -> {
                user.recordTimeout();
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: somma totale deve essere numWins + numLosses + numTimeouts
        int totalRecorded = 0;
        int[] tracker = user.getPuzzlesTracker();
        for (int count : tracker) {
            totalRecorded += count;
        }

        assertEquals(100, totalRecorded,
            "Senza synchronized, alcuni incrementi su puzzlesTracker andrebbero persi");
        assertEquals(30, tracker[4], "Sconfitte per errori");
        assertEquals(20, tracker[5], "Timeout");
    }

    @Test
    @DisplayName("Race condition: letture concorrenti durante scrittura")
    void concurrentReadsDuringWrites() throws InterruptedException {
        User user = new User("test", "pwd");
        int numOperations = 1000;
        CountDownLatch latch = new CountDownLatch(numOperations * 2);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        AtomicInteger readErrors = new AtomicInteger(0);

        // Thread scrittori
        for (int i = 0; i < numOperations; i++) {
            executor.submit(() -> {
                user.addScore(10);
                user.recordWinWithMistakes(0);
                latch.countDown();
            });
        }

        // Thread lettori
        for (int i = 0; i < numOperations; i++) {
            executor.submit(() -> {
                try {
                    int score = user.getTotalScore();
                    float winRate = user.getWinRate();
                    int[] tracker = user.getPuzzlesTracker();

                    // Verifica coerenza: score e tracker devono essere >= 0
                    if (score < 0 || winRate < 0 || tracker == null) {
                        readErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    readErrors.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(0, readErrors.get(),
            "Le letture non devono mai lanciare eccezioni o leggere valori inconsistenti");
        assertEquals(10000, user.getTotalScore(), "1000 operazioni × 10 punti");
        assertEquals(1000, user.getPuzzlesCompleted());
    }

    @RepeatedTest(10)
    @DisplayName("Stress test: 1000 operazioni miste concorrenti")
    void stressTest_mixedOperations() throws InterruptedException {
        User user = new User("stress", "test");
        int numOps = 1000;
        CountDownLatch latch = new CountDownLatch(numOps);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int i = 0; i < numOps; i++) {
            final int op = i % 10;
            executor.submit(() -> {
                try {
                    switch (op) {
                        case 0 -> user.updateCurentStreak();
                        case 1 -> user.resetCurrentStreak();
                        case 2 -> user.addScore(5);
                        case 3 -> user.recordWinWithMistakes(0);
                        case 4 -> user.recordWinWithMistakes(2);
                        case 5 -> user.recordLossByMistakes();
                        case 6 -> user.recordTimeout();
                        case 7 -> user.getTotalScore();
                        case 8 -> user.getWinRate();
                        case 9 -> user.getPuzzlesTracker();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: nessun dato deve essere corrotto
        assertTrue(user.getTotalScore() >= 0);
        assertTrue(user.getCurrentStreak() >= 0);
        assertTrue(user.getWinRate() >= 0 && user.getWinRate() <= 100);
        assertTrue(user.getLossRate() >= 0 && user.getLossRate() <= 100);
        assertNotNull(user.getPuzzlesTracker());
    }

    @Test
    @DisplayName("Defensive copy: modificare array restituito da getPuzzlesTracker non deve cambiare l'interno")
    void defensiveCopy_puzzlesTracker() throws InterruptedException {
        User user = new User("test", "pwd");
        user.recordWinWithMistakes(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                int[] copy = user.getPuzzlesTracker();
                // Tenta di modificare la copia
                for (int j = 0; j < copy.length; j++) {
                    copy[j] = 999;
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // L'originale non deve essere stato modificato
        int[] actual = user.getPuzzlesTracker();
        assertEquals(1, actual[0], "Valore originale deve essere intatto");
        for (int i = 1; i < actual.length; i++) {
            assertEquals(0, actual[i]);
        }
    }

    @Test
    @DisplayName("Concorrenza: addScore da più thread — somma corretta")
    void concurrentAddScore() throws InterruptedException {
        User user = new User("test", "pwd");
        int numThreads = 100;
        int pointsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                user.addScore(pointsPerThread);
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(1000, user.getTotalScore(),
            "100 thread × 10 punti = 1000 (senza race)");
    }

    @Test
    @DisplayName("Concorrenza: setters e getters di username/password")
    void concurrentCredentialChanges() throws InterruptedException {
        User user = new User("original", "pwd");
        int numOps = 500;
        CountDownLatch latch = new CountDownLatch(numOps * 4);

        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Thread che cambiano username
        for (int i = 0; i < numOps; i++) {
            final int id = i;
            executor.submit(() -> {
                user.setUsername("user" + id);
                latch.countDown();
            });
        }

        // Thread che leggono username
        for (int i = 0; i < numOps; i++) {
            executor.submit(() -> {
                String name = user.getUsername();
                assertNotNull(name, "Username non deve mai essere null");
                latch.countDown();
            });
        }

        // Thread che cambiano password
        for (int i = 0; i < numOps; i++) {
            final int id = i;
            executor.submit(() -> {
                user.setPassword("pwd" + id);
                latch.countDown();
            });
        }

        // Thread che leggono password
        for (int i = 0; i < numOps; i++) {
            executor.submit(() -> {
                String pwd = user.getPassword();
                assertNotNull(pwd, "Password non deve mai essere null");
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: username e password finali devono essere non-null e coerenti
        assertNotNull(user.getUsername());
        assertNotNull(user.getPassword());
    }
}
