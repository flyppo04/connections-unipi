package com.unipi.messages.request;

/**
 * Richiesta di aggiornamento credenziali (username e/o password).
 * I campi newName e newPsw sono opzionali:
 *   - solo newName presente  -> aggiorna username
 *   - solo newPsw presente   -> aggiorna password
 *   - entrambi presenti      -> aggiorna entrambi
 *
 * Formato JSON atteso:
 * {
 *   "operation": "updateCredentials",
 *   "oldName": STRING,
 *   "oldPsw":  STRING,
 *   *"newName": STRING,
 *   *"newPsw":  STRING
 * }
 */
public class UpdateCredentialsRequest extends BaseRequest {

    private String oldName;
    private String newName;   // opzionale
    private String oldPsw;
    private String newPsw;    // opzionale

    public UpdateCredentialsRequest() {}

    public String getOldName() { return oldName; }
    public void setOldName(String oldName) { this.oldName = oldName; }

    public String getNewName() { return newName; }
    public void setNewName(String newName) { this.newName = newName; }

    public String getOldPsw() { return oldPsw; }
    public void setOldPsw(String oldPsw) { this.oldPsw = oldPsw; }

    public String getNewPsw() { return newPsw; }
    public void setNewPsw(String newPsw) { this.newPsw = newPsw; }

    public boolean wantsNameUpdate() {
        return newName != null && !newName.isBlank();
    }

    public boolean wantsPasswordUpdate() {
        return newPsw != null && !newPsw.isBlank();
    }

    @Override
    public String toString() {
        return "UpdateCredentialsRequest{oldName='" + oldName
                + "', wantsNameUpdate=" + wantsNameUpdate()
                + ", wantsPasswordUpdate=" + wantsPasswordUpdate() + "}";
    }
}
