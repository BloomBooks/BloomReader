package org.sil.bloom.reader;


import android.support.test.espresso.action.ViewActions;
import android.support.test.espresso.web.webdriver.Locator;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItem;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static android.support.test.espresso.web.model.Atoms.castOrDie;
import static android.support.test.espresso.web.model.Atoms.script;
import static android.support.test.espresso.web.sugar.Web.onWebView;
import static android.support.test.espresso.web.webdriver.DriverAtoms.findElement;
import static android.support.test.espresso.web.webdriver.DriverAtoms.getText;
import static android.support.test.espresso.web.webdriver.DriverAtoms.webClick;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ReadMoonAndCapTest {
    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void readMoonAndCapTest() {
        clickOnMoonAndCap();
        checkTitlePage();
        turnThePage();
        checkPageOne();
        turnToTheFirstCQ();
        clickTheWrongAnswerToCQ1AndVerify();
        clickTheRightAnswerToCQ1AndVerify();
    }

    private void clickOnMoonAndCap() {
        onView(withId(R.id.book_list2))
                 .perform(actionOnItem(hasDescendant(withText("The Moon and the Cap")), click()));
    }

    private void checkTitlePage() {
        onWebView()
                .withElement(findElement(Locator.CSS_SELECTOR, "body"))
                .check(webMatches(getText(), containsString("The Moon and the Cap")));
    }

    private void turnThePage() {
        onView(withId(R.id.bloom_player))
                .perform(ViewActions.swipeLeft());
    }

    private void checkPageOne() {
        onWebView()
                .withElement(findElement(Locator.CSS_SELECTOR, "body"))
                .check(webMatches(getText(), containsString("All of us went to the fun fair.")));
    }

    private void turnToTheFirstCQ() {
        // It's on page 12, we're starting on page 1
        // This process seems a bit flaky. I bumped up the sleep time a bit and added a flag
        // to build.gradle(app) that disables animations for the test.
        for (int p=1; p<12; ++p) {
            AndroidTestHelper.sleep(700); // Unfortunately necessary
            turnThePage();
        }
    }

    private void clickTheWrongAnswerToCQ1AndVerify() {
        String incorrectCheckboxSelector = ".checkbox-and-textbox-choice:not(.correct-answer) input";
        clickAndVerifyCheckbox(incorrectCheckboxSelector, false);
    }

    private void clickTheRightAnswerToCQ1AndVerify() {
        String correctCheckboxSelector = ".checkbox-and-textbox-choice.correct-answer input";
        clickAndVerifyCheckbox(correctCheckboxSelector, true);
    }

    private void clickAndVerifyCheckbox(String cssSelector, boolean expected) {
        AndroidTestHelper.sleep(200); // Unfortunately necessary
        onWebView()
                .withElement(findElement(Locator.CSS_SELECTOR, cssSelector))
                .perform(webClick())
                .check(webMatches(script("return document.querySelector('" + cssSelector + "').checked", castOrDie(Boolean.class)), is(expected)));
    }
}