package com.unipi.messages.request;

/**
 * Richiesta di login.
 * In caso di successo il server risponde con le informazioni
 * sulla partita attualmente in corso.
 *
 * Formato JSON atteso:
 * {
 *   "operation": "login",
 *   "username": STRING,
 *   "psw":      STRING
 * }
 */
public class LoginRequest extends BaseRequest {

    private String username;
    private String psw;
    private int udpPort; // per udp notifiche

    public LoginRequest() {}

    public LoginRequest(String username, String psw) {
        this.username = username;
        this.psw      = psw;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPsw() { return psw; }
    public void setPsw(String psw) { this.psw = psw; }

    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }

    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "'}";
    }
}
