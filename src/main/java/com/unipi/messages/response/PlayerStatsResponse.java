package com.unipi.messages.response;

/**
 * Risposta alla richiesta di statistiche personali (requestPlayerStats).
 * Struttura ispirata alle statistiche del New York Times Connections.
 *
 * mistakeHistogram -> array di 6 elementi (corrisponde a puzzlesTracker in User):
 *   [0] = partite vinte con 0 errori (perfect)
 *   [1] = partite vinte con 1 errore
 *   [2] = partite vinte con 2 errori
 *   [3] = partite vinte con 3 errori
 *   [4] = partite perse per 4 errori
 *   [5] = partite non finite in tempo
 */
public class PlayerStatsResponse extends BaseResponse {

    private int   puzzlesCompleted;
    private float winRate;
    private float lossRate;
    private int   currentStreak;
    private int   maxStreak;
    private int   perfectPuzzles;
    private int[] mistakeHistogram;

    public PlayerStatsResponse() {}

    public PlayerStatsResponse(String status, String message) {
        super(status, message);
    }

    public int   getPuzzlesCompleted()          { return puzzlesCompleted; }
    public void  setPuzzlesCompleted(int v)     { this.puzzlesCompleted = v; }

    public float getWinRate()                   { return winRate; }
    public void  setWinRate(float v)            { this.winRate = v; }

    public float getLossRate()                  { return lossRate; }
    public void  setLossRate(float v)           { this.lossRate = v; }

    public int   getCurrentStreak()             { return currentStreak; }
    public void  setCurrentStreak(int v)        { this.currentStreak = v; }

    public int   getMaxStreak()                 { return maxStreak; }
    public void  setMaxStreak(int v)            { this.maxStreak = v; }

    public int   getPerfectPuzzles()            { return perfectPuzzles; }
    public void  setPerfectPuzzles(int v)       { this.perfectPuzzles = v; }

    public int[] getMistakeHistogram()          { return mistakeHistogram; }
    public void  setMistakeHistogram(int[] v)   { this.mistakeHistogram = v; }

    @Override
    public String toString() {
        return "PlayerStatsResponse{puzzlesCompleted=" + puzzlesCompleted
                + ", winRate=" + winRate + "%, lossRate=" + lossRate
                + "%, currentStreak=" + currentStreak + ", maxStreak=" + maxStreak
                + ", perfectPuzzles=" + perfectPuzzles + "}";
    }
}
