package org.sil.bloom.reader;

import android.app.DialogFragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

public class AnalyticsDeviceInfoEntryDialogFragment extends DialogFragment {
    public static final String ANALYTICS_DEVICE_INFO_ENTRY_DIALOG_FRAGMENT_TAG = "device_info_dialog";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.dialog_device_info, container, false);

        EditText deviceId = view.findViewById(R.id.deviceId);
        EditText project = view.findViewById(R.id.project);

        Pair<String, String> projectAndDevice = BloomReaderApplication.getProjectAndDeviceIds();
        if (projectAndDevice != null) {
            project.setText(projectAndDevice.first);
            deviceId.setText(projectAndDevice.second);
        }

        Button btnSave = view.findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            // We can't allow the user to set non-empty values.
            // 1. Segment will throw if we try to call identify with blank values.
            // 2. Segment does not allow a way to unset a previous identify call.
            //    The value will persist until the app is uninstalled. So we don't
            //    want the user thinking he can unset it when he really can't.
            //    Instead, we include in the device ID documentation that the
            //    only way to unset the value is to uninstall the app.

            boolean isValid = true;
            String deviceIdStr = deviceId.getText().toString().trim();
            if (TextUtils.isEmpty(deviceIdStr)) {
                deviceId.setError("device ID is required");
                isValid = false;
            }
            String projectStr = project.getText().toString().trim();
            if (TextUtils.isEmpty(projectStr)) {
                project.setError("project is required");
                isValid = false;
            }
            if (isValid) {
                BloomReaderApplication.storeDeviceInfoValues(projectStr, deviceIdStr);
                BloomReaderApplication.setUpDeviceIdentityForAnalytics();
                dismiss();
            }
        });

        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> dismiss());

        getDialog().setCanceledOnTouchOutside(isCanceledOnTouchOutside());

        return view;
    }

    protected boolean isCanceledOnTouchOutside() {
        return true;
    }
}
