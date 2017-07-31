package org.sil.bloom.reader.WiFi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import org.sil.bloom.reader.R;

public class GetFromWiFiActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_from_wi_fi);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setTitle(""); // remove default activity name, we have bloom image

        LocalBroadcastManager.getInstance(this).registerReceiver(new ProgressReceiver(),
                new IntentFilter(NewBookListenerService.BROADCAST_BOOK_LISTENER_PROGRESS));
    }

    public static void sendProgressMessage(Context context, String message) {
        Intent progressIntent = new Intent(NewBookListenerService.BROADCAST_BOOK_LISTENER_PROGRESS)
                .putExtra(NewBookListenerService.BROADCAST_BOOK_LISTENER_PROGRESS_CONTENT, message);
        LocalBroadcastManager.getInstance(context).sendBroadcast(progressIntent);
    }

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
        stopBookListener();
    }


    public String getWifiName(Context context) {
        WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
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
