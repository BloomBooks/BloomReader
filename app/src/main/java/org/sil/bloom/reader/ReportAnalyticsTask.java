package org.sil.bloom.reader;

import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.util.Log;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

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
            Location location = getLocation(locationManager);

            // If for any reason we could not get a location, just go ahead and report without it.
            // But note the failure.
            if (location == null) {
                p.putValue("locationSource", "failed");
            } else {

                p.putValue("latitude", location.getLatitude());
                p.putValue("longitude", location.getLongitude());
                p.putValue("locationSource", location.getProvider());
                p.putValue("locationAgeDays", locationAgeDays(location));
                p.putValue("locationAccuracy", location.getAccuracy());
            }
        }
        Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track(reportAnalyticsTaskParams[0].event, reportAnalyticsTaskParams[0].properties);
        return null;
    }

    public static long locationAgeDays(Location location) {
        long nanos = location.getElapsedRealtimeNanos();
        long ageNanos = SystemClock.elapsedRealtimeNanos() - nanos;
        return ageNanos/1000000000/60/60/24;
    }

    @Nullable
    public static Location getLocation(LocationManager locationManager) {
        try {
            // We mainly want the most recent location we can get; precision is not very important.
            // However, we know from experience that in poor countries, IP address doesn't give us
            // reliable location, and we expect that wifi and other networks will be similarly
            // unreliable as means of location. So if we have a
            // reasonably recent high-precision location we will take that in preference to a
            // lower-precision one that may be even more current. (Elsewhere we request one location
            // per hour from GPS, if available, to ensure that the "last known location" for the
            // gps provier will be reasonably recent.)
            List<String> providers = locationManager.getAllProviders();
            Location bestLocation = null;
            for (String provider : providers) {
                Location location = locationManager.getLastKnownLocation(provider);
                bestLocation = getBestLocation(bestLocation, location);
            };
            return bestLocation;
        } catch (SecurityException se) {
            // We didn't have permission. Very surprising, since we should not have been
            // passed a locationManager if we aren't allowed to use it.
            Log.e("locationError", "unexpectedly forbidden to get location");
            return null;
        }
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


