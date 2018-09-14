package org.sil.bloom.reader;

import android.support.test.espresso.web.sugar.Web;
import android.util.Log;
import android.webkit.WebView;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static android.support.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static android.support.test.espresso.web.sugar.Web.onWebView;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.AllOf.allOf;

/*
    Helper methods for running on-device tests
 */

public class AndroidTestHelper {
    // With the ViewPager more than one WebView is in the view hierarchy at any given time.
    // This method returns a WebInteraction for the WebView for the currently displayed page.
    public static Web.WebInteraction onWebViewForCurrentPage() {
        return new StubbornMethodDoer<Web.WebInteraction>().stubbornlyDo(new Callable<Web.WebInteraction>() {
            @Override
            public Web.WebInteraction call() {
                return onWebView(allOf(instanceOf(WebView.class), isCompletelyDisplayed()));
            }
        });
    }

    public static void sleep(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        }
        catch (InterruptedException e) {
            Log.e("AndroidTestHelper", "Interrupted Exception during sleep:\n" + e);
        }
    }

    // Sometimes Espresso gives up too quickly, so we have to be stubborn and make it try a few times
    static class StubbornMethodDoer<T> {
        public T stubbornlyDo(Callable<T> toDo) {
            final int MAX_TRIES = 20; // Equates to 2 seconds
            int tries = 0;
            while(true) {
                try{
                    return  toDo.call(); // onWebView(allOf(instanceOf(WebView.class), isCompletelyDisplayed()));
                }
                catch (RuntimeException e) {
                    // Such as NoMatchingViewException
                    Log.e("AndroidTestHelper", e.getLocalizedMessage());
                    if (tries >= MAX_TRIES)
                        throw e;
                    ++tries;
                    sleep(100);
                }
                catch (Exception e) {
                    Log.e("AndroidTestHelper", e.getLocalizedMessage());
                    return null;
                }
            }

        }
    }
}
