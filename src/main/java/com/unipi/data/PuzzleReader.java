package com.unipi.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PuzzleReader {
    private final List<PuzzleData> allPuzzles;
    private int currentIndex = 0;

    //prende il filepath del file con i puzzle
    public PuzzleReader(String filePath) {
        //creo reader con inputstreamreder che da byte trasforma in caratteri codificati 
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)) {
            
            Gson gson = new Gson();
            
            Type listType = new TypeToken<List<PuzzleData>>() {}.getType();
            
            //deserializzo i puzzle letti dal reader in json con fromJeson e li tipizzo iin PuzzleData 
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
        //indice in modulo per reinizare dal primo puzzle finita la lista 
        currentIndex = (currentIndex + 1) % allPuzzles.size();
        return next;
    }
}