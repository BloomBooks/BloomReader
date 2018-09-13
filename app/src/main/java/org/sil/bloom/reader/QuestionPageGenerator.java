package org.sil.bloom.reader;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/*
    Responsible for creating an Android View for a Comprehension Question (QuizQuestion) to be
    used as a page in the book.
 */

public class QuestionPageGenerator {
    private ReaderActivity readerActivity;

    public QuestionPageGenerator(ReaderActivity readerActivity){
        this.readerActivity = readerActivity;
    }

    public ConstraintLayout generate(final QuizQuestion quizQuestion, int numberOfQuestions, final QuestionAnsweredHandler questionAnsweredHandler) {
        LayoutInflater inflater = (LayoutInflater) readerActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ConstraintLayout questionPageView = (ConstraintLayout) inflater.inflate(R.layout.question_page, null);
        Typeface font = quizQuestion.getFont(); //null for default

        if (readerActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            putQuestionTextBesideProgressText(questionPageView);
        final TextView questionView = (TextView)questionPageView.findViewById(R.id.question);
        questionView.setText(quizQuestion.getQuestion());
        if (font != null)
            questionView.setTypeface(font);

        final LinearLayout answersLayout = (LinearLayout) questionPageView.findViewById(R.id.answers_layout);
        for (int i = 0; i < quizQuestion.getNumberOfAnswers(); i++) {
            // Passing the intended parent view allows the button's margins to work properly.
            // There's an explanation at https://stackoverflow.com/questions/5315529/layout-problem-with-button-margin.
            final CheckBox answerCheck = (CheckBox) inflater.inflate(R.layout.question_answer_check, answersLayout, false);
            answerCheck.setText(quizQuestion.getAnswer(i));
            if (font != null)
                answerCheck.setTypeface(font);
            if (quizQuestion.answerIsCorrect(i))
                answerCheck.setTag("correct");
            answerCheck.setOnClickListener(answerClickListener(answersLayout, quizQuestion.getQuestionIndex(), questionAnsweredHandler));
            answersLayout.addView(answerCheck);
        }

        TextView progressView = (TextView) questionPageView.findViewById(R.id.question_progress);
        progressView.setText(String.format(progressView.getText().toString(), quizQuestion.getQuestionIndex() + 1, numberOfQuestions));
        return questionPageView;
    }

    private void putQuestionTextBesideProgressText(ConstraintLayout questionPageView) {
        ConstraintSet questionConstraints = new ConstraintSet();
        questionConstraints.clone(questionPageView);
        questionConstraints.connect(R.id.question, ConstraintSet.END, R.id.question_progress, ConstraintSet.START);
        questionConstraints.connect(R.id.question, ConstraintSet.TOP, R.id.question_header, ConstraintSet.BOTTOM);
        questionConstraints.applyTo(questionPageView);
    }

    private View.OnClickListener answerClickListener(final LinearLayout answersLayout,
                                                     final int questionIndex,
                                                     final QuestionAnsweredHandler questionAnsweredHandler) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CheckBox answerCheck = (CheckBox) view;
                boolean correct = answerCheck.getTag() == "correct";

                if (correct) {
                    for (int i = 0; i < answersLayout.getChildCount(); i++) {
                        CheckBox check = (CheckBox) answersLayout.getChildAt(i);
                        if (check != answerCheck)
                            check.setEnabled(false);
                    }
                    readerActivity.playSoundFile(R.raw.right_answer);
                } else {
                    answerCheck.setEnabled(false);
                    answerCheck.setChecked(false);
                    readerActivity.playSoundFile(R.raw.wrong_answer);
                }

                // The questionAnsweredHandler processes analytics
                questionAnsweredHandler.questionAnswered(questionIndex, correct);
            }
        };
    }
}
