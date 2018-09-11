package org.sil.bloom.reader;

import android.graphics.Typeface;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class QuizQuestion {
    private String question;
    private int questionIndex;
    private int numberOfAnswers;
    private String[] answers;
    private boolean[] correctAnswers;
    private Typeface font;

    public QuizQuestion(JSONObject questionJSON, int questionIndex, Typeface font) throws JSONException{
        this.questionIndex = questionIndex;
        this.font = font;
        buildFromJSONObject(questionJSON);
    }

    public String getQuestion() { return question; }

    public int getQuestionIndex() { return questionIndex; }

    public int getNumberOfAnswers() { return numberOfAnswers; }

    public String getAnswer(int index) {
        return answers[index];
    }

    public boolean answerIsCorrect(int index) {
        return correctAnswers[index];
    }

    @Nullable // If no custom font was set
    public Typeface getFont() { return font; }

    private void buildFromJSONObject(JSONObject questionJSON) throws JSONException {
        question = questionJSON.getString("question");

        JSONArray answersJSON = questionJSON.getJSONArray("answers");
        numberOfAnswers = answersJSON.length();
        answers = new String[numberOfAnswers];
        correctAnswers = new boolean[numberOfAnswers];

        for(int i=0; i<numberOfAnswers; ++i) {
            JSONObject answerJSON = answersJSON.getJSONObject(i);
            answers[i] = answerJSON.getString("text");
            correctAnswers[i] = answerJSON.getBoolean("correct");
        }
    }
}
