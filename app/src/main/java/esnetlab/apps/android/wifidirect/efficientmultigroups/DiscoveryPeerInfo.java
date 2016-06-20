package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.util.Log;

import java.util.Locale;
import java.util.Random;

/**
 * Created by Ahmed on 4/7/2015.
 */
class DiscoveryPeerInfo {
    private static final String DEVICE_ID = "DeviceId";
    private static final String BATTERY_LEVEL = "BatteryLevel";
    private static final String BATTERY_CAPACITY = "BatteryCapacity";
    private static final String LEGACY_SSID = "LegacySSID";
    private static final String LEGACY_KEY = "LegacyKey";
    private static final String BATTERY_IS_CHARGING = "BatteryIsCharging";
    private static final String PROPOSED_IP = "ProposedIP";
    private static final String NUM_OF_MEMBERS = "NumOfMembers";

    String deviceId;
    int batteryLevel;
    int batteryCapacity;
    boolean batteryIsCharging;
    String legacySSID;
    String legacyKey;
    String proposedIP; //The third octet of the IP
    int numOfMembers;

    DiscoveryPeerInfo() {
        updatePeerInfo("", -1, -1, false, "", "", "", 0);
    }

    DiscoveryPeerInfo(String deviceId) {
        updatePeerInfo(deviceId, -1, -1, false, "", "", "", 0);
    }

    DiscoveryPeerInfo(String deviceId, int batteryLevel, int batteryCapacity, boolean batteryIsCharging, String proposedIP) {
        updatePeerInfo(deviceId, batteryLevel, batteryCapacity, batteryIsCharging, proposedIP, "", "", 0);
    }

    DiscoveryPeerInfo(String deviceId, int batteryLevel, int batteryCapacity, boolean batteryIsCharging, String proposedIP, String legacySSID, String legacyKey, int numOfMembers) {
        updatePeerInfo(deviceId, batteryLevel, batteryCapacity, batteryIsCharging, proposedIP, legacySSID, legacyKey, numOfMembers);
    }

    static DiscoveryPeerInfo convertStringToPeerInfo(String peerInfoStr) {
        DiscoveryPeerInfo discoveryPeerInfo = new DiscoveryPeerInfo();
        String splitArr1[] = peerInfoStr.split(",");
        if (splitArr1.length == 8) {
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
                        case PROPOSED_IP:
                            discoveryPeerInfo.proposedIP = tmp;
                            break;
                        case LEGACY_SSID:
                            discoveryPeerInfo.legacySSID = tmp;
                            break;
                        case LEGACY_KEY:
                            discoveryPeerInfo.legacyKey = tmp;
                            break;
                        case NUM_OF_MEMBERS:
                            discoveryPeerInfo.numOfMembers = Integer.valueOf(tmp);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        return discoveryPeerInfo;
    }

    void updatePeerInfo(String deviceId, int batteryLevel, int batteryCapacity, boolean batteryIsCharging, String proposedIP, String legacySSID, String legacyKey, int numOfMembers) {
        this.deviceId = deviceId;
        this.batteryCapacity = batteryCapacity;
        this.batteryLevel = batteryLevel;
        this.batteryIsCharging = batteryIsCharging;
        this.proposedIP = proposedIP;
        this.legacySSID = legacySSID;
        this.legacyKey = legacyKey;
        this.numOfMembers = numOfMembers;
    }

    private void updatePeerInfo(String peerInfoStr) {
        DiscoveryPeerInfo discoveryPeerInfo = convertStringToPeerInfo(peerInfoStr);
        updatePeerInfo(discoveryPeerInfo.deviceId
                , discoveryPeerInfo.batteryLevel
                , discoveryPeerInfo.batteryCapacity
                , discoveryPeerInfo.batteryIsCharging
                , discoveryPeerInfo.proposedIP
                , discoveryPeerInfo.legacySSID
                , discoveryPeerInfo.legacyKey
                , discoveryPeerInfo.numOfMembers);
    }

    boolean deviceInfoAreEqual(int batteryLevel, int batteryCapacity, boolean batteryIsCharging, String proposedIP) {
        return ((this.batteryLevel == batteryLevel)
                && (this.batteryCapacity == batteryCapacity)
                && (this.batteryIsCharging == batteryIsCharging)
                && (proposedIP.equals(this.proposedIP)));
    }

    boolean legacyApInfoAreEqual(String legacySSID, String legacyKey, int numOfMembers) {
        return ((this.legacySSID.equals(legacySSID))
                && (this.legacyKey.equals(legacyKey))
                && (this.numOfMembers == numOfMembers));
    }

    Boolean getIsGO() {
        return !(legacySSID.equals("") || legacyKey.equals(""));
    }

    float getCalculatedRank() {
        //Charging -> mRankAlpha
        //Level -> mRankBeta
        //Capacity -> mRankGamma
        float rank = 0.0f;

        rank += batteryIsCharging ? EfficientWiFiP2pGroupsActivity.mRankAlpha : 0.0f;
        rank += ((batteryLevel + 0.0001f) / 100.0f) * EfficientWiFiP2pGroupsActivity.mRankBeta;
        rank += ((batteryCapacity + 0.0001f) /
                EfficientWiFiP2pGroupsActivity.mRankMaxCapacity) *
                EfficientWiFiP2pGroupsActivity.mRankGamma;
        rank += new Random().nextFloat() * 0.0001f;

        Log.d(EfficientWiFiP2pGroupsActivity.TAG, "getCalculatedRank: " + rank);
        return rank;
    }

    @Override
    public String toString() {
        String str;
        str = String.format(Locale.US, "%s-> %s, %s-> %d, %s-> %d, %s-> %s, %s-> %s, %s-> %s, %s-> %s, %s-> %d",
                DEVICE_ID, deviceId, BATTERY_LEVEL, batteryLevel, BATTERY_CAPACITY, batteryCapacity,
                BATTERY_IS_CHARGING, Boolean.toString(batteryIsCharging), PROPOSED_IP, proposedIP,
                LEGACY_SSID, legacySSID, LEGACY_KEY, legacyKey, NUM_OF_MEMBERS, numOfMembers);
        return str;
    }
}
