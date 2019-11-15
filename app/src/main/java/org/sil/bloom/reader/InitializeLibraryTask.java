package org.sil.bloom.reader;

import android.os.AsyncTask;
import android.view.View;
import android.widget.LinearLayout;

import org.sil.bloom.reader.models.ExtStorageUnavailableException;

// This task is in charge of loading up the book collection asynchronously.  When there are a large
// number of books, especially in the external folder, startup can be rather slow before everything
// is ready to display.  This allows the overall framework of the main display to come up quickly
// with a progress bar at the bottom that shows progress working through the list of books.
// See https://issues.bloomlibrary.org/youtrack/issue/BL-7432.
public class InitializeLibraryTask extends AsyncTask<MainActivity, Void, Void> {
    private ExtStorageUnavailableException mExceptionCaught = null;
    private MainActivity mMain = null;

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
        mMain.mLoadingProgressBar.setVisibility(View.INVISIBLE);
        // Remove ProgressBar from main content LinearLayout
        LinearLayout linearLayout = mMain.findViewById(R.id.content_main);
        if (linearLayout != null) {
            linearLayout.removeView(mMain.mLoadingProgressBar);
            mMain.mLoadingProgressBar = null;
        }
        // Ensure all the books are displayed at the end of loading.
        mMain.mBookListAdapter.notifyDataSetChanged();
        mMain = null;   // release reference

        if (mExceptionCaught != null) {
            mMain.externalStorageUnavailable(mExceptionCaught);
        }
    }
}
