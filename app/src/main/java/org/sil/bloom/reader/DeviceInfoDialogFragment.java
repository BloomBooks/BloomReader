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

public class DeviceInfoDialogFragment extends DialogFragment {
    public static final String DEVICE_INFO_DIALOG_FRAGMENT_TAG = "device_info_dialog";

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
            boolean isValid = true;
            if (isEmpty(deviceId)) {
                deviceId.setError("device ID is required");
                isValid = false;
            }
            if (isEmpty(project)) {
                project.setError("project is required");
                isValid = false;
            }
            if (isValid) {
                BloomReaderApplication.storeDeviceInfoValues(project.getText().toString(), deviceId.getText().toString());
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

    private boolean isEmpty(EditText text) {
        CharSequence str = text.getText().toString();
        return TextUtils.isEmpty(str);
    }
}
