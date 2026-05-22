package com.unipi.data;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class PuzzleData {
    private int gameId;
    private List<GroupData> groups;

    // Costruttore per test e uso manuale
    public PuzzleData(int gameId, List<GroupData> groups) {
        this.gameId = gameId;
        this.groups = groups;
    }


    // Getter
    public int getGameId() {
        return gameId;
    }

    public List<GroupData> getGroups() {
        return groups;
    }

    //Metodo di utilità: ti restituisce tutte le 16 parole mischiate.
    public List<String> getAllShuffledWords() {
        List<String> allWords = new ArrayList<>();
        if (groups != null) {
            for (GroupData group : groups) {
                allWords.addAll(group.getWords());
            }
        }
        Collections.shuffle(allWords); // Le mescola casualmente!
        return allWords;
    }
}