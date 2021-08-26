package org.sil.bloom.reader;

import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
    Represents a series of comprehension questions derived from a questions.json included in the
    bloompub/bloomd file.
 */

public class Quiz {
    private List<QuizQuestion> questions = new ArrayList<>();
    private Typeface font = null;

    public Quiz() {}

    public int numberOfQuestions() {
        return questions.size();
    }

    public QuizQuestion getQuizQuestion(int index) {
        return questions.get(index);
    }

    @Nullable // If no custom font was set
    public Typeface getFont() { return font; }

    public static Quiz readQuizFromFile(BloomFileReader fileReader, String primaryLanguage) {
        try{
            Quiz quiz = new Quiz();
            String questionSource = fileReader.getFileContent("questions.json");
            JSONArray groups = new JSONArray(questionSource);
            int numberOfPreviousGroupsQuestions = 0;
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                if (!group.getString("lang").equals(primaryLanguage))
                    continue;
                if( group.has("font")) {
                    quiz.setFont(fileReader, group.getString("font"));
                }
                JSONArray groupQuestions = group.getJSONArray("questions");
                for (int j = 0; j < groupQuestions.length(); j++) {
                    quiz.questions.add(new QuizQuestion(groupQuestions.getJSONObject(j), numberOfPreviousGroupsQuestions + j, quiz.font));
                }
                numberOfPreviousGroupsQuestions += groupQuestions.length();
            }
            return quiz;
        } catch(Exception e) {
            Log.e("Quiz", "Error parsing questions.json:\n" + e);
            return new Quiz();
        }
    }

    private void setFont(BloomFileReader fileReader, String fontName) {
        File fontFile = fileReader.getFontFile(fontName);
        if (fontFile != null) {
            this.font = Typeface.createFromFile(fontFile);
        }

    }
}
