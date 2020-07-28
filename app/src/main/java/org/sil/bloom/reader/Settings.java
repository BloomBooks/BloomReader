package org.sil.bloom.reader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

// This class is used to store persistent settings for the entire app.
// Currently there is just one, whether to share location data in analytics messages.
public class Settings {
    private boolean _shareLocation = false;

    public boolean shareLocation() {
        return _shareLocation;
    }

    public void setShareLocation(boolean val)  {
        _shareLocation = val;
    }

    public static Settings load(Activity activity) {
        SharedPreferences sharedPref = activity.getSharedPreferences("BloomReaderSettings", Context.MODE_PRIVATE);
        Settings result = new Settings();
        result.setShareLocation(sharedPref.getBoolean("shareLocation", false));
        return result;
    }

    public void save(Activity activity) {
        SharedPreferences sharedPref = activity.getSharedPreferences("BloomReaderSettings", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("shareLocation", shareLocation());
        editor.commit();
    }
}
