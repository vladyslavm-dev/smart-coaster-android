package com.example.thesis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence of water events to simple text files under
 * /Download/Scale Water/patient_X_backup.txt.
 *
 * - When saving: event types "I"/"R" are written as "Intake"/"Refill".
 * - When loading: "Intake"/"Refill" are mapped back to "I"/"R".
 * - Also exposes helpers to delete a single backup or all backups.
 */
public class BackupManager {

    private static final String TAG = "BackupManager";

    /**
     * Returns the app's backup directory inside the public Downloads folder.
     * Creates the directory on first use.
     */
    private static File getBackupDirectory() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File backupDir = new File(downloadsDir, "Scale Water");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        return backupDir;
    }

    /**
     * Persists all events for a given patient index as a CSV-like text file.
     * Types "I"/"R" are written as "Intake"/"Refill" for readability.
     */
    public static void saveBackup(Context context, int patientIndex) {
        File backupDir = getBackupDirectory();
        // Use patientIndex+1 in the file name so the UI numbering matches the file names.
        String filename = "patient_" + (patientIndex + 1) + "_backup.txt";
        File file = new File(backupDir, filename);

        try (PrintWriter writer = new PrintWriter(file)) {
            List<WaterEvent> events = DataManager.getInstance(context).getEventsForPatient(patientIndex);

            for (WaterEvent event : events) {
                String displayType = event.type.equals("I") ? "Intake"
                        : event.type.equals("R") ? "Refill"
                        : event.type;

                @SuppressLint("DefaultLocale")
                String line = String.format("%s,%s,%.2f,%s",
                        event.timestamp, displayType, event.amount, event.cupName);
                writer.println(line);
            }
            Log.d(TAG, "Backup saved for patient " + (patientIndex + 1)
                    + " => " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving backup for patient "
                    + (patientIndex + 1) + ": " + e.getMessage());
        }
    }

    /**
     * Convenience helper to load a backup into DataManager.
     * Currently only used from manual calls, not on startup.
     */
    public static void loadBackup(Context context, int patientIndex) {
        Log.v(TAG, "loadBackup for pkg=" + context.getPackageName());
        List<WaterEvent> events = readBackupFile(context, patientIndex);
        for (WaterEvent ev : events) {
            DataManager.getInstance(context).getEventsForPatient(patientIndex).add(ev);
        }
        Log.d(TAG, "Backup loaded into DataManager for patient "
                + (patientIndex + 1) + " => " + events.size() + " events");
    }

    /**
     * Reads one backup file from disk and converts "Intake"/"Refill" back to "I"/"R".
     * Called on startup by DataManager.loadAllBackupsOnce().
     */
    public static List<WaterEvent> readBackupFile(Context context, int patientIndex) {
        List<WaterEvent> result = new ArrayList<>();

        File backupDir = getBackupDirectory();
        // Use patientIndex+1 to align with filenames created in saveBackup().
        File file = new File(backupDir, "patient_" + (patientIndex + 1) + "_backup.txt");

        if (!file.exists()) {
            Log.d(TAG, "No backup file found for patient "
                    + (patientIndex + 1) + " at " + file.getAbsolutePath());
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    String timestamp = parts[0];
                    String displayType = parts[1];
                    float amount = Float.parseFloat(parts[2]);
                    String cupName = parts[3];

                    String shortType;
                    if ("Intake".equalsIgnoreCase(displayType)) {
                        shortType = "I";
                    } else if ("Refill".equalsIgnoreCase(displayType)) {
                        shortType = "R";
                    } else {
                        shortType = displayType;
                    }
                    WaterEvent event = new WaterEvent(timestamp, shortType, amount, cupName);
                    result.add(event);
                }
            }
            Log.d(TAG, "File: " + file.getAbsolutePath()
                    + " => exists=" + file.exists()
                    + ", length=" + file.length());
            Log.d(TAG, "readBackupFile => " + result.size()
                    + " lines from " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error reading backup for patient "
                    + (patientIndex + 1) + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * Deletes backup files for all three patients (patient_1..patient_3).
     */
    public static void deleteAllBackups(Context context) {
        for (int i = 0; i < 3; i++) {
            deleteBackup(context, i);
        }
    }

    /**
     * Deletes the backup file for a single patient if it exists.
     */
    public static void deleteBackup(Context context, int patientIndex) {
        Log.v(TAG, "deleteBackup for pkg=" + context.getPackageName());
        File backupDir = getBackupDirectory();
        File file = new File(backupDir, "patient_" + (patientIndex + 1) + "_backup.txt");
        if (file.exists()) {
            if (file.delete()) {
                Log.d(TAG, "Backup file deleted for patient " + (patientIndex + 1));
            } else {
                Log.e(TAG, "Failed to delete backup file for patient " + (patientIndex + 1));
            }
        }
    }
}
