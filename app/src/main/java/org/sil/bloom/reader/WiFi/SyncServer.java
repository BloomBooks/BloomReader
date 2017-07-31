package org.sil.bloom.reader.WiFi;

import android.content.Context;

import cz.msebera.android.httpclient.HttpException;
import cz.msebera.android.httpclient.HttpRequest;
import cz.msebera.android.httpclient.impl.DefaultBHttpServerConnection;
import cz.msebera.android.httpclient.impl.DefaultConnectionReuseStrategy;
import cz.msebera.android.httpclient.impl.DefaultHttpResponseFactory;
import cz.msebera.android.httpclient.impl.DefaultHttpServerConnection;
import cz.msebera.android.httpclient.params.BasicHttpParams;
import cz.msebera.android.httpclient.protocol.BasicHttpContext;
import cz.msebera.android.httpclient.protocol.BasicHttpProcessor;
import cz.msebera.android.httpclient.protocol.HttpRequestHandler;
import cz.msebera.android.httpclient.protocol.HttpRequestHandlerMapper;
import cz.msebera.android.httpclient.protocol.HttpRequestHandlerRegistry;
import cz.msebera.android.httpclient.protocol.HttpService;
import cz.msebera.android.httpclient.protocol.ImmutableHttpProcessor;
import cz.msebera.android.httpclient.protocol.ResponseConnControl;
import cz.msebera.android.httpclient.protocol.ResponseContent;
import cz.msebera.android.httpclient.protocol.ResponseDate;
import cz.msebera.android.httpclient.protocol.ResponseServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * SyncServer manages the 'web server' for the service that supports receiving data
 * from Bloom desktop.
 * It is adapted from the one I (JohnT) wrote for HearThis Android, mainly leaving out parts
 * not needed and updating to more recent API since the one used in HTA doesn't compile
 * with the system settings used in BloomReader.
 * Review: Do we really want a 'server' or would it be better to let the android control
 * more of the process and Bloom serve them up?
 * One advantage is that this approach puts most of the work here, so the sending side...
 * which we eventually want in the Reader as well as the desktop...may be simpler.
 */
public class SyncServer extends Thread {
    Integer _serverPort = 5914; // Must match literal in BloomReaderPublisher.SendBookToWiFi()
    private ImmutableHttpProcessor httpproc = null;
    private BasicHttpContext httpContext = null;
    private HttpService httpService = null;
    boolean _running;
    Context _parent;

    public SyncServer(Context parent)
    {
        super("BloomReaderAndroidServer");
        _parent = parent;
        httpproc = new ImmutableHttpProcessor(new ResponseDate(), new ResponseServer(), new ResponseContent(), new ResponseConnControl());
        httpContext = new BasicHttpContext();

        HttpRequestHandlerMapper requestMapper = new HttpRequestHandlerMapper() {
            @Override
            public HttpRequestHandler lookup(HttpRequest request) {
                String uri = request.getRequestLine().getUri();
                if (uri.contains("/putfile"))
                    return new AcceptFileHandler(_parent);
                else if (uri.contains("/notify"))
                    return new AcceptNotificationHandler();
                return null;
            }
        };

        httpService = new HttpService(httpproc,
                new DefaultConnectionReuseStrategy(),
                new DefaultHttpResponseFactory(), requestMapper);
    }
    public synchronized void startThread() {
        if (_running)
            return; // already started, must not do twice.
        _running = true;

        super.start();
    }

    // Clear flag so main loop will terminate after next request.
    public synchronized void stopThread(){
        _running = false;
    }

    // Method executed in thread when super.start() is called.
    @Override
    public void run() {
        super.run();

        try {
            ServerSocket serverSocket = new ServerSocket(_serverPort);

            serverSocket.setReuseAddress(true);

            while(_running){
                try {
                    final Socket socket = serverSocket.accept();

                    // Constructor requires a buffer size. I found ONE example at
                    // http://www.programcreek.com/java-api-examples/index.php?api=org.apache.http.impl.DefaultBHttpServerConnection
                    // but otherwise no hint anywhere of what the buffer is for or what size might be reasonable.
                    DefaultBHttpServerConnection serverConnection = new DefaultBHttpServerConnection(8*1024);

                    serverConnection.bind(socket);

                    httpService.handleRequest(serverConnection, httpContext);

                    serverConnection.shutdown();
                } catch (IOException | HttpException e) {
                    e.printStackTrace();
                }
            }

            serverSocket.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
