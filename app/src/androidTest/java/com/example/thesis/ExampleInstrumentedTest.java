package com.example.thesis;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Instrumented tests that run on a device/emulator.
 * Focuses on wiring between Context, DataManager and WaterEvent.
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void useAppContext_packageNameIsCorrect() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull(appContext);
        // Adjust if your package name changes.
        assertEquals("com.example.thesis", appContext.getPackageName());
    }

    @Test
    public void dataManager_addWaterEvent_increasesListSize() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DataManager dm = DataManager.getInstance(appContext);

        int patientIndex = 0;
        int initialSize = dm.getEventsForPatient(patientIndex).size();

        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        WaterEvent event = new WaterEvent(ts, "I", 250f, "TestCup");

        dm.addWaterEvent(patientIndex, event);
        int newSize = dm.getEventsForPatient(patientIndex).size();

        assertEquals("Adding a water event should increase the list size by one",
                initialSize + 1, newSize);
    }

    @Test
    public void dataManager_sumCountsOnlyIntakeEvents() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DataManager dm = DataManager.getInstance(appContext);

        int patientIndex = 1;

        // Make sums predictable: start from a clean slate for this patient.
        dm.clearEvents(patientIndex);

        // Use a future timestamp so it is always inside the time window.
        String futureTs = "2099-01-01 12:00:00";

        // Intake event: should be counted.
        WaterEvent intake = new WaterEvent(futureTs, "I", 200f, "CupA");
        dm.addWaterEvent(patientIndex, intake);

        // Refill event: should NOT be counted in intake sum.
        WaterEvent refill = new WaterEvent(futureTs, "R", 100f, "CupB");
        dm.addWaterEvent(patientIndex, refill);

        // Big window in hours so both events are included by time.
        float sum = dm.getIntakeSumHours(patientIndex, 24 * 365);

        // Only the intake amount should be counted, not the refill.
        assertEquals(200f, sum, 0.001f);
    }
}
