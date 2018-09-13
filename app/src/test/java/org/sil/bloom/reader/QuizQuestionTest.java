package org.sil.bloom.reader;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertNull;

public class QuizQuestionTest {
    @Test
    public void basicQuestion() throws Exception {
        QuizQuestion question = new QuizQuestion(basicQuestionObject(), 0, null);
        assertThat(question.getQuestion(), is("How did the boy lose his cap?"));
        assertThat(question.getQuestionIndex(), is(0));
        assertThat(question.getNumberOfAnswers(), is(3));
        assertThat(question.getAnswer(2), is("He left it on the bus"));
        assertThat(question.answerIsCorrect(1), is(true));
        assertThat(question.answerIsCorrect(0), is(false));
        assertNull(question.getFont());
    }

    private JSONObject basicQuestionObject() throws JSONException {
        return new JSONObject("{\n" +
                "        \"question\": \"How did the boy lose his cap?\",\n" +
                "        \"answers\": [\n" +
                "          { \"text\": \"The bully took it\", \"correct\": false },\n" +
                "          { \"text\": \"The wind blew it away\", \"correct\": true },\n" +
                "          { \"text\": \"He left it on the bus\", \"correct\": false }\n" +
                "        ]\n" +
                "      }");
    }
}
