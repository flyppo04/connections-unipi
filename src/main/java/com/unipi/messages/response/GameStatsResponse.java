package com.unipi.messages.response;

/**
 * Risposta alla richiesta di statistiche aggregate di una partita (requestGameStats).
 *
 * Se ongoing = true:
 *   remainingTimeSeconds, playersStillPlaying, playersFinished, playersWon
 *
 * Se ongoing = false:
 *   totalPlayers, playersFinished, playersWon, averageScore
 */
public class GameStatsResponse extends BaseResponse {

    private int gameId;

    /** true se la partita e ancora globalmente in corso (tempo non scaduto). */
    private boolean ongoing;

    // --- Campi per partita in corso ---
    private long remainingTimeSeconds;
    private int playersStillPlaying;

    // --- Campi comuni ---
    private int playersFinished;
    private int playersWon;

    // --- Campi per partita conclusa ---
    private int totalPlayers;
    private float averageScore;

    public GameStatsResponse() {}

    public GameStatsResponse(String status, String message) {
        super(status, message);
    }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public boolean isOngoing() { return ongoing; }
    public void setOngoing(boolean ongoing) { this.ongoing = ongoing; }

    public long getRemainingTimeSeconds() { return remainingTimeSeconds; }
    public void setRemainingTimeSeconds(long v) { this.remainingTimeSeconds = v; }

    public int getPlayersStillPlaying() { return playersStillPlaying; }
    public void setPlayersStillPlaying(int v) { this.playersStillPlaying = v; }

    public int getPlayersFinished() { return playersFinished; }
    public void setPlayersFinished(int v) { this.playersFinished = v; }

    public int getPlayersWon() { return playersWon; }
    public void setPlayersWon(int v) { this.playersWon = v; }

    public int getTotalPlayers() { return totalPlayers; }
    public void setTotalPlayers(int v) { this.totalPlayers = v; }

    public float getAverageScore() { return averageScore; }
    public void setAverageScore(float v) { this.averageScore = v; }

    @Override
    public String toString() {
        return "GameStatsResponse{gameId=" + gameId + ", ongoing=" + ongoing
                + ", playersFinished=" + playersFinished + ", playersWon=" + playersWon + "}";
    }
}
