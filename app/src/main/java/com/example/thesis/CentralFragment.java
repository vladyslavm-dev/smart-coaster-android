package com.example.thesis;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Central overview fragment showing the connection status of all three scales
 * and exposing "Remind" buttons for each device.
 */
public class CentralFragment extends Fragment {

    /**
     * Callback into MainActivity when a "Remind" button is pressed.
     */
    public interface CentralListener {
        void onRemindButtonClicked(int scaleIndex);
    }

    private CentralListener centralListener;
    private View scale1Dot, scale2Dot, scale3Dot;
    private TextView scale1Status, scale2Status, scale3Status;

    public static CentralFragment newInstance() {
        return new CentralFragment();
    }

    public void setCentralListener(CentralListener listener) {
        this.centralListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_central, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        scale1Dot = view.findViewById(R.id.scale1_dot);
        scale2Dot = view.findViewById(R.id.scale2_dot);
        scale3Dot = view.findViewById(R.id.scale3_dot);

        scale1Status = view.findViewById(R.id.scale1_status);
        scale2Status = view.findViewById(R.id.scale2_status);
        scale3Status = view.findViewById(R.id.scale3_status);

        Button btnRemind1 = view.findViewById(R.id.btn_remind_scale1);
        Button btnRemind2 = view.findViewById(R.id.btn_remind_scale2);
        Button btnRemind3 = view.findViewById(R.id.btn_remind_scale3);

        btnRemind1.setOnClickListener(v -> {
            if (centralListener != null) {
                centralListener.onRemindButtonClicked(0);
            }
        });
        btnRemind2.setOnClickListener(v -> {
            if (centralListener != null) {
                centralListener.onRemindButtonClicked(1);
            }
        });
        btnRemind3.setOnClickListener(v -> {
            if (centralListener != null) {
                centralListener.onRemindButtonClicked(2);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // When returning to the central screen, requery the latest statuses.
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            BleDeviceManager scale1 = activity.getScaleManager(0);
            if (scale1 != null) {
                updateConnectionStatus(0, scale1.getLastKnownStatus(), scale1.isCurrentlyConnected());
            }
            BleDeviceManager scale2 = activity.getScaleManager(1);
            if (scale2 != null) {
                updateConnectionStatus(1, scale2.getLastKnownStatus(), scale2.isCurrentlyConnected());
            }
            BleDeviceManager scale3 = activity.getScaleManager(2);
            if (scale3 != null) {
                updateConnectionStatus(2, scale3.getLastKnownStatus(), scale3.isCurrentlyConnected());
            }
        }
    }

    /**
     * Updates the colored dot and text status for a single scale.
     */
    public void updateConnectionStatus(int patientIndex, String status, boolean isConnected) {
        View dotView;
        TextView statusView;

        switch (patientIndex) {
            case 0:
                dotView = scale1Dot;
                statusView = scale1Status;
                break;
            case 1:
                dotView = scale2Dot;
                statusView = scale2Status;
                break;
            case 2:
                dotView = scale3Dot;
                statusView = scale3Status;
                break;
            default:
                return;
        }

        if (isConnected) {
            dotView.setBackgroundResource(R.drawable.dot_shape_green);
        } else if ("Reconnecting".equals(status)) {
            dotView.setBackgroundResource(R.drawable.dot_shape_blue);
        } else {
            dotView.setBackgroundResource(R.drawable.dot_shape_red);
        }
        statusView.setText("Scale " + (patientIndex + 1) + ": " + status);
    }
}
