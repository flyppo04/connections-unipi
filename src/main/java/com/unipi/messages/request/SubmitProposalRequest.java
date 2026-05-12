package com.unipi.messages.request;

import java.util.List;

/**
 * Richiesta di invio di una proposta di raggruppamento.
 * Deve contenere esattamente 4 parole tra quelle non ancora
 * correttamente raggruppate dal giocatore.
 *
 * Formato JSON atteso:
 * {
 *   "operation": "submitProposal",
 *   "words": [STRING, STRING, STRING, STRING]
 * }
 */
public class SubmitProposalRequest extends BaseRequest {

    private List<String> words;

    public SubmitProposalRequest() {}

    public SubmitProposalRequest(List<String> words) {
        this.words = words;
    }

    public List<String> getWords() { return words; }
    public void setWords(List<String> words) { this.words = words; }

    /** Verifica che la proposta contenga esattamente 4 parole. */
    public boolean isWellFormed() {
        return words != null && words.size() == 4;
    }

    @Override
    public String toString() {
        return "SubmitProposalRequest{words=" + words + "}";
    }
}
