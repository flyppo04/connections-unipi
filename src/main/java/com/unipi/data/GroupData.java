package com.unipi.data;

import java.util.List;

public class GroupData {
    private String theme;
    private List<String> words;

    //per test e uso manuale
    public GroupData(String theme, List<String> words) {
        this.theme = theme;
        this.words = words;
    }

    // Getter
    public String getTheme() {
        return theme;
    }

    public List<String> getWords() {
        return words;
    }
    
  /*   // Per il debug
    @Override
    public String toString() {
        return theme + ": " + words;
    } */
}