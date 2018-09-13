package android.graphics;

/*
    Mock Typeface class for testing quizzes
    because Quiz.setTypeface() uses a static method on Typeface
 */

import java.io.File;

public class Typeface {
    public static Typeface createFromFile(File file) {
        return new Typeface();
    }
}
