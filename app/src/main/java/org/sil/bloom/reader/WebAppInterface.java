package org.sil.bloom.reader;

import android.webkit.JavascriptInterface;
import android.widget.Toast;

/**
 * This class exists to provide methods that Javascript in a WebView can make
 * callbacks on. Currently this class is attached using
 * addJavascriptInterface(one of these, "ParentProxy"); as a result, the
 * JavaScript can call the @JavascriptInterface methods of this class as if they
 * were methods of a global object called ParentProxy, e.g.,
 * ParentProxy.postMessage("some json"). Or in typescript, since I haven't
 * figured out a way to declare that this global exists and has these methods,
 * we defeat type-checking with (window as any).ParentProxy.postMessage("some
 * json").
 */

public class WebAppInterface {
    // The reader activity that created this class to serve a particular web view.
    private ReaderActivity mContext;

    WebAppInterface(ReaderActivity c) {
        mContext = c;
    }

    // This can be helpful in debugging. It's not currently used in production.
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    // Receives messages 'posted' by the javascript in the webview.
    // Unfortunately, can't find a way to configure things so that
    // window.postMessage will call this.
    // Instead, we set things up as described in the class comment.
    @JavascriptInterface
    public void receiveMessage(String message) {
        mContext.receiveMessage(message);
    }
}