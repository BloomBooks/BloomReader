package org.sil.bloom.reader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Created by rick on 11/2/17.
 */

public class ConnectivityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Funny story: we don't actually have to do anything here.
        // Receiving this broadcast also calls Application.onCreate
        // if the app wasn't running in the bg already
    }
}
