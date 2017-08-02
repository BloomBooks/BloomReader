package org.sil.bloom.reader.WiFi;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

// Service that runs a simple 'web server' that Bloom desktop can talk to.
// This is probably overkill for simply allowing the desktop to send one file per book to the device.
// But (a) code was available to reuse; and (b) copying one more file from HTA will allow this
// service to also support getting files, which is likely to be helpful for peer-to-peer sharing.
public class SyncService extends Service {
    public SyncService() {
    }

    SyncServer _server;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        _server = new SyncServer(this);
    }

    @Override
    public void onDestroy() {
        _server.stopThread();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        _server.startThread();
        return START_STICKY;
    }
}
