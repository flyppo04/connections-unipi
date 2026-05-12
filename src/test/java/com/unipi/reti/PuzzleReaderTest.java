package com.unipi.reti;

import com.unipi.data.PuzzleData;
import com.unipi.data.PuzzleReader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per PuzzleReader.
 *
 * Usa @TempDir di JUnit 5 per creare file JSON temporanei su disco
 * senza dipendere da file fissi nel progetto.
 *
 * Casi coperti:
 *   - caricamento corretto da JSON valido
 *   - getNextPuzzle scorre i puzzle in sequenza
 *   - wrap-around: dopo l'ultimo puzzle riparte dal primo (bug fix: deve incrementare currentIndex)
 *   - file inesistente: non lancia eccezioni al costruttore
 *   - getAllShuffledWords restituisce 16 parole (non nell'ordine originale necessariamente)
 */
@DisplayName("PuzzleReader — lettura e scorrimento puzzle da JSON")
class PuzzleReaderTest {

    // JSON con 3 puzzle minimali (ogni gruppo ha 4 parole, 4 gruppi per puzzle)
    private static final String VALID_JSON = """
        [
          {
            "gameId": 1,
            "groups": [
              { "theme": "T1", "words": ["A","B","C","D"] },
              { "theme": "T2", "words": ["E","F","G","H"] },
              { "theme": "T3", "words": ["I","J","K","L"] },
              { "theme": "T4", "words": ["M","N","O","P"] }
            ]
          },
          {
            "gameId": 2,
            "groups": [
              { "theme": "U1", "words": ["Q","R","S","T"] },
              { "theme": "U2", "words": ["U","V","W","X"] },
              { "theme": "U3", "words": ["Y","Z","A1","B1"] },
              { "theme": "U4", "words": ["C1","D1","E1","F1"] }
            ]
          },
          {
            "gameId": 3,
            "groups": [
              { "theme": "V1", "words": ["G1","H1","I1","J1"] },
              { "theme": "V2", "words": ["K1","L1","M1","N1"] },
              { "theme": "V3", "words": ["O1","P1","Q1","R1"] },
              { "theme": "V4", "words": ["S1","T1","U1","V1"] }
            ]
          }
        ]
        """;

    @TempDir
    Path tempDir;

    private Path puzzleFile;

    @BeforeEach
    void setUp() throws IOException {
        puzzleFile = tempDir.resolve("puzzles.json");
        Files.writeString(puzzleFile, VALID_JSON);
    }

    // ─── Caricamento ──────────────────────────────────────────────────

    @Test
    @DisplayName("Costruttore: carica correttamente i puzzle da un file JSON valido")
    void constructor_loadsFromValidJson() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        PuzzleData first = reader.getNextPuzzle();
        assertNotNull(first);
        assertEquals(1, first.getGameId());
    }

    @Test
    void constructor_nonExistentFile_throwsException() {
        assertThrows(RuntimeException.class, () -> new PuzzleReader("file-che-non-esiste.json"));
    }
    @Test
    @DisplayName("I gruppi del primo puzzle sono caricati correttamente")
    void firstPuzzle_hasCorrectGroups() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        PuzzleData puzzle = reader.getNextPuzzle();
        assertEquals(4, puzzle.getGroups().size());
        assertEquals("T1", puzzle.getGroups().get(0).getTheme());
        assertEquals(List.of("A","B","C","D"), puzzle.getGroups().get(0).getWords());
    }

    // ─── Scorrimento sequenziale ──────────────────────────────────────

    @Test
    @DisplayName("getNextPuzzle scorre i puzzle in sequenza (gameId crescente)")
    void getNextPuzzle_sequentialOrder() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        assertEquals(1, reader.getNextPuzzle().getGameId());
        assertEquals(2, reader.getNextPuzzle().getGameId());
        assertEquals(3, reader.getNextPuzzle().getGameId());
    }

    // ─── Wrap-around (bug fix) ────────────────────────────────────────

    @Test
    @DisplayName("Wrap-around: dopo l'ultimo puzzle restituisce il primo")
    void getNextPuzzle_wrapsAround_returnsFirst() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        reader.getNextPuzzle(); // puzzle 1
        reader.getNextPuzzle(); // puzzle 2
        reader.getNextPuzzle(); // puzzle 3 — esauriti

        // Al wrap-around deve restituire il puzzle 1
        PuzzleData wrapped = reader.getNextPuzzle();
        assertEquals(1, wrapped.getGameId(),
            "Dopo il wrap-around deve restituire il primo puzzle (gameId=1)");
    }

    @Test
    @DisplayName("Wrap-around: la chiamata SUCCESSIVA al wrap restituisce il secondo puzzle (bug fix currentIndex++)")
    void getNextPuzzle_wrapsAround_nextCallReturnsSecond() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        reader.getNextPuzzle(); // 1
        reader.getNextPuzzle(); // 2
        reader.getNextPuzzle(); // 3

        reader.getNextPuzzle(); // wrap → puzzle 1
        // Se il bug è presente (currentIndex non incrementato al wrap), questa chiamata
        // restituirebbe di nuovo il puzzle 1 invece del puzzle 2
        PuzzleData next = reader.getNextPuzzle();
        assertEquals(2, next.getGameId(),
            "BUG wrap-around: dopo il reset currentIndex non viene incrementato, " +
            "il puzzle 1 viene restituito all'infinito");
    }

    @Test
    @DisplayName("Wrap-around multipli: il ciclo si ripete correttamente")
    void getNextPuzzle_multipleWraps() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        // Primo ciclo
        assertEquals(1, reader.getNextPuzzle().getGameId());
        assertEquals(2, reader.getNextPuzzle().getGameId());
        assertEquals(3, reader.getNextPuzzle().getGameId());
        // Secondo ciclo
        assertEquals(1, reader.getNextPuzzle().getGameId());
        assertEquals(2, reader.getNextPuzzle().getGameId());
        assertEquals(3, reader.getNextPuzzle().getGameId());
        // Terzo ciclo (wrap parziale)
        assertEquals(1, reader.getNextPuzzle().getGameId());
    }

    // ─── getAllShuffledWords ───────────────────────────────────────────

    @Test
    @DisplayName("getAllShuffledWords restituisce esattamente 16 parole")
    void getAllShuffledWords_returns16Words() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        List<String> words = reader.getNextPuzzle().getAllShuffledWords();
        assertEquals(16, words.size());
    }

    @Test
    @DisplayName("getAllShuffledWords contiene tutte le parole originali")
    void getAllShuffledWords_containsAllWords() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        List<String> words = reader.getNextPuzzle().getAllShuffledWords();
        // Tutte le 16 parole del puzzle 1 devono essere presenti
        assertTrue(words.containsAll(List.of("A","B","C","D","E","F","G","H",
                                              "I","J","K","L","M","N","O","P")));
    }

    @Test
    @DisplayName("getAllShuffledWords non restituisce due volte la stessa parola")
    void getAllShuffledWords_noDuplicates() {
        PuzzleReader reader = new PuzzleReader(puzzleFile.toString());
        List<String> words = reader.getNextPuzzle().getAllShuffledWords();
        long distinctCount = words.stream().distinct().count();
        assertEquals(16, distinctCount, "Non devono esserci parole duplicate");
    }
}
