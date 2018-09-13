package org.sil.bloom.reader;

public interface QuestionAnsweredHandler{
    void questionAnswered(int questionIndex, boolean correct);
}