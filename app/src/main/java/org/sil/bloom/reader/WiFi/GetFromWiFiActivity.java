package org.sil.bloom.reader.WiFi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.sil.bloom.reader.BaseActivity;
import org.sil.bloom.reader.MainActivity;
import org.sil.bloom.reader.R;

import java.util.ArrayList;

// An activity that is made to look like a dialog (see the theme associated with it in
// the main manifest and defined in styles.xml) and which implements the command to receive
// Bloom books from Wifi (i.e., from a desktop running Bloom...eventually possibly from
// another copy of BloomReader). This is launched from a menu option in the main activity.
public class GetFromWiFiActivity extends BaseActivity {

    ArrayList<String> newBookPaths = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_from_wi_fi);

        LocalBroadcastManager.getInstance(this).registerReceiver(new ProgressReceiver(),
                new IntentFilter(NewBookListenerService.BROADCAST_BOOK_LISTENER_PROGRESS));

        final Button okButton = (Button)findViewById(R.id.wifiOk);
        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // inform the main activity of the books we added.
        Intent result = new Intent();
        result.putExtra(MainActivity.NEW_BOOKS, newBookPaths.toArray(new String[0]));
        setResult(RESULT_OK, result);
        finish();
    }

    // This is used by various companion classes that want to display stuff in our progress window.
    public static void sendProgressMessage(Context context, String message) {
        Intent progressIntent = new Intent(NewBookListenerService.BROADCAST_BOOK_LISTENER_PROGRESS)
                .putExtra(NewBookListenerService.BROADCAST_BOOK_LISTENER_PROGRESS_CONTENT, message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(progressIntent);
    }

    // This class supports receiving the messages sent by calls to sendProgressMessage()
    private class ProgressReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            TextView progressView = (TextView) findViewById(R.id.wifi_progress);
            progressView.append(intent.getStringExtra(NewBookListenerService.BROADCAST_BOOK_LISTENER_PROGRESS_CONTENT));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String wifiName = getWifiName(this);
        if (wifiName == null)
        {
            GetFromWiFiActivity.sendProgressMessage(this, getString(R.string.no_wifi_connected) + "\n\n");
        }
        else {
            // For some reason the name of the ILC network comes with quotes already around it.
            // Since we want one lot of quotes but not two, decided to add them if missing.
            if (!wifiName.startsWith("\""))
                wifiName = "\"" + wifiName;
            if (!wifiName.endsWith("\""))
                wifiName = wifiName + "\"";
            GetFromWiFiActivity.sendProgressMessage(this, String.format(getString(R.string.looking_for_adds), wifiName) + "\n\n");

            startBookListener();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Enhance: do we want to do something to allow an in-progress transfer to complete?
        stopBookListener();
    }

    @Override
    protected void onNewOrUpdatedBook(String fullPath) {
        newBookPaths.add(fullPath);
        playSoundFile(R.raw.bell);
    }

    // Get the human-readable name of the WiFi network that the Android is connected to
    // (or null if not connected over WiFi).
    public String getWifiName(Context context) {
        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (manager.isWifiEnabled()) {
            WifiInfo wifiInfo = manager.getConnectionInfo();
            if (wifiInfo != null) {
                NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState());
                if (state == NetworkInfo.DetailedState.CONNECTED || state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {
                    return wifiInfo.getSSID();
                }
            }
        }
        return null;
    }

    private void startBookListener() {
        Intent serviceIntent = new Intent(this, NewBookListenerService.class);
        startService(serviceIntent);
    }

    private void stopBookListener() {
        Intent serviceIntent = new Intent(this, NewBookListenerService.class);
        stopService(serviceIntent);
    }
}
