package com.unipi.messages.response;

import java.util.List;

/**
 * Risposta all'invio di una proposta di raggruppamento.
 */
public class SubmitProposalResponse extends BaseResponse {

    /** true se le 4 parole proposte formano un gruppo corretto. */
    private boolean correct;

    /** Nome del gruppo indovinato. Valorizzato solo se correct = true. */
    private String groupName;

    /** Le 4 parole del gruppo indovinato. Valorizzato solo se correct = true. */
    private List<String> groupWords;

    /** Punteggio aggiornato del giocatore dopo questa proposta. */
    private int newScore;

    /** Numero totale di errori dopo questa proposta. */
    private int mistakes;

    /** true se la partita e terminata per questo giocatore. */
    private boolean gameOver;

    /** true se il giocatore ha vinto. Significativo solo se gameOver = true. */
    private boolean winner;

    public SubmitProposalResponse() {}

    public SubmitProposalResponse(String status, String message) {
        super(status, message);
    }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public List<String> getGroupWords() { return groupWords; }
    public void setGroupWords(List<String> groupWords) { this.groupWords = groupWords; }

    public int getNewScore() { return newScore; }
    public void setNewScore(int newScore) { this.newScore = newScore; }

    public int getMistakes() { return mistakes; }
    public void setMistakes(int mistakes) { this.mistakes = mistakes; }

    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }

    public boolean isWinner() { return winner; }
    public void setWinner(boolean winner) { this.winner = winner; }

    @Override
    public String toString() {
        return "SubmitProposalResponse{correct=" + correct + ", groupName='" + groupName
                + "', newScore=" + newScore + ", mistakes=" + mistakes
                + ", gameOver=" + gameOver + ", winner=" + winner + "}";
    }
}
