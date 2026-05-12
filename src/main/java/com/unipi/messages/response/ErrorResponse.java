package com.unipi.messages.response;

/**
 * Risposta di errore generica restituita dal server.
 *
 * Codici di errore suggeriti:
 *   400 - Richiesta malformata (es. proposta con numero errato di parole)
 *   401 - Password errata
 *   403 - Utente non loggato (operazione richiede login)
 *   404 - Risorsa non trovata (es. gameId inesistente, utente non trovato)
 *   409 - Conflitto (es. nome utente gia registrato)
 *   410 - Partita gia terminata per questo giocatore
 */
public class ErrorResponse extends BaseResponse {

    private int errorCode;

    public ErrorResponse() {
        super("ERROR", null);
    }

    public ErrorResponse(int errorCode, String message) {
        super("ERROR", message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() { return errorCode; }
    public void setErrorCode(int errorCode) { this.errorCode = errorCode; }

    @Override
    public String toString() {
        return "ErrorResponse{errorCode=" + errorCode
                + ", message='" + getMessage() + "'}";
    }
}
