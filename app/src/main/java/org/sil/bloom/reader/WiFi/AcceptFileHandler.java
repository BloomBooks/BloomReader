package org.sil.bloom.reader.WiFi;

import android.content.Context;
import android.net.Uri;

import org.sil.bloom.reader.models.BookCollection;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpEntityEnclosingRequest;
import cz.msebera.android.httpclient.HttpException;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.entity.FileEntity;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.protocol.HttpContext;
import cz.msebera.android.httpclient.protocol.HttpRequestHandler;
import cz.msebera.android.httpclient.util.EntityUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Handles requests with urls like http://[ipaddress]:5914/putfile?path=bookTitle.bloomd
 * to write a file containing the data transmitted to a file in the local Bloom directory.
 * This is configured as a request handler in SyncServer.
 * Slightly adapted from a similar file in HearThis Android
 */
public class AcceptFileHandler implements HttpRequestHandler {
    public AcceptFileHandler()
    {}
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext httpContext) throws HttpException, IOException {
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
                byte[] data = EntityUtils.toByteArray(entity);
                File file = new File(path);
                File dir = file.getParentFile();
                if (!dir.exists())
                    dir.mkdirs();
                FileOutputStream fs = new FileOutputStream(file);
                fs.write(data);
                fs.close();
                result = "success";
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        response.setEntity(new StringEntity(result));
    }

    public interface IFileReceivedNotification {
        void receivingFile(String name);
    }

    static IFileReceivedNotification listener;
    public static void requestFileReceivedNotification(IFileReceivedNotification newListener) {
        listener = newListener; // We only support notifying the most recent for now.
    }
}
