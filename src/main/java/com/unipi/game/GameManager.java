package com.unipi.game;

import com.unipi.data.GroupData;
import com.unipi.data.PuzzleData;
import com.unipi.data.PuzzleReader;
import com.unipi.models.Game;
import com.unipi.models.PlayerGameState;
import com.unipi.models.User;
import com.unipi.server.PersistenceManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/* Gestisce il ciclo di vita delle partite: avvio, valutazione proposte,
fine partita per timer o per vittoria/sconfitta del giocatore */
public class GameManager {

    /* Interfaccia callback 
    Contratto del callback di fine partita.
    Il server registra qui la propria logica (UDP + persistenza).
    GameManager non sa nulla di socket, UDP o rete, ma si occuap solo di avvisarlo */
    @FunctionalInterface
    public interface GameEndCallback {
        void onGameEnd(Game endedGame);
    }

    @FunctionalInterface
    public interface NewGameCallback {
        void onNewGame(Game startedGame);
    }

    // Enum di risultato per submitProposal
    public enum ProposalResult {
        CORRECT,           // le 4 parole formano un gruppo corretto
        WRONG,             // le 4 parole non formano nessun gruppo valido
        ALREADY_FOUND,     // questo gruppo era già stato indovinato dal giocatore
        MALFORMED,         // parole non valide, duplicate, o numero errato
        GAME_ALREADY_OVER, // il giocatore ha già vinto o perso
        NO_ACTIVE_GAME     // nessuna partita attiva al momento
    }

    // Campi
    private final PuzzleReader puzzleReader;
    private final UserManager  userManager;
    private final PersistenceManager persistenceManager;
    private final long         gameDurationSeconds;

    // volatile: tutti i thread vedono sempre il riferimento aggiornatosenza caching locale nella CPU
    private volatile Game currentGame;

    /* Contatore thread-safe per gli ID di partita.
    getAndIncrement() è atomico: nessuna race condition possibile */
    private final AtomicInteger nextGameId;

    //Thread pool per il timer della partita
    private final ScheduledExecutorService scheduler;

    /* Riferimento al task del timer corrente.
    Serve per cancellarlo in modo pulito allo shutdown del server */
    private ScheduledFuture<?> currentGameTimer;

    /* Callback singolo registrato dal ServerMain.
    null finché il server non lo registra con setGameEndCallback() */
    private GameEndCallback onGameEnd;
    private NewGameCallback onNewGame;

    // Costruttore
    public GameManager(PuzzleReader puzzleReader,
                       UserManager userManager,
                       long gameDurationSeconds,
                       PersistenceManager persistenceManager) {

        this.puzzleReader        = puzzleReader;
        this.userManager         = userManager;
        this.gameDurationSeconds = gameDurationSeconds;
        //vado a prendere il gameid che ho salvato nel file gameId per continuità
        this.nextGameId          = new AtomicInteger(persistenceManager.loadGameId()); 
        //genero il pool di un solo thread che faccia da timer 
        this.scheduler           = Executors.newScheduledThreadPool(1);
        this.persistenceManager  = persistenceManager;
        this.onGameEnd           = null;
        this.onNewGame           = null;

    }

    // Registrazione callback (chiamata dal ServerMain all'avvio)
    /* Registra il callback da invocare quando una partita termina o inizia
    Tipicamente chiamato una sola volta da ServerMain */

    public void setGameEndCallback(GameEndCallback callback) {
        this.onGameEnd = callback;
    }

    public void setNewGameCallback(NewGameCallback callback) {
        this.onNewGame = callback;
    }

    // Avvio partita

    /* Avvia una nuova partita con il prossimo puzzle disponibile
    synchronized: garantisce che solo un thread alla volta possa
    cambiare currentGame ed avviare un nuovo timer */
    
    public synchronized void startNewGame() {
        // PuzzleReader gestisce internamente l'indice e il wrap-around
        PuzzleData puzzle = puzzleReader.getNextPuzzle();
        int gameId = nextGameId.getAndIncrement();

        //aggiorno il game id che ho nel file gameId 
        //so che è una I/O e sono dentro syncronzed ma è veloce 
        persistenceManager.saveGameId(gameId); 

        // Estrai le 16 parole già mescolate dal puzzle
        List<String> words = puzzle.getAllShuffledWords();

        // Costruisci la mappa tema → [parole] che il costruttore di Game si aspetta
        Map<String, List<String>> connections = new HashMap<>();
        for (GroupData group : puzzle.getGroups()) {
            connections.put(group.getTheme(), group.getWords());
        }

        // Il costruttore di Game vuole i millisecondi, non i secondi
        Game newGame = new Game(gameId, words, connections, gameDurationSeconds * 1000);

        currentGame = newGame;
        // Schedula la fine partita — non bloccante, ritorna subito
        currentGameTimer = scheduler.schedule(this::endCurrentGame,gameDurationSeconds,TimeUnit.SECONDS);

        System.out.println("[GameManager] Partita #" + gameId + " avviata. " + "Durata: " + gameDurationSeconds + "s.");
    }

    // Valutazione proposta
    /* Valuta una proposta di raggruppamento inviata da un giocatore
    Aggiorna PlayerGameState 
    @param username username del giocatore
    @param words    lista di 4 parole proposte
    @return ProposalResult che descrive l'esito */

    public ProposalResult submitProposal(String username, List<String> words) {
        // Copia locale — evita race condition se currentGame cambia
        // mentre questo metodo è in esecuzione
        //esmpio: se mentre elaboro poposta game finisce, currentGame cambia ma game no
        //        non c'è rischio di salvare dati falsi su nuova partita
        Game game = currentGame;

        if (game == null || game.isExpired()) {
            return ProposalResult.NO_ACTIVE_GAME;
        }

        // Aggiunge il giocatore se è la prima volta che interagisce con questa partita
        game.addPlayer(username);
        PlayerGameState state = game.getPlayerState(username);

        if (state == null) {
            return ProposalResult.NO_ACTIVE_GAME;
        }
        
        // Lock per singolo giocatore: rende atomica la sequenza
        // check hasFinished -> valida proposta -> aggiorna stato/statistiche,
        // evitando race con il timer di fine partita sullo stesso PlayerGameState
        // esmpio: se sta elaborando risposta alla peggio ritarda di pochissimo chiusura
        synchronized (state){
            // Il giocatore ha già finito (vinto o perso per errori)
            if (state.hasFinished()) {
                return ProposalResult.GAME_ALREADY_OVER;
            }
    
            // Validazione strutturale della proposta
            ProposalResult validationResult = validateProposal(words, state, game);
            if (validationResult != null) {
                return validationResult;
            }
    
            // Cerca il gruppo corrispondente tra quelli non ancora trovati
            String matchedTheme = findMatchingGroup(words, game, state);
    
            if (matchedTheme != null) {
                // Proposta corretta: registra il gruppo trovato nello stato del giocatore
                List<String> groupWords = game.getConnections().get(matchedTheme);
                state.addCorrectGroup(matchedTheme, groupWords);

                //sezione necesaria per l'aggiunta del 4° gruppo in automatico quando ne vengono accettati 3 
                if (state.isWinner() && state.getCorrectGroupsFound().size() == 3) {
                    //per ogni tema con relativo set di parole 
                    for (Map.Entry<String, List<String>> entry : game.getConnections().entrySet()) {
                        String theme = entry.getKey();
                        //se il tema non è tra quelli trovati lo inserisce 
                        if (!state.getCorrectGroupsFound().contains(theme)) {
                            state.addCorrectGroup(theme, entry.getValue());
                            break; 
                        }
                    }
                }
    
                // Se hasFinished() è diventato true  dentro addCorrectGroup() perchè ha "vinto" o ha "perso"
                // aggiorna le statistiche dell'utente
                if (state.hasFinished()) {
                    updateUserStatsOnFinish(username, state);
                }

                return ProposalResult.CORRECT;
    
            } else {
                // Proposta sbagliata
                state.addMistake();
    
                // Se addMistake() ha portato hasFinished a true (4 errori)
                if (state.hasFinished()) {
                    updateUserStatsOnFinish(username, state);
                }
                return ProposalResult.WRONG;
            }  
        }
    }

    // Fine partita
    /* Termina la partita corrente per scadenza del timer
    Chiamato automaticamente dallo scheduler dopo gameDurationSeconds
    synchronized: non deve sovrapporsi con startNewGame() */
    private synchronized void endCurrentGame() {

        Game game = currentGame;
        if (game == null) return;

        // Forza la fine per tutti i giocatori che non hanno ancora terminato
        for (Map.Entry<String, PlayerGameState> entry : game.getPlayerStates().entrySet()) {

            String username = entry.getKey();
            PlayerGameState state = entry.getValue();

            // Lock sul PlayerGameState del singolo utente: evita che timeout e
            // submitProposal chiudano/modifichino contemporaneamente lo stesso player.
            synchronized (state){
                if (!state.hasFinished()) {
                    state.forceTimeOut();  
                    updateUserStatsOnTimeout(username, state);
                }
            }
        }

        System.out.println("[GameManager] Partita #" + game.getGameId() + " terminata.");

        // Notifica il server (UdpSender + persistenza) tramite il callback
        if (onGameEnd != null) {
            onGameEnd.onGameEnd(game);
        }

        // Avvia immediatamente la partita successiva
        startNewGame();
        
        if (onNewGame != null) {
            onNewGame.onNewGame(currentGame); // game è già la nuova partita
        }
    }

    // Aggiornamento statistiche utente
    // Aggiorna User quando il giocatore finisce la partita per vittoria o per 4 errori (non per timeout)

    private void updateUserStatsOnFinish(String username, PlayerGameState state) {
        User user = userManager.getUserByUsername(username);
        if (user == null) return;

        user.addScore(state.getScore()); 

        if (state.isWinner()) {
            // memorizzo vitoria con relativo numero di errori di questa partita
            user.recordWinWithMistakes(state.getMistakes());
            //aggirono streak vittorie
            user.updateCurentStreak();   
        } else {
            // memorizzo sconfitta dovuta a 4 errori
            user.recordLossByMistakes();
            // cancello streak di vittorie
            user.resetCurrentStreak();
        }
    }

    // Aggiorna User per i giocatori che non hanno finito quando scade il tempo 
    private void updateUserStatsOnTimeout(String username, PlayerGameState state) {
        User user = userManager.getUserByUsername(username);
        if (user == null) return;

        user.addScore(state.getScore());

        // memorizzo sconfitta per timeout
        user.recordTimeout();
        user.resetCurrentStreak();
    }


    // Metodi privati di validazione
    /* Controlla che la proposta sia strutturalmente valida
    Restituisce null se è valida, altrimenti il ProposalResult di errore
    Nota: le proposte malformate NON incrementano gli errori né il punteggio     */
    private ProposalResult validateProposal(List<String> words, PlayerGameState state, Game game) {
        // Numero di parole errato
        if (words == null || words.size() != 4) {
            return ProposalResult.MALFORMED;
        }

        // Parole duplicate nella stessa proposta
        Set<String> unique = new HashSet<>(words);
        if (unique.size() != 4) {
            return ProposalResult.MALFORMED;
        }

        // Recupera le 16 parole valide per questa partita
        List<String> validBoardWords = game.getWords();
        
        // Verifica che OGNI parola proposta dall'utente esista sulla board
        for (String proposedWord : words) {
            boolean foundOnBoard = false;
            
            for (String boardWord : validBoardWords) {
                // Usa equalsIgnoreCase per ignorare differenze di maiuscole/minuscole
                if (boardWord.equalsIgnoreCase(proposedWord)) {
                    foundOnBoard = true;
                    break;
                }
            }
            
            // Se anche solo una parola non è sulla board, la proposta è malformata
            if (!foundOnBoard) {
                return ProposalResult.MALFORMED;
            }
        }

        // Parole già correttamente raggruppate da questo giocatore
        List<String> alreadyFound = state.getWordsAlreadyFound();
        for (String word : words) {
            if (alreadyFound.contains(word.toUpperCase())) {
                return ProposalResult.ALREADY_FOUND;
            }
        }

        return null; // proposta strutturalmente valida
    }

    /* Cerca tra i gruppi della partita quale corrisponde esattamente
    alle parole proposte (confronto non ordinato, case-insensitive)
    Considera solo i gruppi NON ancora trovati dal giocatore
    Restituisce il tema (String) del gruppo trovato, o null se nessuno corrisponde */
    private String findMatchingGroup(List<String> words, Game game, PlayerGameState state) {
        // Normalizza in maiuscolo per confronto case-insensitive
        Set<String> proposed = new HashSet<>();
        for (String w : words) {
            proposed.add(w.toUpperCase());
        }

        // game.getConnections() restituisce Map<String, List<String>> tema -> parole
        for (Map.Entry<String, List<String>> entry : game.getConnections().entrySet()) {
            String theme = entry.getKey();

            // Salta i gruppi già trovati da questo giocatore
            if (state.getCorrectGroupsFound().contains(theme)) continue;

            Set<String> groupWords = new HashSet<>();
            for (String gw : entry.getValue()) {
                groupWords.add(gw.toUpperCase());
            }

            if (groupWords.equals(proposed)) {
                return theme;
            }
        }
        return null;
    }

    // Getter pubblici

    //Restituisce la partita attualmente in corso
    public Game getCurrentGame() {
        return currentGame;
    }

    //Restituisce una partita passata per ID 
    public Game getGame(int gameId) {

        return persistenceManager.findCompletedGameById(gameId);

    }

    //True se c'è una partita attiva e non scaduta
    public boolean hasActiveGame() {
        return currentGame != null && !currentGame.isExpired();
    }

    /**
    Shutdown pulito del server.
    Cancella il timer corrente e ferma il thread pool ordinatamente.
    Chiamato da ServerMain quando il server si spegne.
     */
    
    public void shutdown() {
        if (currentGameTimer != null) {
            // false = non interrompere il task se è già in esecuzione
            currentGameTimer.cancel(false);
        }
        scheduler.shutdown();
        System.out.println("[GameManager] Scheduler fermato correttamente.");
    }
}