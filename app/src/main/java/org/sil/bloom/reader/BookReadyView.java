package org.sil.bloom.reader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.File;

// This class implements the notification at the bottom of BloomLibraryActivity (or MainActivity)
// saying that a book (or books) have been downloaded and offering to show them.
public class BookReadyView extends ConstraintLayout {

    private Button mReadNowButton;
    private TextView mMessage;
    // Initially null, if it is set to a book path we offer to open that book, if it is an empty
    // string that signifies that multiple books have been downloaded and we just offer to go to
    // the main activity to view them.
    private String mBookPath;
    private Context mParent;
    private DownloadsView mDownloadsView;

    public BookReadyView(@NonNull Context context, DownloadsView downloadsView) {
        super(context);
        mParent = context;
        mDownloadsView = downloadsView;
        initializeViews(context);
    }

    private void initializeViews(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.book_ready_layout, this);

        mReadNowButton = (Button)this
                .findViewById(R.id.book_ready_read_now);
        mReadNowButton.setOnClickListener(new OnClickListener() {
            public void onClick(View view) {
                    mDownloadsView.viewBooks(mBookPath);
            }
        });
        mMessage = (TextView) this.findViewById(R.id.book_ready_message);
        updateMessage(); // possibly we got the book path before we finished inflating.
    }

    public void setBook(String path) {
        mBookPath = path;
        updateMessage(); // seems to do nothing initially, but we may change the message later.
    }

    private void updateMessage() {
        if (this.mBookPath == null || mMessage == null) {
            return;
        }
        String label = getContext().getString(mBookPath == "" ? R.string.downloads_are_complete : R.string.download_is_complete);
        mMessage.setText(label);
        String buttonLabel = getContext().getString(mBookPath == "" ? R.string.view_books : R.string.read_now);
        mReadNowButton.setText(buttonLabel);
    }
}
