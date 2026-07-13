package org.vpmq.dailyword.models;

public class StateModel {

    private final int id;
    private int wordId;

    public StateModel(int id, int wordId) {
        this.id = id;
        this.wordId = wordId;
    }

    public int getId() {
        return id;
    }

    public int getWordId() {
        return wordId;
    }

    public void setWordId(int wordId) {
        this.wordId = wordId;
    }
}
