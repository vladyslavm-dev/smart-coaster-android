package com.example.thesis;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Detail fragment for a single patient:
 * - Tabular list of events (RecyclerView)
 * - Delete-all and CSV export actions
 * - Summary text and weekly bar chart.
 */
public class PatientFragment extends Fragment implements DataManager.DataUpdateListener {

    /**
     * Notifies MainActivity when the "clear all backups" action is selected
     * from the navigation drawer.
     */
    public interface PatientFragmentListener {
        void onClearAllBackupsSelected();
    }

    private static final String ARG_PATIENT_INDEX = "patientIndex";
    private int patientIndex;

    private TextView tvSummaries;
    private BarChart barChart;
    private RecyclerView recyclerView;
    private PatientEventAdapter adapter;
    private Button btnDeleteAll, btnExportCsv;
    private PatientFragmentListener patientFragmentListener;
    private Spinner sumModeSpinner;

    public void setPatientFragmentListener(PatientFragmentListener listener) {
        this.patientFragmentListener = listener;
    }

    public static PatientFragment newInstance(int patientIndex) {
        PatientFragment f = new PatientFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_PATIENT_INDEX, patientIndex);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            patientIndex = getArguments().getInt(ARG_PATIENT_INDEX, 0);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_patient, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvSummaries = view.findViewById(R.id.tv_summaries);
        barChart = view.findViewById(R.id.bar_chart);
        recyclerView = view.findViewById(R.id.recycler_entries);
        btnDeleteAll = view.findViewById(R.id.btn_delete_all);
        btnExportCsv = view.findViewById(R.id.btn_export_csv);

        // Spinner for selecting the aggregation window (1h, 1d, 1w, 1m).
        sumModeSpinner = view.findViewById(R.id.sumModeSpinner);
        ArrayAdapter<CharSequence> sumAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.summary_modes,
                android.R.layout.simple_spinner_item
        );
        sumAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sumModeSpinner.setAdapter(sumAdapter);
        sumModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent, View v, int position, long id) {
                updateSummariesAndChart();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Recycler + adapter.
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PatientEventAdapter(getContext(), patientIndex);
        recyclerView.setAdapter(adapter);

        // Swipe to delete individual entries.
        ItemTouchHelper helper = new ItemTouchHelper(
                new MySwipeCallback(getContext(), adapter)
        );
        helper.attachToRecyclerView(recyclerView);

        // "Delete all" for this patient only.
        btnDeleteAll.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("Delete All Entries?")
                    .setMessage("Are you sure you want to remove all events for this patient?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // 1) Clear in-memory events.
                        DataManager.getInstance(getContext())
                                .clearEvents(patientIndex);

                        // 2) Delete the backup file for this patient.
                        BackupManager.deleteBackup(getContext(), patientIndex);

                        // 3) Refresh UI.
                        adapter.refreshData();
                        updateSummariesAndChart();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        btnExportCsv.setOnClickListener(v -> {
            CSVExporter.export(getContext(), patientIndex);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        DataManager.getInstance(getContext()).addDataUpdateListener(this);
        adapter.refreshData();
        updateSummariesAndChart();
    }

    @Override
    public void onPause() {
        super.onPause();
        DataManager dm = DataManager.getInstance(getContext());
        dm.removeDataUpdateListener(this);

        // Only persist on pause if we currently have events.
        int size = dm.getEventsForPatient(patientIndex).size();
        if (size > 0) {
            BackupManager.saveBackup(getContext(), patientIndex);
            Log.d("PatientFragment", "onPause => saved " + size + " events for patient " + patientIndex);
        } else {
            Log.d("PatientFragment", "onPause => 0 events, skipping save to prevent overwriting file");
        }
    }

    @Override
    public void onDataUpdated(int updatedIndex) {
        if (updatedIndex == patientIndex && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                adapter.refreshData();
                updateSummariesAndChart();
            });
        }
    }

    // ------------------------------------------------------------------
    // CHART + SUMMARY LOGIC
    // ------------------------------------------------------------------
    private void updateSummariesAndChart() {
        String selectedMode = (String) sumModeSpinner.getSelectedItem();
        if (selectedMode == null) return;

        float sumVal = 0f;
        switch (selectedMode) {
            case "1h":
                sumVal = DataManager.getInstance(getContext())
                        .getIntakeSumHours(patientIndex, 1);
                break;
            case "1d":
                sumVal = DataManager.getInstance(getContext())
                        .getIntakeSumHours(patientIndex, 24);
                break;
            case "1w":
                sumVal = DataManager.getInstance(getContext())
                        .getIntakeSumHours(patientIndex, 24 * 7);
                break;
            case "1m":
                sumVal = DataManager.getInstance(getContext())
                        .getIntakeSumHours(patientIndex, 24 * 30);
                break;
        }
        tvSummaries.setText(String.format(Locale.US,
                "Sum (%s): %.2f g", selectedMode, sumVal));

        // Build Mon...Sun bar chart for daily intake.
        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        List<BarEntry> entries = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2, etc.
        int indexOfToday = (dayOfWeek + 5) % 7;      // Monday=0..Sunday=6
        int highlightX = -1;

        for (int x = 0; x < 7; x++) {
            int offset = indexOfToday - x; // dayOffset=0 => "today"
            float dayIntake = DataManager.getInstance(getContext())
                    .getIntakeSumDayOffset(patientIndex, offset);
            entries.add(new BarEntry(x, dayIntake));
            if (offset == 0) {
                highlightX = x;
            }
        }

        BarDataSet ds = new BarDataSet(entries, "Daily Intake (g)");
        List<Integer> barColors = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if (i == highlightX) {
                barColors.add(ContextCompat.getColor(
                        requireContext(), R.color.light_blue));
            } else {
                barColors.add(ContextCompat.getColor(
                        requireContext(), R.color.blue));
            }
        }
        ds.setColors(barColors);
        ds.setValueTextColor(ContextCompat.getColor(
                requireContext(),
                isDarkModeEnabled() ? R.color.white : R.color.black
        ));

        BarData barData = new BarData(ds);
        barData.setBarWidth(0.5f);
        barChart.setData(barData);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(dayLabels));
        xAxis.setLabelCount(dayLabels.length);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(ContextCompat.getColor(
                requireContext(),
                isDarkModeEnabled() ? R.color.white : R.color.black
        ));
        xAxis.setYOffset(10f);
        barChart.setExtraBottomOffset(16f);

        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setDrawGridLines(false);
        barChart.getAxisLeft().setDrawAxisLine(false);
        barChart.getAxisLeft().setTextColor(ContextCompat.getColor(
                requireContext(),
                isDarkModeEnabled() ? R.color.white : R.color.black
        ));
        barChart.getAxisRight().setEnabled(false);

        // Auto-scale Y-axis based on data but keep a reasonable minimum.
        float maxIntake = 0f;
        for (BarEntry e : entries) {
            maxIntake = Math.max(maxIntake, e.getY());
        }
        float yMax = Math.max(2000f, maxIntake * 1.2f);
        barChart.getAxisLeft().setAxisMaximum(yMax);

        barChart.getDescription().setEnabled(false);
        barChart.setTouchEnabled(false);
        barChart.setDragEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setDrawGridBackground(false);

        Legend legend = barChart.getLegend();
        legend.setEnabled(false);

        barChart.invalidate();
    }

    private boolean isDarkModeEnabled() {
        int mode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return (mode == Configuration.UI_MODE_NIGHT_YES);
    }
}
