package com.unipi.game;

import com.unipi.models.User;

import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestisce la registrazione, il login e le credenziali degli utenti.
 */
public class UserManager {

    // Enum di risultato 

    public enum RegisterResult {
        SUCCESS,
        USERNAME_ALREADY_EXISTS,
        INVALID_INPUT
    }

    public enum LoginResult {
        SUCCESS,
        USER_NOT_FOUND,
        WRONG_PASSWORD,
        ALREADY_LOGGED_IN
    }

    public enum LogoutResult {
        SUCCESS,
        USER_NOT_LOGGED_IN
    }

    public enum UpdateResult {
        SUCCESS,
        USER_NOT_FOUND,
        WRONG_PASSWORD,
        NEW_USERNAME_TAKEN,
        INVALID_INPUT
    }

    // Strutture dati
    // Tutti gli utenti registrati: username -> User 
    private final ConcurrentHashMap<String, User> registeredUsers;

    // Utenti attualmente loggati: username -> canale NIO 
    private final ConcurrentHashMap<String, SocketChannel> loggedInUsers;

    //Mappa inversa: canale NIO -> username (per identificare mittente)     
    private final ConcurrentHashMap<SocketChannel, String> channelToUser;

    private final Object userStateLock = new Object();

    // Costruttore

    // APPLICAZIONE DEL DESIGN PATTERN FACTORY: 
    // creo un costruttore privato che puo esere chiamato solo da due "metodi"
    // uno per primo avvio e uno per ri-avvio (con utenti già presenti)
    private UserManager() {  
        this.registeredUsers = new ConcurrentHashMap<>();
        this.loggedInUsers   = new ConcurrentHashMap<>();
        this.channelToUser   = new ConcurrentHashMap<>();
    }

    public static UserManager createNew() { //metodo per creazione "modello" di primo avvio  
        return new UserManager();
    }

    public static UserManager fromPersistedUsers(List<User> users) { //metodo per creazione modello di ri-avvio con utenti già presenti

        //la factory mi permette di controllare specifcatamente i possibili errori
        //in questo caso verifico se il parametro da passare non è null o vuoto
        if (users == null || users.isEmpty()) {
            System.out.println("[UserManager] Nessun utente da caricare, " + "avvio come nuovo sistema.");
            return new UserManager();
        }

        UserManager um = new UserManager();  // chiama il costruttore privato
        int caricati = 0;
        int saltati  = 0;

        //controllo caso più problematico e specifico, utente corrotto, lo salto e proseguo 
        for (User u : users) {

             // utente corrotto: username o password null/vuoti
            if (u == null || u.getUsername() == null || u.getUsername().isBlank()
                        || u.getPassword() == null || u.getPassword().isBlank()) {
                System.err.println("[UserManager] Utente corrotto saltato: " + u);
                saltati++;
                continue;  // salta questo utente e continua con il prossimo
            }

            um.registeredUsers.put(u.getUsername(), u);
            caricati++;
        }

        System.out.println(
            "[UserManager] Utenti caricati: " + caricati + "\n Utenti saltati: " + saltati
        );

        return um;
    }

    // Operazioni sugli utenti
    /*Registra un nuovo utente.
    La password viene lasciata in chiaro essendo il progetto didattico, andrebbe almeno hashata nella realtà */

    public RegisterResult register(String username, String password) {
        // Validazione input — fail fast
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return RegisterResult.INVALID_INPUT;
        }

        User newUser = new User(username, password);

        // putIfAbsent è atomico: inserisce solo se la chiave non esiste già.
        // se non lo facessi atomico rischia che mentre verifico che non c'è un'altro 
        // thread lo inserisce, am io avevo verificato che non ci fosse e lo inserisco anche io 
        // RACE CODNITION!!! 
        // Restituisce null se ha inserito, il valore esistente se già c'era.
        User existing = registeredUsers.putIfAbsent(username, newUser);

        boolean inserted = (existing == null); 
        //se existing è null vuol dire che non è stato tornato nessun utente già presente 
        // e quindi l'utente è stato inserito

        return (inserted) ? RegisterResult.SUCCESS : RegisterResult.USERNAME_ALREADY_EXISTS;
    }

    //Effettua il login di un utente e associa il canale NIO alla sessione
    public LoginResult login(String username, String password, SocketChannel channel) {
        User user = registeredUsers.get(username);

        if (user == null) return LoginResult.USER_NOT_FOUND;

        if (!user.getPassword().equals(password)) {
            return LoginResult.WRONG_PASSWORD;
        }

        synchronized (userStateLock) {
            //devo controllare se è gia loggato dentro al lock altrimenti potrei tentare di loggarlo due volte 
            if (loggedInUsers.containsKey(username)) {
                return LoginResult.ALREADY_LOGGED_IN;
            }
    
            loggedInUsers.put(username, channel);
            channelToUser.put(channel, username);
            return LoginResult.SUCCESS;

        }
    }

    /* Effettua il logout identificando l'utente dal suo canale NIO
    Chiamato anche quando un canale si disconnette inaspettatamente */
    public LogoutResult logout(SocketChannel channel) {
        synchronized (userStateLock) {

            String username = channelToUser.remove(channel);
    
            if (username == null) return LogoutResult.USER_NOT_LOGGED_IN;
    
            loggedInUsers.remove(username);
            return LogoutResult.SUCCESS;
            
        }
    }

    
    //Aggiorna username e/o password di un utente, richiede la password attuale come conferma.
    
    public UpdateResult updateCredentials(String oldUsername, String oldPassword,String newUsername, String newPassword) {
        if ((newUsername == null || newUsername.isBlank()) &&
            (newPassword == null || newPassword.isBlank())) {
            return UpdateResult.INVALID_INPUT;
        }

        synchronized (userStateLock) {
            User user = registeredUsers.get(oldUsername);
            if (user == null) return UpdateResult.USER_NOT_FOUND;

            if (!user.getPassword().equals(oldPassword)) {
                return UpdateResult.WRONG_PASSWORD;
            }

            if (newUsername != null && !newUsername.isBlank() && !oldUsername.equals(newUsername)) {
                if (registeredUsers.containsKey(newUsername)) {
                    return UpdateResult.NEW_USERNAME_TAKEN;
                }

                registeredUsers.remove(oldUsername);
                user.setUsername(newUsername);
                registeredUsers.put(newUsername, user);

                SocketChannel ch = loggedInUsers.remove(oldUsername);
                if (ch != null) {
                    loggedInUsers.put(newUsername, ch);
                    channelToUser.put(ch, newUsername);
                }
            }

            if (newPassword != null && !newPassword.isBlank()) {
                user.setPassword(newPassword);
            }

            return UpdateResult.SUCCESS;
        }
    }

    // Query e classifica
    // Restituisce l'utente associato a un canale, o null se non loggato
    public User getUserByChannel(SocketChannel channel) {
        String username = channelToUser.get(channel);
        return (username != null) ? registeredUsers.get(username) : null;
    }

    // Restituisce un utente per username, o null se non esiste
    public User getUserByUsername(String username) {
        return registeredUsers.get(username);
    }

    //Restituisce true se l'utente è attualmente loggato
    public boolean isLoggedIn(String username) {
        return loggedInUsers.containsKey(username);
    }

    /* Restituisce la classifica completa ordinata per punteggio totale decrescente
    Usa Stream API per brevità e leggibilità */

    public List<User> getLeaderboard() {
        return registeredUsers.values() //prendi gli utenti
                .stream()               //apri una stream (catena di montaggio) 
                .sorted(                // ordinali 
                    Comparator.comparingInt(User::getTotalScore)  //usa come parametro pe ordinare il loro score
                    .reversed() //in senso decrescente
                ) 
                .collect(Collectors.toList()); //trasforma il risultato in una lista
    }


    //Restituisce i top K utenti della classifica

    public List<User> getTopK(int k) {
        return getLeaderboard().stream()
                .limit(k)
                .collect(Collectors.toList());
    }

    /* Restituisce la posizione in classifica di un utente (1-based)
    Restituisce -1 se l'utente non esiste*/
    
    public int getRank(String username) {
        List<User> leaderboard = getLeaderboard();
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).getUsername().equals(username)) {
                return i + 1;   // +1 perché la classifica parte da 1
            }
        }
        return -1;
    }

    // Restituisce tutti gli utenti registrati (per la persistenza)
    public Collection<User> getAllUsers() {
        return Collections.unmodifiableCollection(registeredUsers.values());
    }

    //Restituisce tutti i canali degli utenti loggati (per le notifiche UDP)
    public Collection<SocketChannel> getLoggedInChannels() {
        return Collections.unmodifiableCollection(loggedInUsers.values());
    }

}