package org.sil.bloom.reader;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.segment.analytics.Analytics;
import com.segment.analytics.ConnectionFactory;
import com.segment.analytics.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;

// Our special Application class which allows us to share information easily between activities.
// Note that anything stored here should not be expected to persist indefinitely.
// If the Android OS needs memory, it can destroy this object and create a new one even
// without creating new activity objects. So, null checks are always necessary.
public class BloomReaderApplication extends Application {
    public static final String SHARED_PREFERENCES_TAG = "org.sil.bloom.reader.prefs";
    public static final String LAST_RUN_BUILD_CODE = "lastRunBuildCode";
    public static final String ANALYTICS_DEVICE_PROJECT = "analyticsDeviceGroup";
    public static final String ANALYTICS_DEVICE_ID = "analyticsDeviceId";
    public static final String DEVICE_ID_FILE_NAME = "deviceId.json";
    public static final String SOMETHING_MODIFIED_FILE_NAME = "something.modified";

    private String bookToHighlight;
    private static Context sApplicationContext;
    private static final String kBloomLibraryAnalyticsProxyUrl = "https://analytics.bloomlibrary.org";

    // Created by main activity, used also by shelf activities. The active one controls its filter.
    public static BookCollection theOneBookCollection;

    public static boolean stillNeedToSetupAnalytics = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplicationContext = getApplicationContext();
        setupAnalytics(this);
    }

    private static void setupAnalytics(Context context) {
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
                .connectionFactory(new ConnectionFactory() {
                    // This override of openConnection is taken and modified from Segment's
                    // suggestion on how to set up a proxy.
                    // See https://segment.com/docs/sources/mobile/android/#proxy-http-calls
                    //
                    // They also recommend and provide a sample proxy implementation
                    // which has a "reverse proxy" to choose the correct url on the other end.
                    // See https://github.com/segmentio/segment-proxy
                    //
                    // Instead of running a server with this reverse proxy, we just
                    // use Cloudflare and distinguish the urls here.
                    @Override protected HttpURLConnection openConnection(String url) throws IOException {
                        // In Cloudflare, we have created CNAME records to proxy the urls below.
                        // We found the three segment.{io|com} urls in com.segment.analytics.ConnectionFactory.
                        // Testing indicates we probably only need api.segment.io (though the cdn one is called),
                        // but we've added them all to be safe.
                        String proxyUrl;
                        Uri uri = Uri.parse(url);
                        switch (uri.getHost()){
                            case "cdn-settings.segment.com":
                                proxyUrl = "https://analytics-cdn-settings.bloomlibrary.org";
                                break;
                            case "mobile-service.segment.com":
                                proxyUrl = "https://analytics-mobile-service.bloomlibrary.org";
                                break;
                            case "api.segment.io":
                            default:
                                proxyUrl = "https://analytics.bloomlibrary.org";
                                break;
                        }

                        // The path is just the part after the domain, e.g. /v1/import in https://api.segment.io/v1/import
                        String path = uri.getPath();
                        return super.openConnection(proxyUrl + path);
                    }
                })

                // Tracks Application Opened, Application Installed, Application Updated, etc.
                .trackApplicationLifecycleEvents()

                // In Aug 2020, we decided to turn these off.
                // They don't seem to provide anything useful in our simple app, and they
                // are using up precious offline queue slots (max 1000).
                // Tracks each screen opened
                //.recordScreenViews()

                .build();

        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            String[] numbers = version.split("\\.");
            analytics.getAnalyticsContext().putValue("majorMinor", numbers[0] + "." + numbers[1]);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // Set the initialized instance as a globally accessible instance.
        Analytics.setSingletonInstance(analytics);

        // Check for deviceId json file and use its contents to identify this device
        setUpDeviceIdentityForAnalytics();

        // Don't collect any analytics when running in the FirebaseTestLab.
        if (IsRunningInFirebaseTestLab())
            Analytics.with(getBloomApplicationContext()).optOut(true);
    }

    static boolean firstRunAfterInstallOrUpdate = false;
    public static boolean isFirstRunAfterInstallOrUpdate() {
        return firstRunAfterInstallOrUpdate;
    }

    public static void setupVersionUpdateInfo(Context context) {
        SharedPreferences values = context.getSharedPreferences(BloomReaderApplication.SHARED_PREFERENCES_TAG, 0);
        int buildCode = BuildConfig.VERSION_CODE;
        int savedVersion = values.getInt(BloomReaderApplication.LAST_RUN_BUILD_CODE, 0);
        if(buildCode > savedVersion){
            firstRunAfterInstallOrUpdate = true;
            SharedPreferences.Editor valuesEditor = values.edit();
            valuesEditor.putInt(BloomReaderApplication.LAST_RUN_BUILD_CODE, buildCode);
            valuesEditor.apply();
        }
        if (savedVersion == 0) {
            // very first run
            String installer = context.getPackageManager().getInstallerPackageName(context.getPackageName());
            if (TextUtils.isEmpty(installer)) {
                Log.w("BloomReader", "getInstallerPackageName() returned nothing");
                installer = "unknown";
            }
            // Send an analytics event. Trying to follow the structure of a standard one as far
            // as possible: https://segment.com/docs/spec/mobile/#lifecycle-events.
            Properties p = new Properties();
            p.put("provider", "getInstallerPackageName");
            p.putValue("installer", installer); // The fields of 'campaign' don't seem to apply

            ReportAnalyticsWithLocationIfPossible(context, "Install Attributed", p);
        }
    }

    public static void ReportAnalyticsWithLocationIfPossible(Context context, String event, Properties p) {
        // Location
        LocationManager lm = null;
        if (MainActivity.haveLocationPermission(context)) {
            lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        new ReportAnalyticsTask().execute(new ReportAnalyticsTaskParams(event, p, lm));
    }

    public static void setUpDeviceIdentityForAnalytics(){
        Pair<String, String> projectAndDevice = getProjectAndDeviceIds();
        if (projectAndDevice == null)
            return;

        String project = projectAndDevice.first;
        String device = projectAndDevice.second;

        // The value used with identify() needs to be globally unique. Just in case somebody
        // might reuse a deviceId in a different project, we concatenate them.
        String deviceId = project + "-" + device;
        Context context = getBloomApplicationContext();
        if (context != null) {
            Analytics.with(context).identify(deviceId);
            Analytics.with(context).group(project);
        }
    }

    public static Pair<String, String> getProjectAndDeviceIds() {
        SharedPreferences values = getBloomApplicationContext().getSharedPreferences(SHARED_PREFERENCES_TAG, 0);
        if(values.getString(ANALYTICS_DEVICE_ID, null) == null){
            boolean deviceIdFromFile = processDeviceIdFile();
            if(!deviceIdFromFile)
                return null;
        }
        String project = values.getString(ANALYTICS_DEVICE_PROJECT, "");
        String device = values.getString(ANALYTICS_DEVICE_ID, "");

        return new Pair<>(project, device);
    }

    private static boolean processDeviceIdFile() {
        try {
            String jsonString = getJsonFromDeviceIdFile();
            if (jsonString == null)
                return false;

            JSONObject json = new JSONObject(jsonString);
            String project = json.getString("project");
            String device = json.getString("device");

            storeDeviceInfoValues(project, device);
            return true;
        } catch (Exception e) {
            Log.e("Analytics", "Error processing deviceId file json.");
            Log.e("Analytics", e.getMessage());
            e.printStackTrace();

            if (e instanceof JSONException) {
                Toast failToast = Toast.makeText(getBloomApplicationContext(), "Unable to load device metadata. JSON formatting error.", Toast.LENGTH_LONG);
                failToast.show();
            }

            return false;
        }
    }

    public static void storeDeviceInfoValues(String project, String deviceId) {
        SharedPreferences values = getBloomApplicationContext().getSharedPreferences(SHARED_PREFERENCES_TAG, 0);
        SharedPreferences.Editor valuesEditor = values.edit();
        valuesEditor.putString(ANALYTICS_DEVICE_PROJECT, project);
        valuesEditor.putString(ANALYTICS_DEVICE_ID, deviceId);
        valuesEditor.apply();
        reportDeviceIdParseSuccess(project, deviceId);
    }

    private static String getJsonFromDeviceIdFile() {
        Context context = getBloomApplicationContext();
        if (BaseActivity.haveLegacyStoragePermission(context)) {
            File deviceIdFile = new File(BookCollection.getBloomDirectory(), DEVICE_ID_FILE_NAME);
            if (deviceIdFile.exists())
                return IOUtilities.FileToString(deviceIdFile);
        } else if (SAFUtilities.hasPermissionToBloomDirectory(context)) {
            Uri deviceIdFileUri = SAFUtilities.fileUriFromDirectoryUri(
                    context, SAFUtilities.getBloomDirectoryTreeUri(), DEVICE_ID_FILE_NAME);
            return SAFUtilities.getUriContents(context, deviceIdFileUri);
        }

        // We don't generally expect to find this file in our local books directory,
        // but the first few releases of BR v3 moved the file there from the Bloom directory.
        // So we want to ensure any devices which did that still get the correct analytics.
        File deviceIdFile = new File(BookCollection.getLocalBooksDirectory(), DEVICE_ID_FILE_NAME);
        if (deviceIdFile.exists())
            return IOUtilities.FileToString(deviceIdFile);

        return null;
    }

    private static void reportDeviceIdParseSuccess(String project, String device){
        String successMessage = "Device Metadata Loaded\nProject: " + project + "\nDevice: " + device;
        Toast success = Toast.makeText(getBloomApplicationContext(), successMessage, Toast.LENGTH_LONG);
        success.show();
    }

    public static boolean shouldPreserveFilesInOldDirectory() {
        return BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("alpha") || BuildConfig.FLAVOR.equals("beta");
    }

    public static boolean InTestModeForAnalytics() {
        boolean testMode = BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("alpha");
        if (testMode)
            return true;

        if (IsRunningInFirebaseTestLab())
            return true;

        try {
            // We're looking for a file called UseTestAnalytics in the old Bloom folder at the root of
            // the device storage. We no longer have access to the files in this folder, unless the user
            // has given it to us, but surprisingly we can still find out whether the file exists.
            // This may be an accident that a future Android version will fix.
            File oldBookDirectory = IOUtilities.getOldBloomBooksFolder(BloomReaderApplication.sApplicationContext);
            if (oldBookDirectory == null) {
                return false; // bizarre situation, but let's not lose any real data
            }

            // We'd really like to just ignore case, but no easy way to do it.
            return new File(oldBookDirectory, "UseTestAnalytics").exists()
                    || new File(oldBookDirectory, "useTestAnalytics").exists()
                    || new File(oldBookDirectory, "usetestanalytics").exists();
        } catch (Exception e) {
            // Doesn't seem worth crashing the app just to see if we shouldn't send analytics.
            e.printStackTrace();
            return false;
        }
    }

    public static boolean IsRunningInFirebaseTestLab() {
        try {
            // From https://firebase.google.com/docs/test-lab/android/android-studio#modify_instrumented_test_behavior_for
            return "true".equals(System.getString(getBloomApplicationContext().getContentResolver(), "firebase.test.lab"));
        } catch (Exception e) {
            // No exception here is worth crashing
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

    public static String getOurDeviceName() {
        try {
            return android.provider.Settings.Global.getString(sApplicationContext.getContentResolver(), "device_name");
        } catch (Exception e) {
            // Oh well, fall back to Build.MODEL
        }
        try {
            return android.os.Build.MODEL;
        } catch (Exception e) {
            // Oh well, we tried.
        }
        return null;
    }
}
