package org.sil.bloom.reader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

// This class is used to store persistent settings for the entire app.
// Currently there is just one, whether we have ever nagged the user about location permission.
public class Settings {
    private boolean _haveRequestedLocation = false;
    private boolean _haveRequestedTurnOnGps = false;

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

    public static Settings load(Activity activity) {
        SharedPreferences sharedPref = activity.getSharedPreferences("BloomReaderSettings", Context.MODE_PRIVATE);
        Settings result = new Settings();
        result.setHaveRequestedLocation(sharedPref.getBoolean("requestedLocation", false));
        result.setHaveRequestedTurnOnGps(sharedPref.getBoolean("requestedTurnOnGps", false));
        return result;
    }

    public void save(Activity activity) {
        SharedPreferences sharedPref = activity.getSharedPreferences("BloomReaderSettings", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("requestedLocation", haveRequestedLocation());
        editor.putBoolean("requestedTurnOnGps", haveRequestedTurnOnGps());
        editor.commit();
    }
}
