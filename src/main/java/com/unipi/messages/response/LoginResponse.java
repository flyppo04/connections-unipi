package com.unipi.messages.response;

import java.util.List;

/**
 * Risposta al login.
 * Contiene le informazioni sulla partita attualmente in corso.
 * Se il giocatore aveva gia partecipato, correctGroups, mistakes e score
 * riflettono il suo stato attuale nella partita.
 */
public class LoginResponse extends BaseResponse {

    private int gameId;
    private List<String> words;
    private List<String> correctGroups;
    private int mistakes;
    private int score;
    private long remainingTimeSeconds;

    public LoginResponse() {}

    public LoginResponse(String status, String message) {
        super(status, message);
    }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public List<String> getWords() { return words; }
    public void setWords(List<String> words) { this.words = words; }

    public List<String> getCorrectGroups() { return correctGroups; }
    public void setCorrectGroups(List<String> correctGroups) { this.correctGroups = correctGroups; }

    public int getMistakes() { return mistakes; }
    public void setMistakes(int mistakes) { this.mistakes = mistakes; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public long getRemainingTimeSeconds() { return remainingTimeSeconds; }
    public void setRemainingTimeSeconds(long remainingTimeSeconds) {
        this.remainingTimeSeconds = remainingTimeSeconds;
    }

    @Override
    public String toString() {
        return "LoginResponse{gameId=" + gameId + ", mistakes=" + mistakes
                + ", score=" + score + ", remainingTime=" + remainingTimeSeconds + "s}";
    }
}
