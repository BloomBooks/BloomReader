package org.sil.bloom.reader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.sil.bloom.reader.models.BookOrShelf;

import java.util.List;

/*
    Dialog for displaying a spinner while we bundle up the shelf for sharing
 */

public class ShareShelfDialogFragment extends ShareBooksDialogFragment {
    public static final String FRAGMENT_TAG = "share_shelf_dialog";
    private List<BookOrShelf> booksAndShelves;

    public void setBooksAndShelves(List<BookOrShelf> booksAndShelves) {
        this.booksAndShelves = booksAndShelves;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shareBooksAndShelves();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.dialog_share_books, container, false);
        layout.findViewById(R.id.txtShareBooksWithFriend).setVisibility(View.GONE);
        layout.findViewById(R.id.btnShareBooks).setVisibility(View.GONE);
        Button btnCancel = layout.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getDialog().cancel();
            }
        });
        return layout;
    }

    private void shareBooksAndShelves() {
        bundleTask = new SharingManager(getActivity()).shareShelf(booksAndShelves, this);
    }
}
