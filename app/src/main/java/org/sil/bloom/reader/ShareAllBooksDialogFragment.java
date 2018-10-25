package org.sil.bloom.reader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/*
    Dialog for confirming that the user wants to share their whole library
    and displaying a spinner while we bundle it for them
 */

public class ShareAllBooksDialogFragment extends ShareBooksDialogFragment {
    public static final String SHARE_BOOKS_DIALOG_FRAGMENT_TAG = "share_books_dialog";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View layout = inflater.inflate(R.layout.dialog_share_books, container, false);
        layout.findViewById(R.id.spinner).setVisibility(View.GONE);

        Button btnShareBooks = layout.findViewById(R.id.btnShareBooks);
        btnShareBooks.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onClickShare(layout);
            }
        });

        Button btnCancel = layout.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getDialog().cancel();
            }
        });

        getDialog().setCanceledOnTouchOutside(true);

        return layout;
    }

    private void onClickShare(View layout) {
        bundleTask = new SharingManager(getActivity()).shareAllBooksAndShelves(this);
        layout.findViewById(R.id.btnShareBooks).setVisibility(View.GONE);
        layout.findViewById(R.id.spinner).setVisibility(View.VISIBLE);
    }
}
