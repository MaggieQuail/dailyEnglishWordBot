package org.vpmq.dailyword.models;

public class VocabularyModel {
    public int id;
    public String word;
    public String translation;
    public String explanation;

    public VocabularyModel(int id, String word, String translation) {
        this.id = id;
        this.word = word;
        this.translation = translation;
    }
}
