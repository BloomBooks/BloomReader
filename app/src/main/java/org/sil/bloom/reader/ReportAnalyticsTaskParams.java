package org.sil.bloom.reader;
import android.location.LocationManager;

import com.segment.analytics.Properties;

// This class exists to be the argument to ReportAnalyticsTask. The base class design requires
// a single type for the argument to doInBackground, but we need to pass four arguments of
// different types. This class does nothing but combine them into a single object.
public class ReportAnalyticsTaskParams {
    public ReportAnalyticsTaskParams(String event, Properties p, boolean s, LocationManager lm) {
        this.event = event;
        this.properties = p;
        this.shareLocation = s;
        this.locationManager = lm;
    }
    public Properties properties;
    public String event;
    public boolean shareLocation;
    public LocationManager locationManager;
}
