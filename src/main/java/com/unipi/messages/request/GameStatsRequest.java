package com.unipi.messages.request;

/**
 * Richiesta di statistiche aggregate su una partita specifica o sulla partita corrente.
 * Convenzione: gameId = -1 indica la partita attualmente in corso.
 *
 * Se la partita e in corso, la risposta contiene:
 *   tempo rimanente, giocatori ancora in gioco, giocatori che hanno finito, vincitori.
 * Se la partita e conclusa, la risposta contiene:
 *   totale partecipanti, giocatori che hanno finito, vincitori, punteggio medio.
 *
 * Formato JSON atteso:
 * {
 *   "operation": "requestGameStats",
 *   "gameId": INT    (-1 per partita corrente)
 * }
 */
public class GameStatsRequest extends BaseRequest {

    /** -1 indica la partita attualmente in corso. */
    private int gameId;

    public GameStatsRequest() {}

    public GameStatsRequest(int gameId) {
        this.gameId = gameId;
    }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    /** Restituisce true se la richiesta riguarda la partita attualmente in corso. */
    public boolean isCurrentGame() {
        return gameId == -1;
    }

    @Override
    public String toString() {
        return "RequestGameStatsRequest{gameId=" + gameId
                + ", currentGame=" + isCurrentGame() + "}";
    }
}
