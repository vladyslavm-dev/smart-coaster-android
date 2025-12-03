package com.example.thesis;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Simple JVM unit tests for WaterEvent.
 * These do not require an Android device/emulator.
 */
public class ExampleUnitTest {

    @Test
    public void waterEvent_parsesTimestampToMillis() throws ParseException {
        String ts = "2025-01-02 03:04:05";

        // Expected millis using the same format as the app.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        long expected = sdf.parse(ts).getTime();

        WaterEvent event = new WaterEvent(ts, "I", 250f, "TestCup");
        long actual = event.getTimeMillis();

        assertEquals("getTimeMillis() should parse the formatted timestamp correctly",
                expected, actual);
    }

    @Test
    public void waterEvent_uniqueId_isMonotonic() {
        WaterEvent e1 = new WaterEvent("2025-01-02 03:04:05", "I", 200f, "CupA");
        WaterEvent e2 = new WaterEvent("2025-01-02 03:05:05", "R", 50f, "CupB");

        long id1 = e1.getUniqueId();
        long id2 = e2.getUniqueId();

        assertTrue("Second event ID should be greater than the first", id2 > id1);
    }
}