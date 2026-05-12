package com.unipi.messages.request;

/**
 * Richiesta di informazioni sulla classifica globale.
 * I campi playerName e topPlayers sono entrambi opzionali:
 *   - nessun campo opzionale    -> tutta la classifica
 *   - solo playerName presente  -> posizione in classifica di quell'utente
 *   - solo topPlayers presente  -> top K utenti della classifica
 *
 * Formato JSON atteso:
 * {
 *   "operation": "requestLeaderboard",
 *   *"playerName": STRING,
 *   *"topPlayers": INT
 * }
 */
public class LeaderboardRequest extends BaseRequest {

    /** Opzionale: se presente, restituisce la posizione in classifica di questo utente. */
    private String playerName;

    /**
     * Opzionale: se presente, restituisce i top K utenti.
     * Uso di Integer (nullable) per distinguere "non fornito" da 0.
     */
    private Integer topPlayers;

    public LeaderboardRequest() {}

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Integer getTopPlayers() { return topPlayers; }
    public void setTopPlayers(Integer topPlayers) { this.topPlayers = topPlayers; }

    public boolean isPlayerRankRequest() {
        return playerName != null && !playerName.isBlank();
    }

    public boolean isTopKRequest() {
        return topPlayers != null && topPlayers > 0;
    }

    public boolean isFullLeaderboardRequest() {
        return !isPlayerRankRequest() && !isTopKRequest();
    }

    @Override
    public String toString() {
        return "RequestLeaderboardRequest{playerName='" + playerName
                + "', topPlayers=" + topPlayers + "}";
    }
}
