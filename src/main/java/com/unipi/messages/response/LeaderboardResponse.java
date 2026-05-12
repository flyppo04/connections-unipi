package com.unipi.messages.response;

import java.util.List;

/**
 * Risposta alla richiesta di classifica (requestLeaderboard).
 */
public class LeaderboardResponse extends BaseResponse {

    private List<LeaderboardEntry> leaderboard;

    /**
     * Posizione in classifica dell'utente richiesto.
     * Null se la richiesta non era per un singolo utente.
     */
    private Integer playerRank;

    public LeaderboardResponse() {}

    public LeaderboardResponse(String status, String message) {
        super(status, message);
    }

    public List<LeaderboardEntry> getLeaderboard() { return leaderboard; }
    public void setLeaderboard(List<LeaderboardEntry> leaderboard) { this.leaderboard = leaderboard; }

    public Integer getPlayerRank() { return playerRank; }
    public void setPlayerRank(Integer playerRank) { this.playerRank = playerRank; }

    @Override
    public String toString() {
        return "LeaderboardResponse{entries=" + (leaderboard != null ? leaderboard.size() : 0)
                + ", playerRank=" + playerRank + "}";
    }

    // -------------------------------------------------------------------------

    /** Singola voce della classifica. Classe interna statica per Gson. */
    public static class LeaderboardEntry {

        private int    rank;
        private String username;
        private int    totalScore;

        public LeaderboardEntry() {}

        public LeaderboardEntry(int rank, String username, int totalScore) {
            this.rank       = rank;
            this.username   = username;
            this.totalScore = totalScore;
        }

        public int    getRank()                { return rank; }
        public void   setRank(int rank)        { this.rank = rank; }

        public String getUsername()            { return username; }
        public void   setUsername(String u)    { this.username = u; }

        public int    getTotalScore()          { return totalScore; }
        public void   setTotalScore(int score) { this.totalScore = score; }

        @Override
        public String toString() {
            return "LeaderboardEntry{rank=" + rank + ", username='" + username
                    + "', totalScore=" + totalScore + "}";
        }
    }
}
