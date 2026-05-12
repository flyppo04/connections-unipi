package com.unipi.models;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rappresenta una singola istanza di partita.
 * Questa classe è condivisa tra più thread (giocatori) e deve essere thread-safe.
 */
public class Game {

    // Campi 'final': i riferimenti non possono essere modificati dopo la creazione.
    // Questo aiuta a prevenire modifiche accidentali e garantisce visibilità immediata ai thread.
    private final int gameId;

    // Le 16 parole mostrate nella partita
    private final List<String> words;

    // Mappa nomeGruppo -> lista delle 4 parole corrette
    private final Map<String, List<String>> connections;

    // Istante assoluto in cui la partita termina
    private final long endTimeInMillis;

    // Stato dei giocatori relativo A QUESTA specifica partita
    // chiave = username, valore = stato del giocatore nella partita
    // Viene usata una ConcurrentHashMap per permettere modifiche da più thread contemporaneamente
    private final Map<String, PlayerGameState> playerStates;

    public Game(int gameId, List<String> words, Map<String, List<String>> connections, long durationMillis) {
        this.gameId = gameId;

        // Rendiamo le liste di parole e le connessioni immutabili (solo lettura)
        // Così nessun thread può aggiungere o rimuovere parole dalla partita globale in corso
        this.words = Collections.unmodifiableList(new ArrayList<>(words));
        this.connections = Collections.unmodifiableMap(new HashMap<>(connections));

        this.endTimeInMillis = System.currentTimeMillis() + durationMillis;
        this.playerStates = new ConcurrentHashMap<>();
    }

    //costruttore per deserializzazione 
    public Game(int gameId,List<String> words,Map<String, List<String>> connections,Map<String, PlayerGameState> playerStates) {
        this.gameId = gameId;
        this.words = Collections.unmodifiableList(new ArrayList<>(words));
        this.connections = Collections.unmodifiableMap(new HashMap<>(connections));
        this.endTimeInMillis = 0L; // partita storica: sempre terminata
        this.playerStates = new ConcurrentHashMap<>(playerStates);
    }

    public int getGameId() {
        return gameId;
    }

    public List<String> getWords() {
        return words;
    }

    public Map<String, List<String>> getConnections() {
        return connections;
    }

    public long getEndTimeInMillis() {
        return endTimeInMillis;
    }

    public Map<String, PlayerGameState> getPlayerStates() {
        return playerStates;
    }

    // Controlla se il tempo è scaduto
    public boolean isExpired() {
        return System.currentTimeMillis() >= endTimeInMillis;
    }

    // Restituisce i secondi rimanenti
    public long getRemainingTimeInSeconds() {
        long remainingMillis = endTimeInMillis - System.currentTimeMillis();
        return remainingMillis <= 0 ? 0 : remainingMillis / 1000;
    }

    // Aggiunge un giocatore alla partita solo se non era già presente
    public void addPlayer(String username) {
        // Operazione atomica: evita race conditions se due thread provano ad aggiungere
        // lo stesso giocatore contemporaneamente.
        playerStates.putIfAbsent(username, new PlayerGameState(username));
    }

    // Restituisce lo stato del singolo giocatore nella partita
    public PlayerGameState getPlayerState(String username) {
        return playerStates.get(username);
    }

    // Numero totale di giocatori che hanno preso parte a questa partita
    public int getTotalPlayers() {
        return playerStates.size();
    }

    // Numero di giocatori che hanno già concluso la partita
    public int getFinishedPlayersCount() {
        int count = 0;
        // Grazie a ConcurrentHashMap, iterare sui valori è sicuro anche se altri thread 
        // stanno inserendo nuovi giocatori o modificando lo stato
        for (PlayerGameState state : playerStates.values()) {
            if (state.hasFinished()) {
                count++;
            }
        }
        return count;
    }

    // Numero di giocatori che hanno vinto
    public int getWinnerPlayersCount() {
        int count = 0;
        for (PlayerGameState state : playerStates.values()) {
            if (state.isWinner()) {
                count++;
            }
        }
        return count;
    }

    // Lista degli username dei vincitori
    public List<String> getWinnerPlayers() {
        List<String> winners = new ArrayList<>();
        for (PlayerGameState state : playerStates.values()) {
            if (state.isWinner()) {
                winners.add(state.getUsername());
            }
        }
        return winners;
    }

    // Somma dei punteggi di tutti i giocatori della partita
    public int getTotalScore() {
        int total = 0;
        for (PlayerGameState state : playerStates.values()) {
            total += state.getScore();
        }
        return total;
    }

    // Media punteggi della partita
    public float getAverageScore() {
        if (playerStates.isEmpty()) {
            return 0f;
        }
        return (float) getTotalScore() / playerStates.size();
    }
}