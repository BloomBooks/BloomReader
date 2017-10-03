package org.sil.bloom.reader;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * Created by rick on 10/2/17.
 * Much of it borrowed from the ShareDialogFragment for Scripture/ReadingAppBuilder
 */

public class ShareDialogFragment extends DialogFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_share, container, false);

        Button btnShareLink = (Button) view.findViewById(R.id.btnShareLink);
        btnShareLink.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new SharingManager(getActivity()).shareLinkToAppOnGooglePlay();
                dismiss();
            }
        });

        Button btnShareApk = (Button) view.findViewById(R.id.btnShareApkFile);
        btnShareApk.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new SharingManager(getActivity()).shareApkFile();
                dismiss();
            }
        });

        Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismiss();
            }
        });
        btnCancel.setTextColor(getResources().getColor(R.color.accent_material_light));

        getDialog().setCanceledOnTouchOutside(isCanceledOnTouchOutside());

        return view;
    }

    protected boolean isCanceledOnTouchOutside() {
        return true;
    }

    protected void onCloseDialog() {
        dismiss();
    }
}
