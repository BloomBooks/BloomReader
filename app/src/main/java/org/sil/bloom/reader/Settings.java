package org.sil.bloom.reader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

// This class is used to store persistent settings for the entire app.
// Currently there is just one, whether we have ever nagged the user about location permission.
public class Settings {
    private boolean _haveRequestedLocation = false;
    private boolean _haveRequestedTurnOnGps = false;

    private long _bookReadDuration;
    private String _bookBeingRead;

    private JSONObject _pendingProgressReport;

    public boolean haveRequestedLocation() {
        return _haveRequestedLocation;
    }

    public void setHaveRequestedLocation(boolean val)  {
        _haveRequestedLocation = val;
    }


    public boolean haveRequestedTurnOnGps() {
        return _haveRequestedTurnOnGps;
    }

    public void setHaveRequestedTurnOnGps(boolean val)  {
        _haveRequestedTurnOnGps = val;
    }

    public void setBookReadDuration(long val) { _bookReadDuration = val;}
    public long getBookReadDuration() {return _bookReadDuration;}

    public void setBookBeingRead(String val) { _bookBeingRead = val;}
    public String getBookBeingRead() {return _bookBeingRead;}

    // This is the pages read report we will send for analytics.
    // It is updated by BloomPlayer each time the user changes pages
    // and usually sent when the user exits the book.
    // If something prevents us from sending it on this run, we will
    // find it in the settings and send it the next time the app starts.
    public void setPendingProgressReport(JSONObject val) { _pendingProgressReport = val;}
    public JSONObject getPendingProgressReport() {return _pendingProgressReport;}

    public static Settings load(Activity activity) {
        SharedPreferences sharedPref = activity.getSharedPreferences("BloomReaderSettings", Context.MODE_PRIVATE);
        Settings result = new Settings();
        result.setHaveRequestedLocation(sharedPref.getBoolean("requestedLocation", false));
        result.setHaveRequestedTurnOnGps(sharedPref.getBoolean("requestedTurnOnGps", false));
        result.setBookReadDuration(sharedPref.getLong("bookReadDuration", 0));
        result.setBookBeingRead(sharedPref.getString("bookBeingRead", null));

        try {
            String progressReportStr = sharedPref.getString("pendingProgressReport", null);
            if (progressReportStr != null) {
                result.setPendingProgressReport(new JSONObject(progressReportStr));
            }
        } catch (JSONException je) {
            je.printStackTrace();
        }
        return result;
    }

    public void save(Activity activity) {
        SharedPreferences sharedPref = activity.getSharedPreferences("BloomReaderSettings", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("requestedLocation", haveRequestedLocation());
        editor.putBoolean("requestedTurnOnGps", haveRequestedTurnOnGps());
        editor.putLong("bookReadDuration", getBookReadDuration());
        editor.putString("bookBeingRead", getBookBeingRead());
        JSONObject pendingProgressReport = getPendingProgressReport();
        if (pendingProgressReport != null) {
            editor.putString("pendingProgressReport", pendingProgressReport.toString());
        } else {
            editor.putString("pendingProgressReport", null);
        }
        editor.commit();
    }
}
