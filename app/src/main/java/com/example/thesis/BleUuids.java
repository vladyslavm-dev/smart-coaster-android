package com.example.thesis;

import java.util.UUID;

/**
 * Central place to hold the BLE service/characteristic UUIDs
 * for the three clinical scales.
 */
public class BleUuids {

    // Scale 1
    public static final UUID SCALE1_SERVICE_UUID = UUID.fromString("6d12c00c-d907-4af8-b4d5-42680cdbbe04");
    public static final UUID SCALE1_TX_CHAR_UUID = UUID.fromString("c663891c-6163-43cc-9ad6-0771785fde9d");
    public static final UUID SCALE1_RX_CHAR_UUID = UUID.fromString("ab36ebe1-b1a5-4c46-b4e6-d54f3fb53247");

    // Scale 2
    public static final UUID SCALE2_SERVICE_UUID = UUID.fromString("c2c6ca78-0b9a-4b20-a565-9cfdd49acf40");
    public static final UUID SCALE2_TX_CHAR_UUID = UUID.fromString("12fe610e-7f1f-4113-979d-146b3a92f52b");
    public static final UUID SCALE2_RX_CHAR_UUID = UUID.fromString("048e7728-d9fb-4879-9f18-39be069c271d");

    // Scale 3
    public static final UUID SCALE3_SERVICE_UUID = UUID.fromString("bb67e522-fd56-427c-9cb1-15c3a3d9d5dc");
    public static final UUID SCALE3_TX_CHAR_UUID = UUID.fromString("e0cea94b-af76-478d-837a-ce6f8b084855");
    public static final UUID SCALE3_RX_CHAR_UUID = UUID.fromString("26a60bc7-8356-434c-9e11-182edbb0a640");

    /*
     * Alternative UUIDs that were used for bench testing when scales were "off".
     * Kept here for quick toggling during development if needed.
     *
     * // Scale 1 OFF
     * public static final UUID SCALE1_SERVICE_UUID = UUID.fromString("6d12c00c-d907-4af8-b4d5-42680cdb1111");
     * public static final UUID SCALE1_TX_CHAR_UUID = UUID.fromString("c663891c-6163-43cc-9ad6-0771785f1111");
     * public static final UUID SCALE1_RX_CHAR_UUID = UUID.fromString("ab36ebe1-b1a5-4c46-b4e6-d54f3fb51111");
     *
     * // Scale 2 OFF
     * public static final UUID SCALE2_SERVICE_UUID = UUID.fromString("c2c6ca78-0b9a-4b20-a565-9cfdd49a1111");
     * public static final UUID SCALE2_TX_CHAR_UUID = UUID.fromString("12fe610e-7f1f-4113-979d-146b3a921111");
     * public static final UUID SCALE2_RX_CHAR_UUID = UUID.fromString("048e7728-d9fb-4879-9f18-39be069c1111");
     *
     * // Scale 3 OFF
     * public static final UUID SCALE3_SERVICE_UUID = UUID.fromString("bb67e522-fd56-427c-9cb1-15c3a3d91111");
     * public static final UUID SCALE3_TX_CHAR_UUID = UUID.fromString("e0cea94b-af76-478d-837a-ce6f8b081111");
     * public static final UUID SCALE3_RX_CHAR_UUID = UUID.fromString("26a60bc7-8356-434c-9e11-182edbb01111");
     */
}
