package esnetlab.apps.android.wifidirect.efficientmultigroups;

import java.util.Random;

/**
 * Created by Ahmed on 4/7/2015.
 */
public class DiscoveryPeerInfo {
    public static final String DEVICE_ID = "DeviceId";
    public static final String BATTERY_LEVEL = "BatteryLevel";
    public static final String BATTERY_CAPACITY = "BatteryCapacity";
    public static final String LEGACY_SSID = "LegacySSID";
    public static final String LEGACY_KEY = "LegacyKey";
    public static final String BATTERY_IS_CHARGING = "BatteryIsCharging";
    public String deviceId;
    public int batteryLevel;
    public int batteryCapacity;
    public boolean batteryIsCharging;
    public String legacySSID;
    public String legacyKey;

    DiscoveryPeerInfo() {
        updatePeerInfo("", -1, -1, false, "", "");
    }

    DiscoveryPeerInfo(String deviceId) {
        updatePeerInfo(deviceId, -1, -1, false, "", "");
    }

    DiscoveryPeerInfo(String deviceId, int batteryLevel, int batteryCapacity, boolean batteryIsCharging) {
        updatePeerInfo(deviceId, batteryLevel, batteryCapacity, batteryIsCharging, "", "");
    }

    DiscoveryPeerInfo(String deviceId, int batteryLevel, int batteryCapacity, boolean batteryIsCharging, String legacySSID, String legacyKey) {
        updatePeerInfo(deviceId, batteryLevel, batteryCapacity, batteryIsCharging, legacySSID, legacyKey);
    }

    public static DiscoveryPeerInfo convertStringToPeerInfo(String peerInfoStr) {
        DiscoveryPeerInfo discoveryPeerInfo = new DiscoveryPeerInfo();
        String splitArr1[] = peerInfoStr.split(",");
        if (splitArr1.length == 6) {
            for (String str : splitArr1) {
                String splitArr2[] = str.split("->");
                if (splitArr2.length == 2) {
                    String tmp = splitArr2[1].trim();
                    switch (splitArr2[0].trim()) {
                        case DEVICE_ID:
                            discoveryPeerInfo.deviceId = tmp;
                            break;
                        case BATTERY_LEVEL:
                            discoveryPeerInfo.batteryLevel = Integer.valueOf(tmp);
                            break;
                        case BATTERY_CAPACITY:
                            discoveryPeerInfo.batteryCapacity = Integer.valueOf(tmp);
                            break;
                        case BATTERY_IS_CHARGING:
                            discoveryPeerInfo.batteryIsCharging = Boolean.valueOf(tmp);
                            break;
                        case LEGACY_SSID:
                            discoveryPeerInfo.legacySSID = tmp;
                            break;
                        case LEGACY_KEY:
                            discoveryPeerInfo.legacyKey = tmp;
                            break;
                    }

                }
            }
        }
        return discoveryPeerInfo;
    }

    private void updatePeerInfo(String deviceId, int batteryLevel, int batteryCapacity, boolean batteryIsCharging, String legacySSID, String legacyKey) {
        this.deviceId = deviceId;
        this.batteryCapacity = batteryCapacity;
        this.batteryLevel = batteryLevel;
        this.batteryIsCharging = batteryIsCharging;
        this.legacySSID = legacySSID;
        this.legacyKey = legacyKey;
    }

    private void updatePeerInfo(String peerInfoStr) {
        DiscoveryPeerInfo discoveryPeerInfo = convertStringToPeerInfo(peerInfoStr);
        updatePeerInfo(discoveryPeerInfo.deviceId, discoveryPeerInfo.batteryLevel, discoveryPeerInfo.batteryCapacity, discoveryPeerInfo.batteryIsCharging, discoveryPeerInfo.legacySSID, discoveryPeerInfo.legacyKey);
    }

    public Boolean getIsGO() {
        return !(legacySSID.equals("") || legacyKey.equals(""));
    }

    public float getCalculatedRank() {
        //Charging -> 0.34
        //Level -> 0.33
        //Capacity -> 0.33
        float rank = 0.0f;

        rank += batteryIsCharging ? 0.34f : 0.0f;
        rank += (batteryLevel / 100.0) * 0.33f;
        rank += (batteryCapacity / 4000.0) * 0.33f;

        return rank+ new Random().nextFloat();
    }

    @Override
    public String toString() {
        String str;
        str = String.format("%s-> %s, %s-> %d, %s-> %d, %s-> %s, %s-> %s, %s-> %s",
                DEVICE_ID, deviceId, BATTERY_LEVEL, batteryLevel, BATTERY_CAPACITY, batteryCapacity,
                BATTERY_IS_CHARGING, Boolean.toString(batteryIsCharging), LEGACY_SSID, legacySSID, LEGACY_KEY, legacyKey);
        return str;
    }
}
