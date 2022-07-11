package org.sil.bloom.reader;

import android.content.Context;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.widget.Toast;

// This class implements a standard Android interface used to configure various behaviors of a
// WebView. This one is named for use in ReaderActivity, but currently also servers as a base class
// for BloomLibraryWebChromeClient; if we ever have some behavior that is unique to the Reader,
// we can extract a common superclass.
public class ReaderWebChromeClient extends WebChromeClient {

    protected Context mContext;

    public ReaderWebChromeClient(Context context) {
        mContext = context;
    }
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        boolean result = super.onConsoleMessage(consoleMessage);
        if (consoleMessage.messageLevel()== ConsoleMessage.MessageLevel.ERROR) {
            Log.e("ReaderConsoleError", consoleMessage.message() + " in line #" + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
            // This is an attempt to detect that the error is something to do with an activity.
            // Time will tell whether (a) it annoyingly reports problems that are not serious, or
            // (b) fails to report many problems we'd like to let the user know about.
            // It was adequate to give some information about a problem with several of the
            // activities on http://phet.colorado.edu/, which Koen would like to use but which
            // don't work in Android WebView. I don't think it's worth localizing until we have
            // more experience with it, and maybe not then...hopefully only fairly technical
            // authors experimenting with new widgets will see it.
            int index = consoleMessage.sourceId().indexOf("/activities/");
            if (index >= 0) {
                String source = consoleMessage.sourceId().substring(index + 1);
                String msg = "There might be a problem with an activity on this page or the next: " + consoleMessage.message() + " at line " + consoleMessage.lineNumber() + " in " + source;
                Toast.makeText(mContext, msg,Toast.LENGTH_LONG).show();
            }
        }
        return result;
    }
}
