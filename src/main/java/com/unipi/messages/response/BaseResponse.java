package com.unipi.messages.response;

/**
 * Classe base per tutte le risposte dal server al client.
 * Ogni risposta ha sempre uno status ("OK" o "ERROR") e
 * un messaggio opzionale human-readable.
 */
public abstract class BaseResponse {

    /** "OK" se l'operazione e andata a buon fine, "ERROR" altrimenti. */
    private String status;

    /** Messaggio opzionale, utile in caso di errore per descrivere il problema. */
    private String message;

    public BaseResponse() {}

    public BaseResponse(String status, String message) {
        this.status  = status;
        this.message = message;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /** Restituisce true se l'operazione e andata a buon fine. */
    public boolean isOk() {
        return "OK".equals(status);
    }

    @Override
    public String toString() {
        return "BaseResponse{status='" + status + "', message='" + message + "'}";
    }
}
