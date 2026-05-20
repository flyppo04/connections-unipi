package com.unipi.models; 

public class User {
    
    private String username;
    private String password;

    //private int puzzlesCompleted; // per ottenerlo suo l'array puzzlesTracker
    //private float winRate; // calcolo dinamicamente su array puzzlesTracker
    //private float lossRate; // stesso di sopra 
    private int currentStreak;
    private int maxStreak;
    private int totalScore;
    //private int perfectPuzzles;
    /* decido di usare un array per tenermi memorizzato delle partite:
    vinte con punteggio da 0 a 3 errori (dove n. errori saranno indici)
    perse per eccesso di errori (inidce 4)
    perse per termine tempo (indice 5) */

    private int[] puzzlesTracker;

    public User(String username, String password){
        this.username = username;
        this.password = password;
        this.currentStreak = 0;
        this.maxStreak = 0;
        this.puzzlesTracker = new int[6];
    }
    
    public synchronized String getUsername() {
        return username;
    } 

    public synchronized String getPassword() {
        return password;
    }

    // Setter per username (serve per updateCredentials)
    public synchronized void setUsername(String username) {
        this.username = username;
    }

    // Setter per password (serve per updateCredentials)
    public synchronized void setPassword(String password) {
        this.password = password;
    }

    public synchronized int getCurrentStreak() {
        return currentStreak;
    }

    public synchronized void updateCurentStreak() {
        this.currentStreak += 1;

        if (currentStreak > this.getMaxStreak()){
            this.setMaxStreak(currentStreak);
        }

    }

    public synchronized void resetCurrentStreak() {
        this.currentStreak = 0; 
    }

    public synchronized int getMaxStreak() {
        return maxStreak;
    }

    public synchronized void setMaxStreak(int newMaxStreak) {
        this.maxStreak = newMaxStreak;
    }

    public synchronized int getPuzzlesCompleted(){

        int totPuzzles = 0;

        for(int i = 0; i < puzzlesTracker.length; i++){
            totPuzzles += puzzlesTracker[i];
        }

        return totPuzzles;

    }

    public synchronized float getWinRate() {

        int totPuzzles = getPuzzlesCompleted();

        if (totPuzzles == 0) {
            return 0.0f;
        }

        int puzzleWin = puzzlesTracker[0] + puzzlesTracker[1] + puzzlesTracker[2] + puzzlesTracker[3];

        return ((float) puzzleWin / totPuzzles) * 100;
        
    }

    public synchronized float getLossRate() {

        int totPuzzles = getPuzzlesCompleted();


        if (totPuzzles == 0) {
            return 0.0f;
        }

        int puzzleLoss = puzzlesTracker[4] + puzzlesTracker[5];

        return ((float) puzzleLoss / totPuzzles) * 100;
        
    }

    public synchronized int getPerfectWins() {
        return puzzlesTracker[0];
    }


    public synchronized int[] getPuzzlesTracker() {
        return puzzlesTracker.clone();
    }

    public synchronized void recordTimeout() { 
        puzzlesTracker[5]++; 
    }
    
    public synchronized void recordLossByMistakes() { 
        puzzlesTracker[4]++; 
    }    
    

    public synchronized void recordWinWithMistakes(int mistakes) { 
        puzzlesTracker[mistakes]++; 

    }

    // Getter per il punteggio totale (serve per la classifica)
    // Si calcola sommando tutti i punteggi delle partite giocate.
    // Per ora puoi aggiungerlo come campo separato da aggiornare 
    // ogni volta che una partita finisce:
    
    public synchronized int getTotalScore() {
        return totalScore;
    }

    public synchronized void addScore(int points) {
        this.totalScore += points;
    }

}