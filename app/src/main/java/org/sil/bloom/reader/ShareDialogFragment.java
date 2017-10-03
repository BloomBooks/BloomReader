package org.sil.bloom.reader;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

/**
 * Created by rick on 10/2/17.
 */

public class ShareDialogFragment extends DialogFragment {
//    private static final String LOG_TAG_ANALYTICS = "AB-Analytics";
//    private static final String SHARE_TYPE_LINK = "share-link";
//    private static final String SHARE_TYPE_FILE = "share-file";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.dialog_share, container, false);


        // Graphic
        ImageView img = (ImageView) view.findViewById(R.id.imgGraphic);
        img.setAdjustViewBounds(true);

        // Calculate hues
//        int originalImgColor = Color.rgb(45, 78, 107);
//        int originalImgHue   = (int)GraphicsUtils.colorHue(originalImgColor);

//        int color = getStyleColor(CommonStyleName.UI_ACTION_BAR, PropertyName.COLOR_TOP);
//        int hue   = (int)GraphicsUtils.colorHue(color);

//        if (hue > 0) {
//            // Use coloured image, modifying hue for current color scheme
//            Drawable d = ResourcesCompat.getDrawable(getResources(), R.drawable.app_sharing, null);
//            img.setImageDrawable(d);
//
//            int rotation = hue - originalImgHue;
//            ColorFilter filter = ColorFilterGenerator.adjustColor(0, 0, 0, rotation);
//            img.setColorFilter(filter);
//        }
//        else {
            // Use grayscale image
//            Drawable d = ResourcesCompat.getDrawable(getResources(), R.drawable.app_sharing_grey, null);
//            img.setImageDrawable(d);
////        }
//
//        img.requestLayout();


        // Share Link
        Button btnShareLink = (Button) view.findViewById(R.id.btnShareLink);
//        if (getAppDefinition().getAppConfig().hasFeature(CommonFeatureName.SHARE_APP_LINK)) {

            btnShareLink.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
//                    SharingManager mgr = new SharingManager(getActivity(), getAppDefinition());
//                    trackShare(SHARE_TYPE_LINK);
//                    mgr.shareLinkToAppOnGooglePlay();
                    dismiss();
                }
            });
//        }
//        else {
//            btnShareLink.setVisibility(View.GONE);
//        }

        // Share APK
        Button btnShareApk = (Button) view.findViewById(R.id.btnShareApkFile);
//        if (getAppDefinition().getAppConfig().hasFeature(CommonFeatureName.SHARE_APK_FILE)) {

            btnShareApk.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    SharingManager mgr = new SharingManager(getActivity());
                    mgr.shareApkFile();
                    dismiss();
                }
            });
//        }
//        else {
//            btnShareApk.setVisibility(View.GONE);
//        }

        // Cancel button
        Button btnCancel = (Button) view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dismiss();
            }
        });
        btnCancel.setTextColor(getResources().getColor(R.color.accent_material_light));
        btnCancel.setText(getString(R.string.cancel));

        // Allow tap outside dialog to close it
        getDialog().setCanceledOnTouchOutside(isCanceledOnTouchOutside());

        // Colors
//        String bgColorStr = getAppDefinition().getAppConfig().getStylePropertyColorValue(CommonStyleName.UI_DIALOG, BACKGROUND_COLOR);
//        int bgColor = GraphicsUtils.parseColor(bgColorStr, Color.WHITE);
//        view.setBackgroundColor(bgColor);

        return view;
    }

    protected boolean isCanceledOnTouchOutside() {
        return true;
    }

    protected void onCloseDialog() {
        dismiss();
    }

//    private void trackShare(String shareType) {
//        AppDefinition app = getAppDefinition();
//        if (app.getAppConfig().getAnalytics().isEnabled()) {
//            String appName = app.getAppName();
//            String appVersion = app.getVersionName();
//            Log.i(LOG_TAG_ANALYTICS, String.format("SharingManager::TrackShare: type=%s, name=%s, version=%s", shareType, appName, appVersion));
//            AnalyticsEventShareApp event = new AnalyticsEventShareApp();
//            event.withAttribute(AnalyticsEventShareApp.KEY_SHARE_APP_NAME, appName)
//                    .withAttribute(AnalyticsEventShareApp.KEY_SHARE_APP_VERSION, appVersion)
//                    .withAttribute(AnalyticsEventShareApp.KEY_SHARE_TYPE, shareType);
//            getAppApplication().getAnalyticsManager().trackShareApp(event);
//        }
//    }
}
