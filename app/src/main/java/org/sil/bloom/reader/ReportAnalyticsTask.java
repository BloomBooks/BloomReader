package org.sil.bloom.reader;

import android.os.AsyncTask;

import com.segment.analytics.Analytics;

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
        Analytics.with(BloomReaderApplication.getBloomApplicationContext()).track(reportAnalyticsTaskParams[0].event, reportAnalyticsTaskParams[0].properties);
        return null;
    }
}


