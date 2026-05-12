package com.unipi.messages.request;

/**
 * Richiesta delle statistiche personali dell'utente attualmente loggato.
 * Non richiede campi aggiuntivi: il server identifica l'utente
 * dalla sessione TCP attiva.
 *
 * Formato JSON atteso:
 * {
 *   "operation": "requestPlayerStats"
 * }
 */
public class PlayerStatsRequest extends BaseRequest {

    public PlayerStatsRequest() {}

    @Override
    public String toString() {
        return "RequestPlayerStatsRequest{}";
    }
}
