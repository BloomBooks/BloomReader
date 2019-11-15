package org.sil.bloom.reader;

import android.os.AsyncTask;
import android.view.View;
import android.widget.LinearLayout;

import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

// This task is in charge of loading up the book collection asynchronously.  When there are a large
// number of books, especially in the external folder, startup can be rather slow before everything
// is ready to display.  This allows the overall framework of the main display to come up quickly
// with a progress bar at the bottom that shows progress working through the list of books.
// See https://issues.bloomlibrary.org/youtrack/issue/BL-7432.
public class InitializeLibraryTask extends AsyncTask<MainActivity, Void, Void> {
    private ExtStorageUnavailableException mExceptionCaught = null;
    private MainActivity mMain = null;
    private long mBeginningTime = new Date().getTime();
    private Timer mTimer;

    // Initialize the  maximum value for the progress bar to better reflect reality.  It still
    // may not be perfect.  It seems safest to do this on the UI thread.
    public void setBookCount(final Integer count) {
        mMain.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMain.mLoadingProgressBar.setMax(count);
            }
        });
    }
    // Advance the progress bar for one book being processed.
    public void incrementBookProgress() {
        publishProgress();
    }

    @Override
    protected Void doInBackground(MainActivity... activities) {
        try {
            mMain = activities[0];
            BloomReaderApplication.theOneBookCollection.init(mMain.getApplicationContext(), this);
        }
        catch (ExtStorageUnavailableException e) {
            mExceptionCaught = e;
        }
        return null;
    }
    @Override
    protected void onProgressUpdate(Void... progress) {
        mMain.mLoadingProgressBar.incrementProgressBy(1);
    }
    @Override
    protected void onPostExecute(Void result) {
        long now = new Date().getTime();
        long delta = now -mBeginningTime;
        if (delta < 1000) {   // want message to be visible for at least one second
            ScheduleRemovingProgressNotice(delta);
        } else {
            mMain.removeProgressViews();
        }
        // Ensure all the books are displayed at the end of loading.
        mMain.mBookListAdapter.notifyDataSetChanged();
        mMain = null;   // release reference

        if (mExceptionCaught != null) {
            mMain.externalStorageUnavailable(mExceptionCaught);
        }
    }

    private void ScheduleRemovingProgressNotice(long delta) {
        long wait = 1000 - delta;
        if (wait < 50) {
            wait = 50;     // let's wait more than just a millisecond or so...
        }
        mTimer = new Timer();
        mTimer.schedule(new HideTask(mMain), wait);
    }

    private class HideTask extends TimerTask {
        MainActivity mMain;

        public HideTask(MainActivity main) {
            mMain = main;
        }
        public void run() {
            mTimer.cancel(); //Terminate the timer thread
            // Run the remove method on the UI thread
            mMain.runOnUiThread(new Runnable() {
                public void run() {
                    mMain.removeProgressViews();
                }
            });
        }
    }
}
