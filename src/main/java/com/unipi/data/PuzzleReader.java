package com.unipi.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class PuzzleReader {
    private final List<PuzzleData> allPuzzles;
    private int currentIndex = 0;

    public PuzzleReader(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<PuzzleData>>() {}.getType();
            List<PuzzleData> loaded = gson.fromJson(reader, listType);

            if (loaded == null || loaded.isEmpty()) {
                throw new IllegalArgumentException("File puzzle vuoto o malformato: " + filePath);
            }

            this.allPuzzles = loaded;
            System.out.println("Caricati " + allPuzzles.size() + " puzzle.");
        } catch (IOException e) {
            throw new RuntimeException("Errore apertura file puzzle: " + filePath, e);
        }
    }

    public synchronized PuzzleData getNextPuzzle() {
        PuzzleData next = allPuzzles.get(currentIndex);
        currentIndex = (currentIndex + 1) % allPuzzles.size();
        return next;
    }
}