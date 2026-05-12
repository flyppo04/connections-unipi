package com.unipi.messages.response;

import java.util.List;
import java.util.Map;

/**
 * Risposta alla richiesta di informazioni su una partita (requestGameInfo).
 *
 * Se ongoing = true:
 *   remainingTimeSeconds, correctGroups, remainingWords, mistakes, score
 *
 * Se ongoing = false (partita conclusa):
 *   groupAssignments (soluzione completa), correctProposals, mistakes, score
 */
public class GameInfoResponse extends BaseResponse {

    private int gameId;

    /** true se la partita e ancora in corso per il giocatore richiedente. */
    private boolean ongoing;

    // --- Campi per partita in corso ---
    private long remainingTimeSeconds;
    private List<String> correctGroups;
    private List<String> remainingWords;

    // --- Campi comuni ---
    private int mistakes;
    private int score;

    // --- Campi per partita conclusa ---
    /** Mappa: nome del tema -> lista delle 4 parole. Solo se ongoing = false. */
    private Map<String, List<String>> groupAssignments;
    private int correctProposals;

    public GameInfoResponse() {}

    public GameInfoResponse(String status, String message) {
        super(status, message);
    }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public boolean isOngoing() { return ongoing; }
    public void setOngoing(boolean ongoing) { this.ongoing = ongoing; }

    public long getRemainingTimeSeconds() { return remainingTimeSeconds; }
    public void setRemainingTimeSeconds(long v) { this.remainingTimeSeconds = v; }

    public List<String> getCorrectGroups() { return correctGroups; }
    public void setCorrectGroups(List<String> v) { this.correctGroups = v; }

    public List<String> getRemainingWords() { return remainingWords; }
    public void setRemainingWords(List<String> v) { this.remainingWords = v; }

    public int getMistakes() { return mistakes; }
    public void setMistakes(int mistakes) { this.mistakes = mistakes; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public Map<String, List<String>> getGroupAssignments() { return groupAssignments; }
    public void setGroupAssignments(Map<String, List<String>> v) { this.groupAssignments = v; }

    public int getCorrectProposals() { return correctProposals; }
    public void setCorrectProposals(int v) { this.correctProposals = v; }

    @Override
    public String toString() {
        return "GameInfoResponse{gameId=" + gameId + ", ongoing=" + ongoing
                + ", mistakes=" + mistakes + ", score=" + score + "}";
    }
}
