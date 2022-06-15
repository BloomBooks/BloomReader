package org.sil.bloom.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


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
        PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId,    mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager)this.getSystemService(this.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }
}