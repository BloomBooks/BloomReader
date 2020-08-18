package org.sil.bloom.reader.wifi;

import android.content.Context;
import android.net.Uri;

import org.sil.bloom.reader.R;
import org.sil.bloom.reader.models.BookCollection;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpEntityEnclosingRequest;
import cz.msebera.android.httpclient.HttpException;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.protocol.HttpContext;
import cz.msebera.android.httpclient.protocol.HttpRequestHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles requests with urls like http://[ipaddress]:5914/putfile?path=bookTitle.bloomd
 * to write a file containing the data transmitted to a file in the local Bloom directory.
 * This is configured as a request handler in SyncServer.
 * Slightly adapted from a similar file in HearThis Android
 */
public class AcceptFileHandler implements HttpRequestHandler {
    Context _parent;
    public AcceptFileHandler(Context parent)
    {
        _parent = parent;
    }
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws HttpException, IOException {
        GetFromWiFiActivity.sendProgressMessage(_parent, _parent.getString(R.string.downloading) + "\n");
        File baseDir = BookCollection.getLocalBooksDirectory();
        Uri uri = Uri.parse(request.getRequestLine().getUri());
        String filePath = uri.getQueryParameter("path");

        if (listener != null)
            listener.receivingFile(filePath);
        String path = baseDir  + "/" + filePath;
        HttpEntity entity = null;
        String result = "failure";
        if (request instanceof HttpEntityEnclosingRequest)
            entity = ((HttpEntityEnclosingRequest)request).getEntity();
        if (entity != null) {
            try {
                final InputStream input = entity.getContent();
                final byte[] buffer = new byte[4096];
                File file = new File(path);
                File dir = file.getParentFile();
                if (!dir.exists())
                    dir.mkdirs();
                boolean aborted = false;
                FileOutputStream fs = new FileOutputStream(file);
                try {
                    int bytesRead = 1; // to make first cycle go ahead
                    // We want to copy the input from WiFi to the output file.
                    // We'd like to recover if the transmission is interrupted.
                    // Ideally we'd just put a timeout on the socket that is reading the data,
                    // but I can't find any way to access it.
                    // Nor does it work to expect that an interrupted transmission will result
                    // in a smaller number of bytes being available and successfully reading
                    // however many there are, even zero. Even though we are only asking the
                    // stream for (at most) the number of bytes it says are available, the
                    // read can block forever if the connection is broken suddenly.
                    // The technique actually used here, with thanks to one of the answers on
                    // https://stackoverflow.com/questions/804951/is-it-possible-to-read-from-a-inputstream-with-a-timeout,
                    // runs the read on (yet another) helper thread, forcing a timeout if we don't get some data
                    // within a second.
                    ExecutorService executor = Executors.newFixedThreadPool(2);
                    Callable<Integer> readTask = new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            return input.read(buffer, 0, Math.min(input.available(), buffer.length));
                        }
                    };
                    while (bytesRead >= 0) {
                        // This is a trick to get an exception thrown if it takes more than a second to
                        // read a block of data.
                        try {
                            Future<Integer> future = executor.submit(readTask);
                            bytesRead = future.get(1000, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException e) {
                            // This also blocks. This should be rare, I think we can afford to
                            // leave the thread stuck.
                            //input.close(); // should clean up the thread attempting the read
                            aborted = true;
                            break;
                        }

                        if (bytesRead > 0) {
                            fs.write(buffer, 0, bytesRead);
                        }
                    }
                } catch (Exception e) {
                    // something unexpected went wrong while writing the output
                    e.printStackTrace();
                    aborted = true;
                }
                fs.close();
                if (aborted) {
                    file.delete(); // incomplete, useless, may cause exceptions trying to unzip.
                } else {
                    result = "success"; // normal completion.
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        response.setEntity(new StringEntity(result));
        if (listener != null)
            listener.receivedFile(path, result == "success");
    }

    public interface IFileReceivedNotification {
        void receivingFile(String name);
        void receivedFile(String name, boolean success);
    }

    static IFileReceivedNotification listener;
    public static void requestFileReceivedNotification(IFileReceivedNotification newListener) {
        listener = newListener; // We only support notifying the most recent for now.
    }
}
