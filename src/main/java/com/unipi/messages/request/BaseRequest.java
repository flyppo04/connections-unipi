package com.unipi.messages.request;

/**
 * Classe base per tutte le richieste dal client al server.
 * Il campo "operation" viene popolato automaticamente da Gson
 * durante la deserializzazione del JSON ricevuto sul canale TCP.
 */
public abstract class BaseRequest {

    private String operation;

    public String getOperation() {
        return operation;
    }

    @Override
    public String toString() {
        return "BaseRequest{operation='" + operation + "'}";
    }
}
