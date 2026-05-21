package com.unipi.reti;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.unipi.models.PlayerGameState;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerGameStateTest1 {

    private PlayerGameState state;
    private final List<String> dummyGroup = Arrays.asList("A", "B", "C", "D");

    @BeforeEach
    void setUp() {
        state = new PlayerGameState("testUser");
    }

    @Test
    void testInitialState() {
        assertEquals(0, state.getScore());
        assertEquals(0, state.getMistakes());
        assertFalse(state.hasFinished());
        assertFalse(state.isWinner());
    }

    @Test
    void testCorrectProposalIncreasesScore() {
        state.addCorrectGroup("Colori", dummyGroup);
        assertEquals(6, state.getScore());
        assertEquals(1, state.getNumberOfCorrectGroupsFound());
        assertFalse(state.hasFinished());
    }

    @Test
    void testWrongProposalDecreasesScore() {
        state.addMistake();
        assertEquals(-4, state.getScore());
        assertEquals(1, state.getMistakes());
        assertFalse(state.hasFinished());
    }

    @Test
    void testWinningCondition() {
        // La vittoria scatta al TERZO gruppo indovinato
        state.addCorrectGroup("Gruppo1", dummyGroup);
        state.addCorrectGroup("Gruppo2", Arrays.asList("E", "F", "G", "H"));
        state.addCorrectGroup("Gruppo3", Arrays.asList("I", "J", "K", "L"));

        assertTrue(state.hasFinished());
        assertTrue(state.isWinner());
        assertEquals(18, state.getScore()); // 6 * 3
    }

    @Test
    void testLosingCondition() {
        // La sconfitta scatta al QUARTO errore
        state.addMistake();
        state.addMistake();
        state.addMistake();
        state.addMistake();

        assertTrue(state.hasFinished());
        assertFalse(state.isWinner());
        assertEquals(-16, state.getScore()); // -4 * 4
    }

    @Test
    void testDuplicateGroupIgnored() {
        state.addCorrectGroup("Gruppo1", dummyGroup);
        state.addCorrectGroup("Gruppo1", dummyGroup); // Tentativo duplicato

        assertEquals(6, state.getScore());
        assertEquals(1, state.getNumberOfCorrectGroupsFound());
    }
}