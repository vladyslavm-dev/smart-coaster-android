package com.example.thesis;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Central in-memory store for all water events across patients.
 * Events are stored with compact types ("I"/"R") and backed by simple
 * text files on disk via BackupManager.
 *
 * On first initialization, all backup files are loaded into memory so that
 * sums and charts work across app restarts.
 */
public class DataManager {
    private static final String TAG = "DataManager";
    private static DataManager instance;
    private final Context context;

    // In-memory lists per patient.
    private final List<WaterEvent> patient0Events = new ArrayList<>();
    private final List<WaterEvent> patient1Events = new ArrayList<>();
    private final List<WaterEvent> patient2Events = new ArrayList<>();

    /**
     * Listener notified whenever a patient’s data changes.
     */
    public interface DataUpdateListener {
        void onDataUpdated(int patientIndex);
    }

    private final List<DataUpdateListener> listeners = new ArrayList<>();

    private DataManager(Context ctx) {
        this.context = ctx.getApplicationContext();
        loadAllBackupsOnce();
    }

    public static synchronized DataManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new DataManager(ctx);
        }
        return instance;
    }

    /**
     * Called once at startup: loads backup files for all patients and
     * converts textual types ("Intake"/"Refill") back to "I"/"R".
     */
    private void loadAllBackupsOnce() {
        for (int i = 0; i < 3; i++) {
            List<WaterEvent> fromFile = BackupManager.readBackupFile(context, i);
            getEventsForPatient(i).addAll(fromFile);
            Log.d(TAG, "Loaded " + fromFile.size() + " events for patient " + i);
        }
        Log.d(TAG, "All backups loaded into memory => sums & chart will see I/R codes.");
    }

    // ------------------------------------------------------------------
    // LISTENER REGISTRATION
    // ------------------------------------------------------------------
    public void addDataUpdateListener(DataUpdateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeDataUpdateListener(DataUpdateListener listener) {
        listeners.remove(listener);
    }

    private void notifyDataUpdated(int patientIndex) {
        for (DataUpdateListener l : listeners) {
            l.onDataUpdated(patientIndex);
        }
    }

    // ------------------------------------------------------------------
    // ACCESSORS
    // ------------------------------------------------------------------
    public List<WaterEvent> getEventsForPatient(int index) {
        switch (index) {
            case 0:
                return patient0Events;
            case 1:
                return patient1Events;
            case 2:
                return patient2Events;
            default:
                return new ArrayList<>();
        }
    }

    // ------------------------------------------------------------------
    // MUTATORS
    // ------------------------------------------------------------------
    public synchronized void addWaterEvent(int index, WaterEvent event) {
        getEventsForPatient(index).add(event);
        // Persist new state; file stores "Intake"/"Refill".
        BackupManager.saveBackup(context, index);
        notifyDataUpdated(index);
    }

    /**
     * Removes an event by list position (legacy path).
     */
    public synchronized void removeEvent(int index, int position) {
        List<WaterEvent> lst = getEventsForPatient(index);
        if (position >= 0 && position < lst.size()) {
            lst.remove(position);
            BackupManager.saveBackup(context, index);
            notifyDataUpdated(index);
        }
    }

    /**
     * Removes an event by its stable unique ID.
     */
    public synchronized void removeEvent(int index, long eventId) {
        List<WaterEvent> lst = getEventsForPatient(index);
        int foundPos = -1;
        for (int i = 0; i < lst.size(); i++) {
            if (lst.get(i).getUniqueId() == eventId) {
                foundPos = i;
                break;
            }
        }
        if (foundPos >= 0) {
            lst.remove(foundPos);
            BackupManager.saveBackup(context, index);
            notifyDataUpdated(index);
        }
    }

    /**
     * Clears all events for a single patient and persists the empty list.
     */
    public synchronized void clearEvents(int index) {
        getEventsForPatient(index).clear();
        BackupManager.saveBackup(context, index);
        notifyDataUpdated(index);
    }

    /**
     * Removes all backup files on disk. In-memory data is not touched here.
     */
    public void clearAllBackups() {
        // This removes all .txt files in /Download/Scale Water.
        BackupManager.deleteAllBackups(context);
    }

    // ------------------------------------------------------------------
    // AGGREGATION LOGIC
    // ------------------------------------------------------------------
    public float getIntakeSumHours(int index, int hours) {
        long now = System.currentTimeMillis();
        long cutoff = now - (hours * 3600_000L);
        float sum = 0f;
        for (WaterEvent w : getEventsForPatient(index)) {
            if ("I".equals(w.type)) {
                long t = w.getTimeMillis();
                if (t >= cutoff) {
                    sum += w.amount;
                }
            }
        }
        return sum;
    }

    /**
     * Returns the intake sum for one calendar day.
     * dayOffset=0 => today, 1 => yesterday, etc.
     */
    public float getIntakeSumDayOffset(int index, int dayOffset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        // Midnight of "today - dayOffset".
        long startOfDayMs = c.getTimeInMillis() - (dayOffset * 24L * 3600_000L);
        long endOfDayMs = startOfDayMs + (24L * 3600_000L);

        float sum = 0f;
        for (WaterEvent w : getEventsForPatient(index)) {
            if ("I".equals(w.type)) {
                long t = w.getTimeMillis();
                if (t >= startOfDayMs && t < endOfDayMs) {
                    sum += w.amount;
                }
            }
        }
        return sum;
    }
}
