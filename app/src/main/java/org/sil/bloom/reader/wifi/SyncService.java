package org.sil.bloom.reader.wifi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;

// Service that runs a simple 'web server' that Bloom desktop can talk to.
// This is probably overkill for simply allowing the desktop to send one file per book to the device.
// But (a) code was available to reuse; and (b) copying one more file from HTA will allow this
// service to also support getting files, which is likely to be helpful for peer-to-peer sharing.
public class SyncService extends Service {
    public SyncService() {
    }

    SyncServer _server;
    WifiManager.WifiLock _lock;
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // acquiring this lock may help prevent the OS deciding to shut down the WiFi system
        // while we are are transferring. It won't prevent the user restarting, turning off WiFi,
        // switching to airplane mode, etc.
        // Note that this service is started when we request a book and stopped when we get it,
        // so WiFi should not remain locked indefinitely.
        // Enhance: possibly we should do something to prevent WiFi staying locked if the
        // transfer is interrupted?
        _lock = ((WifiManager)this.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).createWifiLock("file transfer lock");
        _lock.acquire();

        _server = new SyncServer(this);
    }

    @Override
    public void onDestroy() {
        _server.stopThread();
        _lock.release();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        _server.startThread();
        return START_STICKY;
    }
}
