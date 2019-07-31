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
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnItem;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isChecked;
import static android.support.test.espresso.matcher.ViewMatchers.isNotChecked;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static android.support.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static android.support.test.espresso.web.webdriver.DriverAtoms.findElement;
import static android.support.test.espresso.web.webdriver.DriverAtoms.getText;
import static org.hamcrest.CoreMatchers.containsString;
import static org.sil.bloom.reader.AndroidTestHelper.onWebViewForCurrentPage;

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
        onWebViewForCurrentPage()
                .withElement(findElement(Locator.CSS_SELECTOR, "body"))
                .check(webMatches(getText(), containsString("The Moon and the Cap")));
    }

    private void turnThePage() {
        onView(withId(R.id.bloom_player))
                .perform(ViewActions.swipeLeft());
    }

    private void checkPageOne() {
        onWebViewForCurrentPage()
                .withElement(findElement(Locator.CSS_SELECTOR, "body"))
                .check(webMatches(getText(), containsString("All of us went to the fun fair.")));
    }

    private void turnToTheFirstCQ() {
        // It's on page 12, we're starting on page 1
        for (int p=1; p<12; ++p)
            turnThePage();
    }

    private void clickTheWrongAnswerToCQ1AndVerify() {
        AndroidTestHelper.sleep(200); // Unfortunately necessary
        onView(withText("The bully took it"))
                .perform(click());
        onView(withText("The bully took it"))
                .check(matches(isNotChecked()));
    }

    private void clickTheRightAnswerToCQ1AndVerify() {
        AndroidTestHelper.sleep(200);
        onView(withText("The wind blew it away"))
                .perform(click());
        onView(withText("The wind blew it away"))
                .check(matches(isChecked()));
    }
}