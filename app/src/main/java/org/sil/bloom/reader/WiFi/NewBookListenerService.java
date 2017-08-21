package org.sil.bloom.reader.WiFi;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.R;
import org.sil.bloom.reader.models.Book;
import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Thomson on 7/22/2017.
 * This class listens for a computer on the local network running Bloom to advertise a book as
 * available for download. When one is published, it gets it.
 * Based on ideas from https://gist.github.com/finnjohnsen/3654994
 */

public class NewBookListenerService extends Service {
    // Small files with this extension are created to store the file version information
    // sent with each advertisement. This allows BloomReader to avoid repeatedly downloading
    // the same version of the same book. BloomReader does not interpret the version information,
    // just compares what it got originally with the new advertisement.
    public static final String VERSION_EXTENSION = ".version";
    DatagramSocket socket;
    Thread UDPBroadcastThread;
    private Boolean shouldRestartSocketListen=true;

    // port on which the desktop is listening for our book request.
    // Must match Bloom Desktop UDPListener._portToListen.
    // Must be different from ports in NewBookListenerService.startListenForUDPBroadcast
    // and SyncServer._serverPort.
    int desktopPort = 5915;
    String newBookVersion;
    File versionFile;
    boolean gettingBook = false;
    private Set<String> _announcedBooks = new HashSet<String>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void listen(Integer port) throws Exception {
        byte[] recvBuf = new byte[15000];
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        }

        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        //Log.e("UDP", "Waiting for UDP broadcast");
        socket.receive(packet);
        if (gettingBook)
            return; // ignore new advertisements while downloading. Will receive again later.
        String senderIP = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        try {
            JSONObject data = new JSONObject(message);
            String title = data.getString("Title");
            newBookVersion = data.getString("Version");
            String sender = "unknown";
            try {
                sender = data.getString("Sender");
            } catch(JSONException e) {
                e.printStackTrace();
            }
            File localBookDirectory = BookCollection.getLocalBooksDirectory();
            File bookFile = new File(localBookDirectory, title + Book.BOOK_FILE_EXTENSION);
            boolean bookExists = bookFile.exists();
            versionFile = new File(localBookDirectory, title + VERSION_EXTENSION);
            // It is pathological that the book doesn't exist but the version file is up to date.
            // But it easily happens with manual testers wanting to redo transmission.
            // It could possibly happen with end users messing with their file system.
            // So if we don't actually have the book we will re-request it.
            if (bookExists && IsCurrentBookUpToDate()) {
                // Enhance: possibly we might want to announce this again if the book has been off the air
                // for a while? So a user doesn't see "nothing happening" if he thinks he just started
                // publishing it, but somehow BR has seen it recently? Thought about just keeping
                // the most recent name, so we'd report a different one even if it had been advertised
                // recently. But there could be two advertisers on the network, which could lead to
                // alternating advertisements. Another idea: find a way to only keep track of, say,
                // books advertised in the last few seconds. Since books are normally advertised
                // every second, a book we haven't seen for even 5 seconds is probably interesting
                // enough to announce again. One way would be, every 5 seconds we copy the current
                // set to an 'old' set and clear current. Then when we see a book, we skip announcing if it is in
                // either set. But only add it to the new one. Then, after 5-10 seconds of not seeing
                // an add, a book would drop out of both. Another approach would be a dictionary
                // mapping title to last-advertised-time, and if > 5s ago announce again.
                if (!_announcedBooks.contains(title)) {
                    GetFromWiFiActivity.sendProgressMessage(this, String.format(getString(R.string.already_have_version), title) + "\n\n");
                }
            }
            else {
                if (bookExists)
                    GetFromWiFiActivity.sendProgressMessage(this, String.format(getString(R.string.found_new_version), title, sender) + "\n");
                else
                    GetFromWiFiActivity.sendProgressMessage(this, String.format(getString(R.string.found_file), title, sender) + "\n");
                getBook(senderIP, title);
            }
            // Whether we just got it or just said we already have it, we don't need to keep announcing
            // that we have it.
            _announcedBooks.add(title);

        } catch (JSONException e) {
            // This can stay in production. Just ignore any broadcast packet that doesn't have
            // the data we expect.
            e.printStackTrace();
        }
        socket.close();
    }

    // Private class to handle receiving notification from AcceptNotificationHandler.
    // I can't figure out how to make an anonymous class which can keep a reference to itself
    // for use in removing itself later. The notification is sent when the transfer of a book
    // is complete.
    class EndOfTransferListener implements AcceptNotificationHandler.NotificationListener {

        NewBookListenerService _parent;
        public EndOfTransferListener(NewBookListenerService parent) {
            _parent = parent;
        }

        @Override
        public void onNotification(String message) {
            AcceptNotificationHandler.removeNotificationListener(this);
            _parent.transferComplete();
        }
    }

    private void getBook(String sourceIP, String title) {
        gettingBook = true; // don't start more receives until we deal with this.
        AcceptNotificationHandler.addNotificationListener(new EndOfTransferListener(this));
        // This server will be sent the actual book data (and the final notification)
        startSyncServer();
        // Send one package to the desktop to request the book. Its contents tell the desktop
        // what IP address to use.
        SendMessage sendMessageTask = new SendMessage();
        sendMessageTask.desktopIpAddress = sourceIP;
        sendMessageTask.ourIpAddress = getOurIpAddress();
        sendMessageTask.ourDeviceName = getOurDeviceName();
        sendMessageTask.execute();
    }

    // It's slightly odd to use the Bluetooth name for a WiFi function, but it's the only
    // generally-available user-configurable device name we can find. (Some devices...e.g.,
    // my Note 4...have a setting for a more general device name, but others (e.g., Nexus)
    // do not.)
    private String getOurDeviceName() {
        BluetoothAdapter myDevice = BluetoothAdapter.getDefaultAdapter();
        return myDevice.getName();
    }

    private void startSyncServer() {
        Intent serviceIntent = new Intent(this, SyncService.class);
        startService(serviceIntent);
    }

    // Called via EndOfTransferListener when desktop sends transfer complete notification.
    private void transferComplete() {
        // We can stop listening for file transfers and notifications from the desktop.
        Intent serviceIntent = new Intent(this, SyncService.class);
        stopService(serviceIntent);

        byte[] versionInfo=new byte[0];
        try {
            versionInfo = newBookVersion.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(); // not going to happen but Java demands we catch it.
        }
        try {
            FileOutputStream fos = new FileOutputStream(versionFile);
            fos.write(versionInfo);
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        gettingBook = false;
        GetFromWiFiActivity.sendProgressMessage(this, getString(R.string.done) + "\n\n");
    }

    // Get the IP address of this device (on the WiFi network) to transmit to the desktop.
    private String getOurIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }

    // Determine whether the book is up to date, based on the versionFile information
    // saved in a member variable when we got the advertisement.
    boolean IsCurrentBookUpToDate() {
        if (!versionFile.isFile())
            return false;
        try {
            FileInputStream fis = new FileInputStream(versionFile);
            byte[] data = new byte[(int)versionFile.length()];
            fis.read(data);
            fis.close();
            String oldSha = new String(data, "UTF-8");
            return oldSha.equals(newBookVersion); // not ==, they are different objects.
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static final String BROADCAST_BOOK_LISTENER_PROGRESS = "org.sil.bloomreader.booklistener.progress";
    public static final String BROADCAST_BOOK_LISTENER_PROGRESS_CONTENT = "org.sil.bloomreader.booklistener.progress.content";

    void startListenForUDPBroadcast() {
        UDPBroadcastThread = new Thread(new Runnable() {
            public void run() {
                try {
                    Integer port = 5913; // Must match port in Bloom class WiFiAdvertiser
                    while (shouldRestartSocketListen) { //
                        listen(port);
                    }
                    //if (!shouldListenForUDPBroadcast) throw new ThreadDeath();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        UDPBroadcastThread.start();
    }

    void stopListen() {
        shouldRestartSocketListen = false;
        socket.close();
    }

    @Override
    public void onDestroy() {
        stopListen();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldRestartSocketListen = true;
        startListenForUDPBroadcast();
        //Log.i("UDP", "Service started");
        return START_STICKY;
    }

    // This class is responsible to send one message packet to the IP address we
    // obtained from the desktop, containing the Android's own IP address.
    private class SendMessage extends AsyncTask<Void, Void, Void> {

        public String ourIpAddress;
        public String desktopIpAddress;
        public String ourDeviceName;
        @Override
        protected Void doInBackground(Void... params) {
            try {
                InetAddress receiverAddress = InetAddress.getByName(desktopIpAddress);
                DatagramSocket socket = new DatagramSocket();
                JSONObject data = new JSONObject();
                try {
                    // names used here must match those in Bloom WiFiAdvertiser.Start(),
                    // in the event handler for _wifiListener.NewMessageReceived.
                    data.put("deviceAddress", ourIpAddress);
                    data.put("deviceName", ourDeviceName);
                } catch (JSONException e) {
                    // How could these fail?? But compiler demands we catch this.
                    e.printStackTrace();
                }
                byte[] buffer = data.toString().getBytes("UTF-8");
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, desktopPort);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
