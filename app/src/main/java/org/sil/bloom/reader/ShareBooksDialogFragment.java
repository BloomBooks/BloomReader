package org.sil.bloom.reader;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

// Dialog for confirmation of sharing (all) books with another device.
public class ShareBooksDialogFragment extends DialogFragment {
    public static final String SHARE_BOOKS_DIALOG_FRAGMENT_TAG = "share_books_dialog";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_share_books, container, false);

        Button btnShareBooks = (Button) view.findViewById(R.id.btnShareBooks);
        btnShareBooks.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new SharingManager(getActivity()).shareBooks();
                dismiss();
            }
        });

        Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismiss();
            }
        });

        getDialog().setCanceledOnTouchOutside(true);

        return view;
    }
}
