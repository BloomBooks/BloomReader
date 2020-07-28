package org.sil.bloom.reader;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

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
        if (reportAnalyticsTaskParams[0].shareLocation) {
            // get the location, add to properties, track.
            LocationManager locationManager = reportAnalyticsTaskParams[0].locationManager;
            Location location = getLocation(locationManager);
            Properties p = reportAnalyticsTaskParams[0].properties;
            // If for any reason we could not get a location, just go ahead and report without it.
            if (location != null) {
                p.putValue("lat", location.getLatitude());
                p.putValue("long", location.getLongitude());
            }
        }
        Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track(reportAnalyticsTaskParams[0].event, reportAnalyticsTaskParams[0].properties);
        return null;
    }

    @Nullable
    public static Location getLocation(LocationManager locationManager) {
        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, true);
        Location location = null;
        if (provider != null) {

            try {
                location = locationManager.getLastKnownLocation(provider);
            } catch (SecurityException se) {
                Log.e("locationError", "unexpectedly forbidden to get location");
            }
        }
        return location;
    }
}


