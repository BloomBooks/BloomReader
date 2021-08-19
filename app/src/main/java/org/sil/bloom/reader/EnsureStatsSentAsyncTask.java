package org.sil.bloom.reader;

import android.content.Context;
import android.os.AsyncTask;

import com.segment.analytics.Analytics;
import com.segment.analytics.StatsSnapshot;

import java.lang.ref.WeakReference;

// A background task to check if analytics events have been sent.
// This is used to place information on the navigation menu.
public class EnsureStatsSentAsyncTask extends AsyncTask<Void, Void, Boolean> {
    private static long s_flushEventCountBefore;
    private static boolean s_haveSentEvent;

    private final WeakReference<Context> _contextRef;
    private final AsyncResponse _asyncResponse;

    public EnsureStatsSentAsyncTask(Context context, AsyncResponse asyncResponse) {
        _contextRef = new WeakReference<>(context);
        _asyncResponse = asyncResponse;
    }

    @Override
    public Boolean doInBackground(Void... v) {
        try {
            final Context context = _contextRef.get();
            if (context == null)
                return false;

            Analytics analytics = Analytics.with(context);

            StatsSnapshot snapshot = analytics.getSnapshot();
            long flushEventCountBefore = snapshot.flushEventCount;
            long flushEventCountAfter;
            analytics.flush();

            int countSleeps = 0;
            do {
                if (isCancelled())
                    return false;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return false;
                }
                snapshot = analytics.getSnapshot();
                flushEventCountAfter = snapshot.flushEventCount;

                // All events may have already been sent before we called this.
                // So we may need to send our own event to get the flush count to change.
                // However, we want to minimize doing this as much as possible so as not to push events off
                // the 1000-limit queue and save bandwidth, etc.
                // We can avoid sending another one if
                //  - no events have been sent since starting the app (there is always an Application Opened event)
                // OR
                //  - we have already sent one here and the event count has not increased since then.
                boolean eventIsGuaranteedInQueue = flushEventCountBefore == 0 || (s_haveSentEvent && s_flushEventCountBefore == flushEventCountBefore);
                if (!eventIsGuaranteedInQueue && countSleeps == 3) {
                    analytics.track("Check For Sending Events");
                    analytics.flush();
                    s_flushEventCountBefore = flushEventCountBefore;
                    s_haveSentEvent = true;
                }

                countSleeps++;
            } while (flushEventCountAfter == flushEventCountBefore);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void onPostExecute(Boolean success) {
        _asyncResponse.processComplete(success);
    }

    public interface AsyncResponse {
        void processComplete(boolean success);
    }
}
