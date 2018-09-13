package org.sil.bloom.reader;

import android.graphics.Typeface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;

import static junit.framework.Assert.assertNotNull;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class QuizTest {
    @Test
    public void validEmptyQuiz() throws Exception {
        Quiz empty = new Quiz();
        assertEmptyQuiz(empty);
    }

    @Test
    public void moonAndCapQuiz() throws Exception {
        Quiz quiz = Quiz.readQuizFromFile(mockBloomFileReader(moonAndCapQuizJSON()), "en");
        assertThat(quiz.numberOfQuestions(), is(2));
        assertThat(quiz.getQuizQuestion(1).getQuestion(), is("What did the moon do?"));
        assertNull(quiz.getFont());
    }

    @Test
    public void customFont() throws Exception {
        BloomFileReader mockFileReader = mockBloomFileReader(customFontQuizJSON());
        Quiz quiz = Quiz.readQuizFromFile(mockFileReader, "en");
        assertNotNull(quiz.getFont());
        assertNotNull(quiz.getQuizQuestion(0).getFont());
    }

    @Test
    public void noJSONAtAllTest() throws Exception {
        Quiz quiz = Quiz.readQuizFromFile(mockBloomFileReader(null), "en");
        assertEmptyQuiz(quiz);
    }

    @Test
    public void malformattedJSONTest() throws Exception {
        Quiz quiz = Quiz.readQuizFromFile(mockBloomFileReader(malformattedJSON()), "en");
        assertEmptyQuiz(quiz);
    }

    @Test
    public void noMatchingLanguageTest() throws Exception {
        Quiz quiz = Quiz.readQuizFromFile(mockBloomFileReader(moonAndCapQuizJSON()), "fr");
        assertEmptyQuiz(quiz);
    }

    private void assertEmptyQuiz(Quiz quiz) {
        assertThat(quiz.numberOfQuestions(), is(0));
    }

    private BloomFileReader mockBloomFileReader(String questionsJSON) {
        BloomFileReader mockFileReader = mock(BloomFileReader.class);
        when(mockFileReader.getFileContent("questions.json")).thenReturn(questionsJSON);
        when(mockFileReader.getFontFile("GamjaFlower-Regular.ttf")).thenReturn(new File("mock"));
        return mockFileReader;
    }

    private String moonAndCapQuizJSON() {
        return "[\n" +
                "  {\n" +
                "    \"questions\": [\n" +
                "      {\n" +
                "        \"question\": \"How did the boy lose his cap?\",\n" +
                "        \"answers\": [\n" +
                "          { \"text\": \"The bully took it\", \"correct\": false },\n" +
                "          { \"text\": \"The wind blew it away\", \"correct\": true },\n" +
                "          { \"text\": \"He left it on the bus\", \"correct\": false }\n" +
                "        ]\n" +
                "      },\n" +
                "      {\n" +
                "        \"question\": \"What did the moon do?\",\n" +
                "        \"answers\": [\n" +
                "          { \"text\": \"Sent him a lollipop\", \"correct\": false },\n" +
                "          { \"text\": \"Hid behind clouds\", \"correct\": false },\n" +
                "          { \"text\": \"Appeared to wear the cap\", \"correct\": true }\n" +
                "        ]\n" +
                "      }\n" +
                "    ],\n" +
                "    \"lang\": \"en\"\n" +
                "  }\n" +
                "]";
    }

    private String customFontQuizJSON() {
        return "[\n" +
                "  {\n" +
                "    \"questions\": [\n" +
                "      {\n" +
                "        \"question\": \"How did the boy lose his cap?\",\n" +
                "        \"answers\": [\n" +
                "          { \"text\": \"The bully took it\", \"correct\": false },\n" +
                "          { \"text\": \"The wind blew it away\", \"correct\": true },\n" +
                "          { \"text\": \"He left it on the bus\", \"correct\": false }\n" +
                "        ]\n" +
                "      }\n" +
                "    ],\n" +
                "    \"lang\": \"en\",\n" +
                "    \"font\": \"GamjaFlower-Regular.ttf\"\n" +
                "  }\n" +
                "]";
    }

    private String malformattedJSON() {
        return "[\n" +
                "  {\n" +
                "    \"questions\": [\n" +
                "      {\n" +
                "        \"question\": \"How did the boy lose his cap?\",\n" +
                "        \"answers\": [\n" +
                "          { \"text\": \"The bully took it\", \"correct\": false },\n" +
                "          { \"text\": \"The wind blew it away\", \"correct\": true },\n" +
                "          { \"text\": \"He left it on the bus\", \"correct\": false }\n" +
                "        ]\n" +
                "      },\n" +
                "      {\n" +
                "        \"question\": \"What did the moon do?\",\n" +
                "        \"answers\": [\n" +
                "          { \"text\": \"Sent him a lollipop\", \"correct\": false },\n" +
                "          { \"text\": \"Hid behind clouds\", \"correct\": false },\n" +
                "          { \"texxxxxxxxxxt\": \"Appeared to wear the cap\", \"correct\": true }\n" +
                "        ]\n" +
                "      }\n" +
                "    ],\n" +
                "    \"lang\": \"en\"\n" +
                "  }\n" +
                "]";
    }
}