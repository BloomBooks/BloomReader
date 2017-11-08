package org.sil.bloom.reader;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;

import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Created by Thomson on 11/7/2017.
 */
public class ReaderActivityTest {
    @Test
    public void makeQuestionPages_OneQuestion_AppendsOnePage() throws Exception {
        String input =  wrapQuestionContent("<p>What is a question?</p>\n\n<p>a way to keep score</p>\n\n<p>*a way to check comprehension</p>\n<p>a way to embarrass readers</p>");
        ArrayList<String> pages = new ArrayList<String>();
        pages.add("dummy");
        ReaderActivity.MakeQuestionPages(pages, input, "tpi");
        assertThat(pages.get(0), is("dummy")); // didn't insert before it
        assertThat(pages.size(), is(2));
        String questionPage = pages.get(1);
        assertThat(pages.get(1), is("What is a question?\na way to keep score\n*a way to check comprehension\na way to embarrass readers"));
    }

    @Test
    public void makeQuestionPages_TwoQuestions_AppendsTwoPages() throws Exception {
        String input =  wrapQuestionContent("\r\n     <p>What is a question?</p>\n\n<p>a way to keep score</p>\r\n\n<p>*a way to check comprehension</p>\n<p>a way to embarrass readers</p><p></p>" +
                "<p>Why ask questions?<br></br>\n*to see what students learned<br></br>\nto annoy students</p>");
        ArrayList<String> pages = new ArrayList<String>();
        pages.add("dummy");
        ReaderActivity.MakeQuestionPages(pages, input, "tpi");
        assertThat(pages.get(0), is("dummy")); // didn't insert before it
        assertThat(pages.size(), is(3));
        assertThat(pages.get(1), is("What is a question?\na way to keep score\n*a way to check comprehension\na way to embarrass readers"));
        assertThat(pages.get(2), is("Why ask questions?\n*to see what students learned\nto annoy students"));
    }

    // This is a simplification of the structure of a real bloom questions page,
    // but as long as it is enough like it for the question content to be found
    // all is well.
    String wrapQuestionContent(String content) {
        return "<div>    <div class=\"bloom-editable\" lang=\"wrong\">don't want this</div><div class=\"bloom-editable\" lang=\"tpi\">"
                + content
                + "</div></div>";
    }
}