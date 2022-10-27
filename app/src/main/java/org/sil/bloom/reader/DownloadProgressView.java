package org.sil.bloom.reader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.File;

// This class displays a progress bar at the bottom of BloomLibraryActivity (or MainActivity)
// and supports canceling the download(s) in progress.
public class DownloadProgressView extends ConstraintLayout {

    private Button mCloseButton;
    private TextView mMessage;
    private String mBookPath;
    private Context mParent;
    ProgressBar mProgress;
    long mDownloadId;
    DownloadsView mDownloadsView;

    public DownloadProgressView(@NonNull Context context, DownloadsView downloadsView, long downloadId) {
        super(context);
        mParent = context;
        mDownloadId = downloadId;
        initializeViews(context);
        mDownloadsView = downloadsView;
    }

    private void initializeViews(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.download_progress_layout, this);

        mCloseButton = (Button) this
                .findViewById(R.id.download_close);
        mCloseButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                mDownloadsView.cancelDownloads();
            }
        });
        mProgress = findViewById(R.id.download_progressBar);
        mMessage = findViewById(R.id.book_ready_message);
        updateMessage(); // possibly we got the book path before we finished inflating.
    }

    public void setBook(String path) {
        this.mBookPath = path;
        updateMessage();
    }

    public String getBook() {
        return mBookPath;
    }

    public static String titleFromName(String name) {
        // Filenames from BL commonly contain plus signs for spaces.
        // Nearly always things will be more readable if we replace them.
        // A sequence of three plus signs might indicate that the name really had a plus sign.
        // But it might equally indicate a sequence of undesirable characters that each got
        // changed to a space to make a file name. (We had some code briefly to treat three
        // plus signs specially, but got bad results for an Adangbe book called "Bɔ++++kuu.bloompub".)
        String result = name.replace("+", " ");
        // The above might just possibly have produced a sequence of several spaces.
        while (result.indexOf("  ") >= 0)
            result = result.replace("  ", " ");
        // We don't need a file extension in the name.
        result = result.replace(".bloompub", "").replace(".bloomd", "");
        return result;
    }

    private void updateMessage() {
        if (this.mBookPath == null || mMessage == null) {
            return;
        }

        if (mBookPath == "") {
            // special case: multiple books
            mMessage.setText(R.string.downloading_books);
            return;
        }
        String name = new File(this.mBookPath).getName();
        String template = getContext().getString(R.string.downloading_file);

        String label = String.format(template, titleFromName(name));
        mMessage.setText(label);
    }

    // 0-100
    public void setProgress(int progress) {
        if (mProgress == null)
            return;
        mProgress.setProgress(progress);
    }
}
