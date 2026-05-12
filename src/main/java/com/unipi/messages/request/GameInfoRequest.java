package com.unipi.messages.request;

/**
 * Richiesta di informazioni su una partita specifica o sulla partita corrente.
 * Convenzione: gameId = -1 indica la partita attualmente in corso.
 *
 * Se la partita e in corso, la risposta contiene:
 *   tempo rimanente, proposte corrette, parole rimanenti, errori, punteggio corrente.
 * Se la partita e conclusa, la risposta contiene:
 *   assegnazione completa delle 16 parole ai 4 gruppi, proposte corrette, errori, punteggio.
 *
 * Formato JSON atteso:
 * {
 *   "operation": "requestGameInfo",
 *   "gameId": INT    (-1 per partita corrente)
 * }
 */
public class GameInfoRequest extends BaseRequest {

    /** -1 indica la partita attualmente in corso. */
    private int gameId;

    public GameInfoRequest() {}

    public GameInfoRequest(int gameId) {
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
        return "RequestGameInfoRequest{gameId=" + gameId
                + ", currentGame=" + isCurrentGame() + "}";
    }
}
