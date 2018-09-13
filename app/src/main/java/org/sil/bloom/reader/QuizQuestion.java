package org.sil.bloom.reader;

import android.graphics.Typeface;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/*
    Represents a single comprehension questions included in a Quiz object
 */

public class QuizQuestion {
    private String question;
    private int questionIndex;
    private String[] answers;
    private boolean[] answersCorrectness;  // Parallel array to answers[]. True if the corresponding answer is correct
    private Typeface font;

    public QuizQuestion(JSONObject questionJSON, int questionIndex, Typeface font) throws JSONException{
        this.questionIndex = questionIndex;
        this.font = font;
        buildFromJSONObject(questionJSON);
    }

    public String getQuestion() { return question; }

    public int getQuestionIndex() { return questionIndex; }

    public int getNumberOfAnswers() { return answers.length; }

    public String getAnswer(int index) {
        return answers[index];
    }

    public boolean answerIsCorrect(int index) {
        return answersCorrectness[index];
    }

    @Nullable // If no custom font was set
    public Typeface getFont() { return font; }

    private void buildFromJSONObject(JSONObject questionJSON) throws JSONException {
        question = questionJSON.getString("question");

        JSONArray answersJSON = questionJSON.getJSONArray("answers");
        int numberOfAnswers = answersJSON.length();
        answers = new String[numberOfAnswers];
        answersCorrectness = new boolean[numberOfAnswers];

        for(int i=0; i<numberOfAnswers; ++i) {
            JSONObject answerJSON = answersJSON.getJSONObject(i);
            answers[i] = answerJSON.getString("text");
            answersCorrectness[i] = answerJSON.getBoolean("correct");
        }
    }
}
