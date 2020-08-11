package org.sil.bloom.reader;

import androidx.fragment.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import java.io.File;

/*
    Superclass that holds the shared logic for ShareAllBooksDialogFragment and
    ShareShelfDialogFragment.
    Both these dialogs kick off a BundleTask that should be cancelled if the dialog
    is cancelled. When the task is done, the dialog needs to be dismissed.
 */

public abstract class ShareBooksDialogFragment extends DialogFragment implements BundleTask.BundleTaskDoneListener {
    protected BundleTask bundleTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (bundleTask != null)
            bundleTask.cancel(false);
    }

    public void onBundleTaskDone(File bundleFile) {
        dismiss();
    }
}
