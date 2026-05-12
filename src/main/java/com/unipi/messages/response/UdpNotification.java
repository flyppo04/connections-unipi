package com.unipi.messages.response;

import java.util.List;
import java.util.Map;

/**
 * Oggetto inviato dal server via UDP come notifica asincrona.
 *
 * Tipi supportati:
 *   "GAME_OVER" -> partita terminata (tempo scaduto).
 *                  Contiene soluzione completa e statistiche finali.
 *
 *   "NEW_GAME"  -> nuova partita iniziata.
 *                  Contiene le 16 parole e la durata della nuova partita.
 */
public class UdpNotification {

    private String type;
    private int    gameId;

    // --- Campi per GAME_OVER ---
    /** Mappa: nome del tema -> lista delle 4 parole. Solo per "GAME_OVER". */
    private Map<String, List<String>> groupAssignments;
    private int   totalPlayers;
    private int   playersFinished;
    private int   playersWon;
    private float averageScore;

    // --- Campi per NEW_GAME ---
    /** Le 16 parole della nuova partita in ordine casuale. Solo per "NEW_GAME". */
    private List<String> words;
    private long durationSeconds;

    public UdpNotification() {}

    public String getType()                  { return type; }
    public void   setType(String type)       { this.type = type; }

    public int  getGameId()                  { return gameId; }
    public void setGameId(int gameId)        { this.gameId = gameId; }

    public Map<String, List<String>> getGroupAssignments()               { return groupAssignments; }
    public void setGroupAssignments(Map<String, List<String>> m)         { this.groupAssignments = m; }

    public int   getTotalPlayers()           { return totalPlayers; }
    public void  setTotalPlayers(int v)      { this.totalPlayers = v; }

    public int   getPlayersFinished()        { return playersFinished; }
    public void  setPlayersFinished(int v)   { this.playersFinished = v; }

    public int   getPlayersWon()             { return playersWon; }
    public void  setPlayersWon(int v)        { this.playersWon = v; }

    public float getAverageScore()           { return averageScore; }
    public void  setAverageScore(float v)    { this.averageScore = v; }

    public List<String> getWords()           { return words; }
    public void  setWords(List<String> w)    { this.words = w; }

    public long  getDurationSeconds()        { return durationSeconds; }
    public void  setDurationSeconds(long v)  { this.durationSeconds = v; }

    @Override
    public String toString() {
        return "UdpNotification{type='" + type + "', gameId=" + gameId + "}";
    }
}
