package org.sil.bloom.reader;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import com.segment.analytics.Analytics;

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
