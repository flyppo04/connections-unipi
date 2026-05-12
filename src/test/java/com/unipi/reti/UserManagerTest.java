package com.unipi.reti;

import com.unipi.game.UserManager;
import com.unipi.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per UserManager.
 * Verifica: register, login, logout, updateCredentials, leaderboard.
 *
 * NOTA: login richiede un SocketChannel aperto. I test che lo usano
 * lo aprono e lo chiudono esplicitamente in un blocco try-finally.
 */
@DisplayName("UserManager — gestione utenti e classifica")
class UserManagerTest {

    private UserManager um;

    @BeforeEach
    void setUp() {
        um = UserManager.createNew();
    }

    // ─── Register ────────────────────────────────────────────────────

    @Test
    @DisplayName("register: successo con credenziali valide")
    void register_success() {
        assertEquals(UserManager.RegisterResult.SUCCESS,
            um.register("alice", "pwd"));
    }

    @Test
    @DisplayName("register: fallisce se lo username è già registrato")
    void register_duplicateUsername() {
        um.register("alice", "pwd");
        assertEquals(UserManager.RegisterResult.USERNAME_ALREADY_EXISTS,
            um.register("alice", "altro"));
    }

    @Test
    @DisplayName("register: fallisce con username vuoto")
    void register_invalidUsername() {
        assertEquals(UserManager.RegisterResult.INVALID_INPUT,
            um.register("", "pwd"));
    }

    @Test
    @DisplayName("register: fallisce con password vuota")
    void register_invalidPassword() {
        assertEquals(UserManager.RegisterResult.INVALID_INPUT,
            um.register("alice", ""));
    }

    @Test
    @DisplayName("register: fallisce con username null")
    void register_nullUsername() {
        assertEquals(UserManager.RegisterResult.INVALID_INPUT,
            um.register(null, "pwd"));
    }

    // ─── Login ───────────────────────────────────────────────────────

    @Test
    @DisplayName("login: utente inesistente → USER_NOT_FOUND")
    void login_userNotFound() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            assertEquals(UserManager.LoginResult.USER_NOT_FOUND,
                um.login("ghost", "pwd", ch));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("login: password errata → WRONG_PASSWORD")
    void login_wrongPassword() throws IOException {
        um.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            assertEquals(UserManager.LoginResult.WRONG_PASSWORD,
                um.login("alice", "sbagliata", ch));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("login: successo con credenziali corrette")
    void login_success() throws IOException {
        um.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            assertEquals(UserManager.LoginResult.SUCCESS,
                um.login("alice", "pwd", ch));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("login: secondo login sullo stesso account → ALREADY_LOGGED_IN")
    void login_alreadyLoggedIn() throws IOException {
        um.register("alice", "pwd");
        SocketChannel ch1 = SocketChannel.open();
        SocketChannel ch2 = SocketChannel.open();
        try {
            um.login("alice", "pwd", ch1);
            assertEquals(UserManager.LoginResult.ALREADY_LOGGED_IN,
                um.login("alice", "pwd", ch2));
        } finally { ch1.close(); ch2.close(); }
    }

    @Test
    @DisplayName("login: dopo login, isLoggedIn restituisce true")
    void login_isLoggedIn() throws IOException {
        um.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            um.login("alice", "pwd", ch);
            assertTrue(um.isLoggedIn("alice"));
        } finally { ch.close(); }
    }

    // ─── Logout ──────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: successo dopo un login")
    void logout_success() throws IOException {
        um.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            um.login("alice", "pwd", ch);
            assertEquals(UserManager.LogoutResult.SUCCESS, um.logout(ch));
            assertFalse(um.isLoggedIn("alice"));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("logout: canale non associato → USER_NOT_LOGGED_IN")
    void logout_notLoggedIn() throws IOException {
        SocketChannel ch = SocketChannel.open();
        try {
            assertEquals(UserManager.LogoutResult.USER_NOT_LOGGED_IN, um.logout(ch));
        } finally { ch.close(); }
    }

    @Test
    @DisplayName("logout: doppio logout sullo stesso canale → secondo fallisce")
    void logout_doubleLogout() throws IOException {
        um.register("alice", "pwd");
        SocketChannel ch = SocketChannel.open();
        try {
            um.login("alice", "pwd", ch);
            um.logout(ch);
            assertEquals(UserManager.LogoutResult.USER_NOT_LOGGED_IN, um.logout(ch));
        } finally { ch.close(); }
    }

    // ─── UpdateCredentials ────────────────────────────────────────────

    @Test
    @DisplayName("updateCredentials: cambia solo la password")
    void updateCredentials_passwordOnly() {
        um.register("alice", "pwd");
        assertEquals(UserManager.UpdateResult.SUCCESS,
            um.updateCredentials("alice", "pwd", null, "newpwd"));

        User u = um.getUserByUsername("alice");
        assertEquals("newpwd", u.getPassword());
    }

    @Test
    @DisplayName("updateCredentials: cambia solo lo username")
    void updateCredentials_usernameOnly() {
        um.register("alice", "pwd");
        assertEquals(UserManager.UpdateResult.SUCCESS,
            um.updateCredentials("alice", "pwd", "alice2", null));

        assertNull(um.getUserByUsername("alice"));
        assertNotNull(um.getUserByUsername("alice2"));
    }

    @Test
    @DisplayName("updateCredentials: cambia sia username che password")
    void updateCredentials_both() {
        um.register("alice", "pwd");
        um.updateCredentials("alice", "pwd", "alice2", "newpwd");

        User u = um.getUserByUsername("alice2");
        assertNotNull(u);
        assertEquals("alice2", u.getUsername());
        assertEquals("newpwd", u.getPassword());
    }

    @Test
    @DisplayName("updateCredentials: vecchia password errata → WRONG_PASSWORD")
    void updateCredentials_wrongOldPassword() {
        um.register("alice", "pwd");
        assertEquals(UserManager.UpdateResult.WRONG_PASSWORD,
            um.updateCredentials("alice", "sbagliata", "alice2", null));
    }

    @Test
    @DisplayName("updateCredentials: nuovo username già occupato → NEW_USERNAME_TAKEN")
    void updateCredentials_newUsernameTaken() {
        um.register("alice", "pwd");
        um.register("bob", "pwd");
        assertEquals(UserManager.UpdateResult.NEW_USERNAME_TAKEN,
            um.updateCredentials("alice", "pwd", "bob", null));
    }

    @Test
    @DisplayName("updateCredentials: utente inesistente → USER_NOT_FOUND")
    void updateCredentials_userNotFound() {
        assertEquals(UserManager.UpdateResult.USER_NOT_FOUND,
            um.updateCredentials("ghost", "pwd", "ghost2", null));
    }

    @Test
    @DisplayName("updateCredentials: nessun campo da aggiornare → INVALID_INPUT")
    void updateCredentials_nothingToUpdate() {
        um.register("alice", "pwd");
        assertEquals(UserManager.UpdateResult.INVALID_INPUT,
            um.updateCredentials("alice", "pwd", null, null));
    }

    // ─── Leaderboard ─────────────────────────────────────────────────

    @Test
    @DisplayName("getLeaderboard: ordinata per punteggio decrescente")
    void getLeaderboard_sortedByScoreDescending() {
        um.register("alice", "pwd");
        um.register("bob", "pwd");
        um.register("carol", "pwd");

        um.getUserByUsername("alice").addScore(30);
        um.getUserByUsername("carol").addScore(50);
        um.getUserByUsername("bob").addScore(10);

        List<User> lb = um.getLeaderboard();
        assertEquals("carol", lb.get(0).getUsername());
        assertEquals("alice", lb.get(1).getUsername());
        assertEquals("bob",   lb.get(2).getUsername());
    }

    @Test
    @DisplayName("getTopK: restituisce i primi K utenti")
    void getTopK_returnsTopK() {
        um.register("alice", "pwd"); um.getUserByUsername("alice").addScore(30);
        um.register("bob",   "pwd"); um.getUserByUsername("bob").addScore(10);
        um.register("carol", "pwd"); um.getUserByUsername("carol").addScore(50);

        List<User> top2 = um.getTopK(2);
        assertEquals(2, top2.size());
        assertEquals("carol", top2.get(0).getUsername());
        assertEquals("alice", top2.get(1).getUsername());
    }

    @Test
    @DisplayName("getRank: posizione 1-based corretta nella classifica")
    void getRank_correctPosition() {
        um.register("alice", "pwd"); um.getUserByUsername("alice").addScore(30);
        um.register("bob",   "pwd"); um.getUserByUsername("bob").addScore(10);
        um.register("carol", "pwd"); um.getUserByUsername("carol").addScore(50);

        assertEquals(1, um.getRank("carol"));
        assertEquals(2, um.getRank("alice"));
        assertEquals(3, um.getRank("bob"));
    }

    @Test
    @DisplayName("getRank: utente inesistente → -1")
    void getRank_unknownUser() {
        assertEquals(-1, um.getRank("ghost"));
    }

    // ─── fromPersistedUsers ───────────────────────────────────────────

    @Test
    @DisplayName("fromPersistedUsers: carica correttamente una lista di utenti esistenti")
    void fromPersistedUsers_loadsUsers() {
        List<User> users = List.of(
            new User("alice", "pwd"),
            new User("bob",   "pwd2")
        );

        UserManager loaded = UserManager.fromPersistedUsers(users);
        assertNotNull(loaded.getUserByUsername("alice"));
        assertNotNull(loaded.getUserByUsername("bob"));
    }

    @Test
    @DisplayName("fromPersistedUsers: lista vuota → avvia come nuovo sistema")
    void fromPersistedUsers_emptyList() {
        UserManager loaded = UserManager.fromPersistedUsers(List.of());
        assertEquals(0, loaded.getAllUsers().size());
    }

    @Test
    @DisplayName("fromPersistedUsers: salta utenti corrotti (username null)")
    void fromPersistedUsers_skipsCorruptedUsers() {
        List<User> users = List.of(
            new User("alice", "pwd"),
            new User(null, "pwd")  // utente corrotto
        );

        // Non deve lanciare eccezioni, alice deve essere caricata
        UserManager loaded = UserManager.fromPersistedUsers(users);
        assertNotNull(loaded.getUserByUsername("alice"));
        assertEquals(1, loaded.getAllUsers().size());
    }
}
