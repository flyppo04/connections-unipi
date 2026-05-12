package com.unipi.messages.request;

/**
 * Richiesta di logout.
 * Dopo il logout il client non riceve piu notifiche UDP
 * e non puo inviare proposte sulla partita corrente.
 *
 * Formato JSON atteso:
 * {
 *   "operation": "logout"
 * }
 */
public class LogoutRequest extends BaseRequest {

    public LogoutRequest() {}

    @Override
    public String toString() {
        return "LogoutRequest{}";
    }
}
