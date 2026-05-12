package com.unipi.models;

import java.util.ArrayList;
import java.util.List;

public class PlayerGameState {
    private String username;
    private int mistakes;           // Da 0 a 4
    private int score;              // Punteggio provvisorio o finale
    private List<String> correctGroupsFound; // Nomi dei gruppi indovinati ("Sport", "Colori")
    private boolean hasFinished;    // true se ha vinto, ha perso (4 errori), o il tempo è scaduto
    private List<String> wordsAlreadyFound; // parole già utilizate (serve a non fargliele utilizzare nuovamente)
    private boolean isWinner;
    
    public PlayerGameState(String username) {
        this.username = username;
        this.mistakes = 0;
        this.score = 0;
        this.correctGroupsFound = new ArrayList<>(); //tine traccia dei gruppi individuati
        this.wordsAlreadyFound = new ArrayList<>(); 
        this.hasFinished = false;
        this.isWinner = false;
    }


    // Costruttore storico per PlayerGameState 
    public PlayerGameState(String username, int mistakes, int score, 
                        List<String> correctGroupsFound, List<String> wordsAlreadyFound,
                        boolean hasFinished, boolean isWinner) {
        this.username = username;
        this.mistakes = mistakes;
        this.score = score;
        this.correctGroupsFound = new ArrayList<>(correctGroupsFound != null ? correctGroupsFound : new ArrayList<>());
        this.wordsAlreadyFound = new ArrayList<>(wordsAlreadyFound != null ? wordsAlreadyFound : new ArrayList<>());
        this.hasFinished = hasFinished;
        this.isWinner = isWinner;
    }

    

    
    public String getUsername() {
        return username;
    }

    public int getMistakes() {
        return mistakes;
    }
    
    public int getScore() {
        return score;
    }
    
    public List<String> getCorrectGroupsFound() {
        return correctGroupsFound;
    }
    
    public List<String> getWordsAlreadyFound() {
        return wordsAlreadyFound;
    }

    public boolean isWinner() {
        return isWinner;
    }

    public boolean hasFinished() {
        return hasFinished;
    }
    
    public void addCorrectGroup(String groupName, List<String> wordsOfGroup) {
        if (hasFinished) return;

        if (this.correctGroupsFound.contains(groupName)) {
            return;
        }

        this.correctGroupsFound.add(groupName);
        this.wordsAlreadyFound.addAll(wordsOfGroup);
        this.score += 6;

        if (this.correctGroupsFound.size() >= 3) {
            this.hasFinished = true;
            this.isWinner = true;
        }
    }

    public int getNumberOfCorrectGroupsFound() {
        return correctGroupsFound.size();
    }
    
    // Qui dentro metti i metodi specifici per l'utente, es:
    public void addMistake() {
        if (hasFinished) return;
        this.mistakes++;
        this.score -= 4; // Il PDF dice penalità di -4 per errore
        if (this.mistakes >= 4) {
            this.hasFinished = true; // Ha perso
        }
    }

    


    public void forceTimeOut() {
        // Se non aveva già finito per conto suo, finisce ora (e non è un vincitore)
        if (!this.hasFinished) {
            this.hasFinished = true;
            this.isWinner = false;
        }
    }

}