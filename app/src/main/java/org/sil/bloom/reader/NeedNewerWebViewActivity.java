package org.sil.bloom.reader;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


// This is a trivial activity that just tells the user he needs a newer version of WebView.
public class NeedNewerWebViewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_need_newer_web_view2);
        setupHyperlink();
    }

    private void setupHyperlink() {
        TextView linkTextView = findViewById(R.id.need_newer_webview_link);
        //linkTextView.setMovementMethod(LinkMovementMethod.getInstance());
        linkTextView.setLinkTextColor(Color.parseColor("#0000EE"));
        linkTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://details?id=com.google.android.webview"));
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    // This happens on some emulators...typically not on real devices, which tend
                    // to have PlayStore installed. Huawei and Amazon devices may not...hopefully
                    // they still handle this intent intelligently? Or are too new to have the problem?
                    e.printStackTrace();
                }
            }
        });
    }

    public void restartClicked(View view) {
        // Force BR to restart by setting up an alarm to launch it in 100ms, then quitting.
        Intent mStartActivity = new Intent(this, MainActivity.class);
        int mPendingIntentId = 123456;
        int flags = PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Targeting S (api 31) or higher requires us to set this or FLAG_MUTABLE.
            // But this flag only exists in M or higher.
            // (Counterintuitively, Build.VERSION.SDK_INT is the version of the Android system
            // we are running under, not the one we were built for.)
            // I chose IMMUTABLE because the documentation says it is safer/preferred, and it seems to work.
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId, mStartActivity, flags);
        AlarmManager mgr = (AlarmManager)this.getSystemService(ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }
}