package org.sil.bloom.reader;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

// This task is in charge of loading up the book collection asynchronously.  When there are a large
// number of books, especially in the external folder, startup can be rather slow before everything
// is ready to display.  This allows the overall framework of the main display to come up quickly
// with a progress bar at the bottom that shows progress working through the list of books.
// See https://issues.bloomlibrary.org/youtrack/issue/BL-7432.
public class InitializeLibraryTask extends AsyncTask<Void, Void, Void> {
    private ExtStorageUnavailableException mExceptionCaught = null;
    private final WeakReference<MainActivity> mainActivityRef;
    private final long mBeginningTime = new Date().getTime();
    private Timer mTimer;

    public InitializeLibraryTask(MainActivity mainActivity) {
        this.mainActivityRef = new WeakReference<>(mainActivity);
        addProgressViews(mainActivity);
    }
    // Initialize the  maximum value for the progress bar to better reflect reality.  It still
    // may not be perfect.  It seems safest to do this on the UI thread.
    public void setBookCount(final Integer count) {
        // get a reference to the activity if it is still there
        final MainActivity mainActivity = mainActivityRef.get();
        if (mainActivity == null || mainActivity.isFinishing())
            return;

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mainActivity.mLoadingProgressBar.setMax(count);
            }
        });
    }
    // Advance the progress bar for one book being processed.
    public void incrementBookProgress() {
        publishProgress();
    }

    @Override
    protected Void doInBackground(Void... v) {
        try {
            // get a reference to the activity if it is still there
            final MainActivity mainActivity = mainActivityRef.get();
            if (mainActivity == null || mainActivity.isFinishing())
                return null;

            BloomReaderApplication.theOneBookCollection.init(mainActivity, this);
        }
        catch (ExtStorageUnavailableException e) {
            mExceptionCaught = e;
        }
        return null;
    }
    @Override
    protected void onProgressUpdate(Void... v) {
        // get a reference to the activity if it is still there
        final MainActivity mainActivity = mainActivityRef.get();
        if (mainActivity == null || mainActivity.isFinishing())
            return;

        mainActivity.mLoadingProgressBar.incrementProgressBy(1);
    }
    @Override
    protected void onPostExecute(Void v) {
        // get a reference to the activity if it is still there
        final MainActivity mainActivity = mainActivityRef.get();
        if (mainActivity == null || mainActivity.isFinishing())
            return;

        long now = new Date().getTime();
        long delta = now - mBeginningTime;
        if (delta < 1000) {   // want message to be visible for at least one second
            scheduleRemovingProgressNotice(mainActivity, delta);
        } else {
            removeProgressViews(mainActivity);
        }
        // Ensure all the books are displayed at the end of loading.
        mainActivity.mBookListAdapter.notifyDataSetChanged();

        if (mExceptionCaught != null) {
            mainActivity.externalStorageUnavailable(mExceptionCaught);
        }
    }

    static private void addProgressViews(MainActivity main)
    {
        if (main == null)
            return;

        // Add TextView and ProgressBar to main content LinearLayout.  These will be removed automatically
        // when the asynchronous loading task finishes.
        LinearLayout linearLayout = main.findViewById(R.id.content_main);
        if (linearLayout != null) {
            // Create TextView dynamically to use during initial loading to tell user what is happening.
            main.mLoadingTextView = new TextView(main);
            String msg = main.getResources().getString(R.string.preparing_to_show_books);
            main.mLoadingTextView.setText(msg);
            LinearLayout.LayoutParams textViewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            textViewParams.setMargins(30, 30, 30, 1);   // bottom = 1
            main.mLoadingTextView.setLayoutParams(textViewParams);
            main.mLoadingTextView.setTextColor(Color.BLACK);
            // Create horizontal ProgressBar dynamically.  This is used during initial loading to reassure user
            // when there are lots of books to set up, especially from the external folder.
            // See https://issues.bloomlibrary.org/youtrack/issue/BL-7432.
            main.mLoadingProgressBar = new ProgressBar(main, null, android.R.attr.progressBarStyleHorizontal);
            LinearLayout.LayoutParams progressBarParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            progressBarParams.setMargins(30, 1, 30, 30);    // top = 1
            main.mLoadingProgressBar.setLayoutParams(progressBarParams);
            main.mLoadingProgressBar.setIndeterminate(false);
            main.mLoadingProgressBar.setProgressTintList(ColorStateList.valueOf(0xffd65649));    // @bloom-red: #d65649 (+ alpha = ff for opaque)

            linearLayout.addView(main.mLoadingTextView);
            linearLayout.addView(main.mLoadingProgressBar);
        }
    }

    static private void removeProgressViews(MainActivity main)
    {
        if (main == null)
            return;

        // Remove TextView and ProgressBar from main content LinearLayout
        main.mLoadingTextView.setVisibility(View.INVISIBLE);
        main.mLoadingProgressBar.setVisibility(View.INVISIBLE);
        LinearLayout linearLayout = main.findViewById(R.id.content_main);
        if (linearLayout != null) {
            if (main.mLoadingProgressBar != null) {
                linearLayout.removeView(main.mLoadingProgressBar);
                main.mLoadingProgressBar = null;
            }
            if (main.mLoadingTextView != null) {
                linearLayout.removeView(main.mLoadingTextView);
                main.mLoadingTextView = null;
            }
        }

    }

    private void scheduleRemovingProgressNotice(MainActivity mainActivity, long delta) {
        if (mainActivity == null || mainActivity.isFinishing())
            return;

        long wait = 1000 - delta;
        if (wait < 50) {
            wait = 50;     // let's wait more than just a millisecond or so...
        }
        mTimer = new Timer();
        mTimer.schedule(new HideTask(mainActivity), wait);
    }

    private class HideTask extends TimerTask {
        MainActivity mMain;

        public HideTask(MainActivity main) {
            mMain = main;
        }
        public void run() {
            mTimer.cancel(); //Terminate the timer thread

            // Run the remove method on the UI thread
            if (mMain != null)
                mMain.runOnUiThread(new Runnable() {
                public void run() {
                    removeProgressViews(mMain);
                }
            });
        }
    }
}
