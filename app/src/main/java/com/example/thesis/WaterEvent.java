package com.example.thesis;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Immutable record of a single water event (intake or refill).
 * Stores a stable unique ID per run so list operations can use it
 * as a key independent of RecyclerView positions.
 */
public class WaterEvent {
    public String timestamp;  // "yyyy-MM-dd HH:mm:ss"
    public String type;       // "I" or "R"
    public float amount;
    public String cupName;

    // Monotonically increasing counter to assign stable IDs in memory.
    private static long eventCounter = 0;
    public final long uniqueId;

    public WaterEvent(String timestamp, String type, float amount, String cupName) {
        this.timestamp = timestamp;
        this.type = type;
        this.amount = amount;
        this.cupName = cupName;

        // Unique ID for each event during the app run.
        this.uniqueId = ++eventCounter;
    }

    /**
     * Parses the timestamp using the fixed event format and returns
     * the epoch time in milliseconds. Returns 0 if parsing fails.
     */
    public long getTimeMillis() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()).parse(timestamp).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public long getUniqueId() {
        return uniqueId;
    }
}
