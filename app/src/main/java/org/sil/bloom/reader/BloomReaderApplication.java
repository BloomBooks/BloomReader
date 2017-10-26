package org.sil.bloom.reader;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import com.segment.analytics.Analytics;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.io.File;

// Our special Application class which allows us to share information easily between activities.
// Note that anything stored here should not be expected to persist indefinitely.
// If the Android OS needs memory, it can destroy this object and create a new one even
// without creating new activity objects. So, null checks are always necessary.
public class BloomReaderApplication extends Application {
    public static final String SHARED_PREFERENCES_TAG = "org.sil.bloom.reader.prefs";
    public static final String LAST_RUN_BUILD_CODE = "lastRunBuildCode";
    public static final String ANALYTICS_DEVICE_PROJECT = "analyticsDeviceGroup";
    public static final String ANALYTICS_DEVICE_ID = "analyticsDeviceId";
    public static final String DEVICE_ID_FILE = "deviceId.json";

    private String bookToHighlight;
    private static Context sApplicationContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplicationContext = getApplicationContext();

        String writeKey = "FSepBapJtfOi3FfhsEWQjc2Dw0O3ixuY"; // Source BloomReaderTest

        if (InTestModeForAnalytics()) {
            VerboseToast("Using Test Analytics");
        } else {
            if (BuildConfig.FLAVOR == "production") {
                writeKey = "EfisyNbRjBYIHyHZ9njJcs5dWF4zabyH"; // Source BloomReader
            } else {
                writeKey = "HRltJ1F4vEVgCypIMeRVnjLAMUTAyOAI"; // Source BloomReaderBeta
            }
        }

        // Create an analytics client with the given context and Segment write key.
        Analytics analytics = new Analytics.Builder(sApplicationContext, writeKey)
                // Tracks Application Opened, Application Installed, Application Updated
                .trackApplicationLifecycleEvents()
                // Tracks each screen opened
                .recordScreenViews()
                .build();

        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            String[] numbers = version.split("\\.");
            analytics.getAnalyticsContext().putValue("majorMinor", numbers[0] + "." + numbers[1]);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Set the initialized instance as a globally accessible instance.
        Analytics.setSingletonInstance(analytics);

        // Check for deviceId json file and use its contents to identify this device
        identifyDevice();
    }

    private static void identifyDevice(){
        SharedPreferences values = getBloomApplicationContext().getSharedPreferences(SHARED_PREFERENCES_TAG, 0);
        if(values.getString(ANALYTICS_DEVICE_ID, null) == null){
            boolean deviceIdFromFile = parseDeviceIdFile(values.edit());
            if(!deviceIdFromFile)
                return;
        }
        String project = values.getString(ANALYTICS_DEVICE_PROJECT, "");
        String device = values.getString(ANALYTICS_DEVICE_ID, "");

        // The value used with identify() needs to be globally unique. Just in case somebody
        // might reuse a deviceId in a different project, we concatenate them.
        String deviceId = project + "-" + device;
        Analytics.with(getBloomApplicationContext()).identify(deviceId);
        Analytics.with(getBloomApplicationContext()).group(project);
    }

    private static boolean parseDeviceIdFile(SharedPreferences.Editor valuesEditor){
        try{
            String filename = BookCollection.getLocalBooksDirectory().getPath() + File.separator + DEVICE_ID_FILE;
            File deviceIdFile = new File(filename);
            if(!deviceIdFile.exists())
                return false;
            String jsonString = IOUtilities.FileToString(deviceIdFile);
            JSONObject json = new JSONObject(jsonString);
            String project = json.getString("project");
            String device = json.getString("device");
            valuesEditor.putString(ANALYTICS_DEVICE_PROJECT, project);
            valuesEditor.putString(ANALYTICS_DEVICE_ID, device);
            valuesEditor.commit();
            reportDeviceIdParseSuccess(project, device);
            return true;
        }
        catch (ExtStorageUnavailableException e){
            Log.e("Analytics", "Unable to check for deviceId file because external storage is unavailable.");
            // No toast here, because we could end up here with regular users not even trying to load a device id.
            return false;
        }
        catch (JSONException e){
            Log.e("Analytics", "Error processing deviceId file json.");
            e.printStackTrace();

            Toast failToast = Toast.makeText(getBloomApplicationContext(), R.string.metadata_error, Toast.LENGTH_LONG);
            failToast.show();

            return false;
        }
    }

    private static void reportDeviceIdParseSuccess(String project, String device){
        String successMessage = getBloomApplicationContext().getString(R.string.metadata_loaded) + "\nProject: " + project + "\nDevice: " + device;
        Toast success = Toast.makeText(getBloomApplicationContext(), successMessage, Toast.LENGTH_LONG);
        success.show();
    }

    public static boolean InTestModeForAnalytics(){
        boolean testMode = BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("alpha");
        if(testMode)
            return true;
        try{
            // We'd really like to just ignore case, but no easy way to do it.
            return new File(BookCollection.getLocalBooksDirectory(), "UseTestAnalytics").exists()
                    || new File(BookCollection.getLocalBooksDirectory(), "useTestAnalytics").exists()
                    || new File(BookCollection.getLocalBooksDirectory(), "usetestanalytics").exists();
        }
        catch (ExtStorageUnavailableException e){
            Log.e("BloomReader/FileIO", e.getStackTrace().toString());
            return false;
        }
    }


    public static void VerboseToast(String message){
        if(InTestModeForAnalytics()) {
            Toast.makeText(getBloomApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
    }


    public void setBookToHighlight(String bookToHighlight) {
        this.bookToHighlight = bookToHighlight;
    }

    public String getBookToHighlight() {
        return this.bookToHighlight;
    }

    // This makes the context generally available during the running of the program.
    // Note that whether it is available during startup depends on whether the instance
    // create method has been called...use with care!
    public static Context getBloomApplicationContext() {
        return sApplicationContext;
    }
}
