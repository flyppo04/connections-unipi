package com.unipi.messages.request;

/**
 * Richiesta di registrazione di un nuovo utente.
 * Formato JSON atteso:
 * {
 *   "operation": "register",
 *   "name": STRING,
 *   "psw": STRING
 * }
 */
public class RegisterRequest extends BaseRequest {

    private String name;
    private String psw;

    public RegisterRequest() {}

    public RegisterRequest(String name, String psw) {
        this.name = name;
        this.psw  = psw;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPsw() { return psw; }
    public void setPsw(String psw) { this.psw = psw; }

    @Override
    public String toString() {
        return "RegisterRequest{name='" + name + "'}";
    }
}
