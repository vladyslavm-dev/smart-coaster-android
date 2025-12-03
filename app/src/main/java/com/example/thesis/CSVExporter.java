package com.example.thesis;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * Builds a temporary CSV file from the in-memory events for one patient
 * and launches a share intent so it can be exported (mail, Drive, etc.).
 */
public class CSVExporter {

    /**
     * Exports all events for a patient index to a CSV file in the cache
     * directory and triggers the Android share sheet.
     */
    public static void export(Context context, int patientIndex) {
        List<WaterEvent> events = DataManager.getInstance(context).getEventsForPatient(patientIndex);
        if (events.isEmpty()) {
            return;
        }
        File cacheDir = context.getCacheDir();
        // patientIndex+1 in the CSV filename to match UI numbering.
        File csvFile = new File(cacheDir, "patient_" + (patientIndex + 1) + ".csv");
        FileWriter writer = null;
        try {
            writer = new FileWriter(csvFile);
            writer.write("Timestamp,Event,Weight (g),Cup\n");

            for (WaterEvent e : events) {
                String displayType = e.type.equals("I") ? "Intake"
                        : e.type.equals("R") ? "Refill"
                        : e.type;
                writer.write(e.timestamp + "," + displayType + ","
                        + e.amount + "," + e.cupName + "\n");
            }
            writer.flush();
            writer.close();

            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    csvFile
            );
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(shareIntent, "Share CSV File"));
        } catch (Exception e) {
            Log.e("CSVExporter", "Export failed", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
