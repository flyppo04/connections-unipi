package com.unipi.reti;

import com.unipi.models.PlayerGameState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di concorrenza per PlayerGameState.
 * Verifica race condition tra submitProposal (addMistake/addCorrectGroup)
 * e timer di fine partita (forceTimeOut).
 */
@DisplayName("PlayerGameState — Test di concorrenza")
class PlayerGameStateConcurrencyTest {

    @Test
    @DisplayName("CRITICO: addMistake vs forceTimeOut — nessuno deve vincere se c'è timeout")
    void criticalRace_addMistakeVsForceTimeOut() throws InterruptedException {
        // Scenario: timer scade MENTRE il giocatore sta inviando la 4a proposta sbagliata
        PlayerGameState state = new PlayerGameState("mario");

        // 3 errori già fatti
        state.addMistake();
        state.addMistake();
        state.addMistake();

        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread A: submitProposal → addMistake (4° errore)
        Future<Boolean> futureA = executor.submit(() -> {
            try {
                state.addMistake();
                return state.hasFinished();
            } finally {
                latch.countDown();
            }
        });

        // Thread B: timer scaduto → forceTimeOut
        Future<Boolean> futureB = executor.submit(() -> {
            try {
                state.forceTimeOut();
                return state.hasFinished();
            } finally {
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: il giocatore DEVE aver finito
        assertTrue(state.hasFinished(), "Dopo addMistake + forceTimeOut deve aver finito");

        // Verifica: mistakes deve essere consistente (3 o 4, non altro)
        int mistakes = state.getMistakes();
        assertTrue(mistakes == 3 || mistakes == 4,
            "Mistakes deve essere 3 (se timeout è arrivato prima) o 4 (se addMistake è arrivato prima)");

        // Verifica: isWinner deve essere false (o timeout o 4 errori)
        assertFalse(state.isWinner(),
            "In entrambi i casi (timeout o 4 errori) non è un vincitore");

        // Verifica: score deve essere coerente con mistakes
        int expectedScore = mistakes * -4;
        assertEquals(expectedScore, state.getScore(),
            "Score deve essere coerente con il numero di errori");
    }

    @Test
    @DisplayName("Race: addCorrectGroup concorrente con hasFinished check")
    void race_addCorrectGroupDuringFinishCheck() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("alice");

        // Alice trova 2 gruppi
        state.addCorrectGroup("G1", List.of("A", "B", "C", "D"));
        state.addCorrectGroup("G2", List.of("E", "F", "G", "H"));

        CountDownLatch latch = new CountDownLatch(10);
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // 10 thread provano a aggiungere il 3° gruppo contemporaneamente
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                state.addCorrectGroup("G3", List.of("I", "J", "K", "L"));
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: deve aver vinto (3 gruppi)
        assertTrue(state.hasFinished());
        assertTrue(state.isWinner());
        assertEquals(3, state.getNumberOfCorrectGroupsFound());

        // Verifica: G3 compare UNA SOLA VOLTA (idempotenza)
        List<String> groups = state.getCorrectGroupsFound();
        long countG3 = groups.stream().filter(g -> g.equals("G3")).count();
        assertEquals(1, countG3, "G3 deve comparire una sola volta");

        // Verifica: score corretto (3 gruppi × 6 = 18)
        assertEquals(18, state.getScore());
    }

    @Test
    @DisplayName("Race: addMistake fino a 4 errori da più thread")
    void race_addMistake_multipleThreads() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("bob");
        int numThreads = 20;
        CountDownLatch latch = new CountDownLatch(numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // 20 thread provano tutti a fare addMistake
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                state.addMistake();
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: mistakes NON deve superare 4
        assertTrue(state.getMistakes() <= 4,
            "Anche con 20 thread, mistakes non deve superare 4 (protezione in addMistake)");

        // Verifica: deve aver finito (4 errori)
        assertTrue(state.hasFinished());
        assertFalse(state.isWinner());

        // Verifica: score coerente
        int expectedScore = state.getMistakes() * -4;
        assertEquals(expectedScore, state.getScore());
    }

    @Test
    @DisplayName("Race: forceTimeOut chiamato più volte concorrentemente")
    void race_forceTimeOut_idempotent() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("carol");

        // Carol ha già trovato 1 gruppo
        state.addCorrectGroup("G1", List.of("A", "B", "C", "D"));

        CountDownLatch latch = new CountDownLatch(50);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        // 50 thread chiamano forceTimeOut contemporaneamente
        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                state.forceTimeOut();
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: stato finale consistente
        assertTrue(state.hasFinished());
        assertFalse(state.isWinner(), "Timeout → non vincitore");
        assertEquals(1, state.getNumberOfCorrectGroupsFound());
        assertEquals(6, state.getScore(), "1 gruppo corretto = 6 punti");
    }

    @Test
    @DisplayName("Race: lettura wordsAlreadyFound durante addCorrectGroup")
    void race_readWordsAlreadyFoundDuringWrite() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("dave");
        int numOps = 500;
        CountDownLatch latch = new CountDownLatch(numOps * 2);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        AtomicInteger readErrors = new AtomicInteger(0);

        // Thread scrittori
        for (int i = 0; i < numOps; i++) {
            final int groupId = i % 3; // cicla su G1, G2, G3
            executor.submit(() -> {
                List<String> words = List.of("W" + groupId + "A", "W" + groupId + "B",
                                              "W" + groupId + "C", "W" + groupId + "D");
                state.addCorrectGroup("G" + groupId, words);
                latch.countDown();
            });
        }

        // Thread lettori
        for (int i = 0; i < numOps; i++) {
            executor.submit(() -> {
                try {
                    List<String> found = state.getWordsAlreadyFound();
                    if (found == null) {
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
            "getWordsAlreadyFound non deve mai lanciare eccezioni o restituire null");
    }

    @Test
    @DisplayName("Stress: vittoria anticipata mentre altri thread provano ad aggiungere errori")
    void stress_winWhileOthersAddMistakes() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("eva");

        // Eva ha già 2 gruppi corretti
        state.addCorrectGroup("G1", List.of("A", "B", "C", "D"));
        state.addCorrectGroup("G2", List.of("E", "F", "G", "H"));

        CountDownLatch latch = new CountDownLatch(20);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        // 10 thread provano ad aggiungere il 3° gruppo (vittoria)
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                state.addCorrectGroup("G3", List.of("I", "J", "K", "L"));
                latch.countDown();
            });
        }

        // 10 thread provano ad aggiungere errori
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                state.addMistake();
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: se ha vinto, mistakes non deve aver continuato a crescere
        assertTrue(state.hasFinished());

        if (state.isWinner()) {
            // Ha vinto: score = 18 + eventuali penalità da errori arrivati PRIMA della vittoria
            assertTrue(state.getScore() <= 18 && state.getScore() >= -2,
                "Score deve essere tra -2 (3 gruppi - 5 errori prima della vittoria) e 18 (vittoria pulita)");
            assertEquals(3, state.getNumberOfCorrectGroupsFound());
        } else {
            // Ha perso per errori: mistakes >= 4
            assertTrue(state.getMistakes() >= 4);
        }
    }

    @RepeatedTest(20)
    @DisplayName("Fuzzy test: operazioni casuali concorrenti")
    void fuzzyTest_randomOperations() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("fuzzy");
        int numOps = 100;
        CountDownLatch latch = new CountDownLatch(numOps);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < numOps; i++) {
            executor.submit(() -> {
                try {
                    int op = random.nextInt(5);
                    switch (op) {
                        case 0 -> state.addMistake();
                        case 1 -> state.addCorrectGroup("G" + random.nextInt(4),
                                List.of("W1", "W2", "W3", "W4"));
                        case 2 -> state.forceTimeOut();
                        case 3 -> state.hasFinished();
                        case 4 -> state.getScore();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: stato finale deve essere consistente
        assertTrue(state.getMistakes() >= 0 && state.getMistakes() <= 4);
        assertTrue(state.getNumberOfCorrectGroupsFound() >= 0 && state.getNumberOfCorrectGroupsFound() <= 3);

        // Se ha finito, deve avere una ragione valida
        if (state.hasFinished()) {
            boolean validReason = state.getMistakes() >= 4 ||
                                   state.getNumberOfCorrectGroupsFound() >= 3 ||
                                   !state.isWinner(); // timeout
            assertTrue(validReason, "hasFinished deve avere una ragione valida");
        }
    }

    @Test
    @DisplayName("Defensive copy: modificare lista restituita da getCorrectGroupsFound non deve alterare l'interno")
    void defensiveCopy_correctGroupsFound() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("test");
        state.addCorrectGroup("G1", List.of("A", "B", "C", "D"));

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                List<String> copy = state.getCorrectGroupsFound();
                // Tenta di modificare la copia
                assertThrows(UnsupportedOperationException.class, () -> copy.add("FAKE"),
                    "La lista deve essere immutabile (List.copyOf)");
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: l'originale non è stato modificato
        assertEquals(1, state.getCorrectGroupsFound().size());
        assertTrue(state.getCorrectGroupsFound().contains("G1"));
    }

    @Test
    @DisplayName("Verifica atomicità: addCorrectGroup fino a vittoria deve essere esattamente 3 gruppi")
    void atomicity_addCorrectGroupUntilWin() throws InterruptedException {
        PlayerGameState state = new PlayerGameState("frank");

        CountDownLatch latch = new CountDownLatch(100);
        ExecutorService executor = Executors.newFixedThreadPool(50);

        // 100 thread provano ad aggiungere gruppi (solo 3 diversi)
        for (int i = 0; i < 100; i++) {
            final int groupId = i % 3; // cicla G1, G2, G3
            executor.submit(() -> {
                state.addCorrectGroup("G" + groupId, List.of("W" + groupId));
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verifica: esattamente 3 gruppi (idempotenza + protezione hasFinished)
        assertEquals(3, state.getNumberOfCorrectGroupsFound());
        assertTrue(state.hasFinished());
        assertTrue(state.isWinner());
        assertEquals(18, state.getScore());
    }
}
