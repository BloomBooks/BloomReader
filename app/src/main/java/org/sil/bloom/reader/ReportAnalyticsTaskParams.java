package org.sil.bloom.reader;
import com.segment.analytics.Properties;

// This class exists to be the argument to ReportAnalyticsTask. The base class design requires
// a single type for the argument to doInBackground, but we need to pass two arguments of
// different types. This class does nothing but combine them into a single object.
public class ReportAnalyticsTaskParams {
    public ReportAnalyticsTaskParams(String event, Properties p) {
        this.event = event;
        this.properties = p;
    }
    public Properties properties;
    public String event;
}
