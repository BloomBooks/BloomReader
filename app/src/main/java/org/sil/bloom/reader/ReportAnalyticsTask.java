package org.sil.bloom.reader;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.util.Log;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

import java.security.Security;
import java.util.List;

// This task supports reporting analytics on a background thread. We are doing this not so much
// to improve performance, but in hopes that it may make reporting pages read more reliable.
// This report is sent when the ReaderActivity is shutting down, and was previously unreliable.
// We suspect some race condition between the analytics reporting and the termination of the
// ReaderActivity, and hope that making the reporting async will help.
public class ReportAnalyticsTask extends AsyncTask<ReportAnalyticsTaskParams, Void, Void> {
    // The async task requires doInBackground to have this signature; but for this task,
    // there should always be exactly one ReportAnalyticsTaskParams argument.
    @Override
    protected Void doInBackground(ReportAnalyticsTaskParams... reportAnalyticsTaskParams) {
        // Try to get a location (if we're allowed)
        LocationManager locationManager = reportAnalyticsTaskParams[0].locationManager;
        Properties p = reportAnalyticsTaskParams[0].properties;
        if (locationManager == null) {
            p.putValue("locationSource", "denied");
        } else {
            // If for any reason we cannot set the location, just go ahead and report without it.
            // But note the failure.
            Location bestLocation = setLocations(p, locationManager);
            if (bestLocation == null) {
                p.putValue("locationSource", "failed");
            }
        }

        Context context = BloomReaderApplication.getBloomApplicationContext();
        // Play Console reports we sometimes get null here which causes a crash.
        // I wish I understood what was happening better, but anything is better than crashing the app just to send analytics.
        if (context != null)
            Analytics.with(context).track(reportAnalyticsTaskParams[0].event, reportAnalyticsTaskParams[0].properties);
        return null;
    }

    public static long locationAgeDays(Location location) {
        long nanos = location.getElapsedRealtimeNanos();
        long ageNanos = SystemClock.elapsedRealtimeNanos() - nanos;
        return ageNanos/1000000000/60/60/24;
    }

    @Nullable
    private static Location setLocations(Properties props, LocationManager locationManager) {
        // We record the locations available from each of the standard providers (network, gps,
        // and passive) explicitly.  But for the standard location for this report, we mainly
        // want the most recent location we can get; precision is not very important.
        // However, we know from experience that in poor countries, IP address doesn't give us
        // reliable location, and we expect that wifi and other networks will be similarly
        // unreliable as means of location. So if we have a
        // reasonably recent high-precision location we will take that in preference to a
        // lower-precision one that may be even more current. (Elsewhere we request one location
        // per hour from GPS, if available, to ensure that the "last known location" for the
        // gps provider will be reasonably recent.)
        List<String> providers = locationManager.getAllProviders();
        Location bestLocation = null;
        for (String provider : providers) {
            try {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) continue;
                bestLocation = getBestLocation(bestLocation, location);
                if (provider.equals("gps") || provider.equals("passive") || provider.equals("network")) {
                    // Note: "passive" may actually return "gps" or "network" as the actual provider.
                    props.putValue(provider + "_latitude", location.getLatitude());
                    props.putValue(provider + "_longitude", location.getLongitude());
                    props.putValue(provider + "_locationAgeDays", locationAgeDays(location));
                    props.putValue(provider + "_locationAccuracy", location.getAccuracy());
                }
            } catch (SecurityException se) {
                // We didn't have permission. Very surprising, since we should not have been
                // passed a locationManager if we aren't allowed to use it.
                Log.e("locationError", "unexpectedly forbidden to get location for " + provider);
                // other providers may be okay, so continue the for loop
            }
        }
        if (bestLocation != null) {
            props.putValue("latitude", bestLocation.getLatitude());
            props.putValue("longitude", bestLocation.getLongitude());
            props.putValue("locationSource", bestLocation.getProvider());
            props.putValue("locationAgeDays", locationAgeDays(bestLocation));
            props.putValue("locationAccuracy", bestLocation.getAccuracy());
        };
        return bestLocation;
    }

    @Nullable
    // This method is used in MainActivity.showLocationMessage().
    public static Location getLocation(LocationManager locationManager) {
        Properties props = new Properties();
        return setLocations(props, locationManager);
    }

    private static long ageMinutes(Location location) {
        long nanos = location.getElapsedRealtimeNanos();
        long ageNanos = SystemClock.elapsedRealtimeNanos() - nanos;
        return ageNanos/1000000000/60;
    }

    private static Location getBestLocation(Location a, Location b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean aIsOld = ageMinutes(a) > 100;
        boolean bIsOld = ageMinutes(b) > 100;
        if (aIsOld && !bIsOld) {
            return b;
        }
        if (bIsOld && !aIsOld) {
            return a;
        }
        // If both are old we'll use the newest
        if (aIsOld && bIsOld) {
            return a.getTime() < b.getTime() ? b : a;
        }
        // Both are reasonably current, use the most accurate
        return a.getAccuracy() < b.getAccuracy() ? a : b;
    }
}


