package org.sil.bloom.reader;

import androidx.test.espresso.web.sugar.Web;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static org.hamcrest.core.AllOf.allOf;

/*
    Helper methods for running on-device tests
 */

public class AndroidTestHelper {

    public static void sleep(int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        }
        catch (InterruptedException e) {
            Log.e("AndroidTestHelper", "Interrupted Exception during sleep:\n" + e);
        }
    }
}
