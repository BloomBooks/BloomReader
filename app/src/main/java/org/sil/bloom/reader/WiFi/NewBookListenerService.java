package org.sil.bloom.reader.WiFi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.BaseActivity;
import org.sil.bloom.reader.IOUtilities;
import org.sil.bloom.reader.MainActivity;
import org.sil.bloom.reader.R;
import org.sil.bloom.reader.models.BookOrShelf;
import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static org.sil.bloom.reader.BloomReaderApplication.getOurDeviceName;


/**
 * Created by Thomson on 7/22/2017.
 * This class listens for a computer on the local network running Bloom to advertise a book as
 * available for download. When one is published, it gets it.
 * Based on ideas from https://gist.github.com/finnjohnsen/3654994
 */

public class NewBookListenerService extends Service {
    DatagramSocket socket;
    Thread UDPBroadcastThread;
    private Boolean shouldRestartSocketListen=true;

    // port on which the desktop is listening for our book request.
    // Must match Bloom Desktop UDPListener._portToListen.
    // Must be different from ports in NewBookListenerService.startListenForUDPBroadcast
    // and SyncServer._serverPort.
    int desktopPort = 5915;
    boolean gettingBook = false;
    boolean httpServiceRunning = false;
    int addsToSkipBeforeRetry;
    boolean reportedVersionProblem = false;
    private Set<String> _announcedBooks = new HashSet<String>();
    WifiManager.MulticastLock multicastLock;

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

        // This seems to have become necessary for receiving a packet around Android 8.
        WifiManager wifi;
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        multicastLock = wifi.createMulticastLock("lock");
        multicastLock.acquire();

        try {
            DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
            //Log.e("UDP", "Waiting for UDP broadcast");
            socket.receive(packet);
            if (gettingBook)
                return; // ignore new advertisements while downloading. Will receive again later.
            if (addsToSkipBeforeRetry > 0) {
                // We ignore a few adds after requesting a book before we (hopefully) start receiving.
                addsToSkipBeforeRetry--;
                return;
            }
            String senderIP = packet.getAddress().getHostAddress();
            String message = new String(packet.getData()).trim();
            JSONObject data = new JSONObject(message);
            String title = data.getString("title");
            String newBookVersion = data.getString("version");
            String sender = "unknown";
            String protocolVersion = "0.0";
            try {
                protocolVersion = data.getString("protocolVersion");
                sender = data.getString("sender");
            } catch(JSONException e) {
                e.printStackTrace();
            }
            float version = Float.parseFloat(protocolVersion);
            if (version <  2.0f) {
                if (!reportedVersionProblem) {
                    GetFromWiFiActivity.sendProgressMessage(this, "You need a newer version of Bloom editor to exchange data with this BloomReader\n");
                    reportedVersionProblem = true;
                }
                return;
            } else if (version >= 3.0f) {
                // Desktop currently uses 2.0 exactly; the plan is that non-breaking changes
                // will tweak the minor version number, breaking will change the major.
                if (!reportedVersionProblem) {
                    GetFromWiFiActivity.sendProgressMessage(this, "You need a newer version of BloomReader to exchange data with this sender\n");
                    reportedVersionProblem = true;
                }
                return;
            }
            File localBookDirectory = BookCollection.getLocalBooksDirectory();
            File bookFile = new File(localBookDirectory, title + IOUtilities.BOOK_FILE_EXTENSION);
            boolean bookExists = bookFile.exists();
            // If the book doesn't exist it can't be up to date.
            if (bookExists && IsBookUpToDate(bookFile, title, newBookVersion)) {
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
                    _announcedBooks.add(title); // don't keep saying this.
                }
            }
            else {
                if (bookExists)
                    GetFromWiFiActivity.sendProgressMessage(this, String.format(getString(R.string.found_new_version), title, sender) + "\n");
                else
                    GetFromWiFiActivity.sendProgressMessage(this, String.format(getString(R.string.found_file), title, sender) + "\n");
                // It can take a few seconds for the transfer to get going. We won't ask for this again unless
                // we don't start getting it in a reasonable time.
                addsToSkipBeforeRetry = 3;
                getBook(senderIP, title);
            }
        } catch (JSONException e) {
            // This can stay in production. Just ignore any broadcast packet that doesn't have
            // the data we expect.
            e.printStackTrace();
        }
        finally {
            socket.close();
            multicastLock.release();
        }
    }

    // Private class to handle receiving notification from AcceptFileHandler.
    // I can't figure out how to make an anonymous class which can keep a reference to itself
    // for use in removing itself later. The notification is sent when the transfer of a book
    // is complete.
    class EndOfTransferListener implements AcceptFileHandler.IFileReceivedNotification {

        NewBookListenerService _parent;
        String _title;
        public EndOfTransferListener(NewBookListenerService parent, String title) {
            _parent = parent;
            _title = title;
        }

        @Override
        public void receivingFile(String name) {
            // Once the receive actually starts, don't start more receives until we deal with this.
            // If our request for the book didn't produce a response, we'll ask again when we get
            // the next notification.
            gettingBook = true;
        }

        @Override
        public void receivedFile(String name, boolean success) {
            _parent.transferComplete(success);
            if (success) {
                // We won't announce subsequent up-to-date advertisements for this book.
                _announcedBooks.add(_title);
            }
        }
    }

    private void getBook(String sourceIP, String title) {
        AcceptFileHandler.requestFileReceivedNotification(new EndOfTransferListener(this, title));
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

    private void startSyncServer() {
        if (httpServiceRunning)
            return;
        Intent serviceIntent = new Intent(this, SyncService.class);
        startService(serviceIntent);
        httpServiceRunning = true;
    }

    private void stopSyncServer() {
        if (!httpServiceRunning)
            return;
        Intent serviceIntent = new Intent(this, SyncService.class);
        stopService(serviceIntent);
        httpServiceRunning = false;
    }

    // Called via EndOfTransferListener when desktop sends transfer complete notification.
    private void transferComplete(boolean success) {
        // We can stop listening for file transfers and notifications from the desktop.
        stopSyncServer();
        gettingBook = false;

        final int resultId = success ? R.string.done : R.string.transferFailed;
        GetFromWiFiActivity.sendProgressMessage(this, getString(resultId) + "\n\n");

        BaseActivity.playSoundFile(R.raw.bookarrival);
        // We already played a sound for this file, don't need to play another when we resume
        // the main activity and notice the new file.
        MainActivity.skipNextNewFileSound();
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

    // Determine whether the book is up to date, based on comparing the version file embedded in it
    // with the one we got from the advertisement.
    // A small file called version.txt is embedded in each .bloomd file to store the file version information
    // sent with each advertisement. This allows BloomReader to avoid repeatedly downloading
    // the same version of the same book. BloomReader does not interpret the version information,
    // just compares what is in the  version.txt in the .bloomd file it has (if any) with what it
    // got in the new advertisement.
    boolean IsBookUpToDate(File bookFile, String title, String newBookVersion) {
        // "version.txt" must match the name given in Bloom Desktop BookCompressor.CompressDirectory()
        byte[] oldShaBytes = IOUtilities.ExtractZipEntry(bookFile, "version.txt");
        if (oldShaBytes == null)
            return false;
        String oldSha = "";
        try {
            oldSha = new String(oldShaBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return oldSha.equals(newBookVersion); // not ==, they are different objects.
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
        if (socket != null)
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
