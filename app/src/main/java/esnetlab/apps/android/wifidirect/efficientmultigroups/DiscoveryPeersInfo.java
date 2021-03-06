package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Ahmed on 4/7/2015.
 */
class DiscoveryPeersInfo implements ProtocolConstants {
    final List<DiscoveryPeerInfo> peersInfo = new ArrayList<>();
    final List<WifiP2pDevice> devices = new ArrayList<>();
    private DiscoveryPeerInfo selectedGoPeer = null;
    private DiscoveryPeerInfo spareGoPeer = null;

    public void add(DiscoveryPeerInfo discoveryPeerInfo) {
        peersInfo.add(discoveryPeerInfo);
    }

    public void addOrUpdate(String discoveryPeersInfoString) {
        addOrUpdate(discoveryPeersInfoString, "");
    }

    void addOrUpdate(String discoveryPeersInfoString, String ignoreDeviceId) {
        String dpiStrings[] = discoveryPeersInfoString.split(";");
        for (String dpiString : dpiStrings) {
            DiscoveryPeerInfo peerInfo = DiscoveryPeerInfo.convertStringToPeerInfo(dpiString);
            if (peerInfo.deviceId.equals(ignoreDeviceId))
                continue;
            DiscoveryPeerInfo existingPeerInfo = getPeerInfo(peerInfo.deviceId);
            if (existingPeerInfo != null) {
                peersInfo.remove(existingPeerInfo);
            }
            peersInfo.add(peerInfo);
        }
    }

    boolean addOrUpdate(String deviceId, Map<String, String> record) {
        boolean cond1 = false;
        boolean cond2 = false;
        DiscoveryPeerInfo peerInfo = getPeerInfo(deviceId);
        //If the peer is not existed, add new one
        if (peerInfo == null) {
            peerInfo = new DiscoveryPeerInfo(deviceId);
            peersInfo.add(peerInfo);
        }

        if (record.containsKey(RECORD_LEVEL)) {
            peerInfo.batteryLevel = Integer.valueOf(record.get(RECORD_LEVEL));
        }
        if (record.containsKey(RECORD_CAPACITY)) {
            peerInfo.batteryCapacity = Integer.valueOf(record.get(RECORD_CAPACITY));
        }
        if (record.containsKey(RECORD_CHARGING)) {
            peerInfo.batteryIsCharging = Boolean.valueOf(record.get(RECORD_CHARGING));
        }
        if (record.containsKey(RECORD_PROPOSED_IP)) {
            peerInfo.proposedIP = extractIpFromPeer(record.get(RECORD_PROPOSED_IP));
        }
        if (record.containsKey(RECORD_SSID)) {
            if (peerInfo.legacySSID.equals(record.get(RECORD_SSID)))
                cond1 = true;
            else
                peerInfo.legacySSID = record.get(RECORD_SSID);
        }
        if (record.containsKey(RECORD_KEY)) {
            if (peerInfo.legacyKey.equals(record.get(RECORD_KEY)))
                cond2 = true;
            else
                peerInfo.legacyKey = record.get(RECORD_KEY);
        }
        if (record.containsKey(RECORD_NUMBER_OF_MEMBERS)) {
            peerInfo.numOfMembers = Integer.valueOf(record.get(RECORD_NUMBER_OF_MEMBERS));
        }

        return cond1 & cond2;
    }

    void addDevice(WifiP2pDevice p2pDevice) {
        WifiP2pDevice device = getDeviceByMacAddress(p2pDevice.deviceAddress);
        if (device != null)
            devices.remove(device);
        devices.add(p2pDevice);
    }

    private WifiP2pDevice getDeviceByMacAddress(String deviceAddress) {
        for (WifiP2pDevice device : devices)
            if (device.deviceAddress.equalsIgnoreCase(deviceAddress))
                return device;
        return null;
    }

    WifiP2pDevice getSelectedGoDevice() {
        if (selectedGoPeer != null) {
            return getDeviceByMacAddress(selectedGoPeer.deviceId);
        }
        return null;
    }

    public WifiP2pDevice getSpareGoDevice() {
        if (spareGoPeer != null) {
            return getDeviceByMacAddress(spareGoPeer.deviceId);
        }
        return null;
    }

    public void remove(DiscoveryPeerInfo discoveryPeerInfo) {
        peersInfo.remove(discoveryPeerInfo);
    }

    void clear() {
        peersInfo.clear();
    }

    DiscoveryPeerInfo getPeerInfo(String deviceId) {
        for (DiscoveryPeerInfo discoveryPeerInfo : peersInfo) {
            if (discoveryPeerInfo.deviceId.equals(deviceId)) {
                return discoveryPeerInfo;
            }
        }
        return null;
    }

    String getPeerLegacyInfoStr(String deviceId) {
        String str = null;
        DiscoveryPeerInfo pInfo = getPeerInfo(deviceId);

        if (pInfo != null) {
            if ((!pInfo.legacySSID.equals(""))
                    && (!pInfo.legacyKey.equals(""))) {
                str = pInfo.legacySSID + "," + pInfo.legacyKey;
            }
        }

        return str;
    }

    /**
     * For a group member, ths function sets the value of the GO and SpareGo
     * That the GM should connect to.
     */
    String decideGoAndSpareToConnect() {
        String rankStr = "";
        float firstRank = -1.0f;
        float secondRank = -1.0f;
        DiscoveryPeerInfo first = null, second = null;

        for (DiscoveryPeerInfo peerInfo : peersInfo) {
            if (peerInfo.getIsGO()) {
                float rank = peerInfo.getCalculatedRank();
                float oldFirstRank = firstRank;
                if (rank > firstRank) {
                    firstRank = rank;
                    first = peerInfo;
                }
                if ((rank > secondRank) && (secondRank < oldFirstRank)) {
                    secondRank = rank;
                    second = peerInfo;
                }
                rankStr += "DiscoveryPeer: "
                        + peerInfo.toString()
                        + "\n\tRank: "
                        + rank + "\n";
            }
        }

        selectedGoPeer = first;
        spareGoPeer = second;
        //Now based on the number of members on each group we decide which group to join
        if (second != null) {
            int num2 = second.numOfMembers;
            int num1 = first.numOfMembers;

            if (num2 < num1) {
                selectedGoPeer = second;
                spareGoPeer = first;
            }
        }

        return rankStr;
    }

    /**
     * Decides if me is the best GO based on the currently known peers
     *
     * @return true if me is the best
     */
    String getBestGoIsMe(Context context) {
        String rankStr = "";
        boolean meIsBest = false;
        BatteryInformation batteryInfo = new BatteryInformation();
        batteryInfo.getBatteryStats(context);

        DiscoveryPeerInfo myInfo = new DiscoveryPeerInfo("", batteryInfo.level, batteryInfo.capacity, batteryInfo.isCharging, "");

        float bestRank = -1.0f;
        for (DiscoveryPeerInfo peerInfo : peersInfo) {
            float rank = peerInfo.getCalculatedRank();
            if (rank > bestRank)
                bestRank = rank;

            rankStr += "DiscoveryPeer: "
                    + peerInfo.toString()
                    + "\n\tRank: "
                    + rank + "\n";
        }

        float myRank = myInfo.getCalculatedRank();
        rankStr += "MyInfo: "
                + myInfo.toString()
                + "\n\tRank: "
                + myRank + "\n";

        if (myRank > bestRank)
            meIsBest = true;

        return meIsBest ? "YES\n" + rankStr : rankStr;
    }

    boolean isMyProposedIpConflicting(String proposedIp, String conflictedIpsString) {
        boolean conflict = false;

        if (!conflictedIpsString.isEmpty()) {
            //Try to check if my proposed IP is in the conflict list sent by any of the other neighbors
            String[] ipStr = conflictedIpsString.split(",");
            for (String anIpStr : ipStr) {
                if (anIpStr.equals(proposedIp)) {
                    return true;
                }
            }
        }

        //Try to check if there is a direct conflict with neighbors
        for (DiscoveryPeerInfo pInfo : peersInfo) {
            if (!"".equals(pInfo.proposedIP)) {
                if (proposedIp.equals(pInfo.proposedIP)) {
                    conflict = true;
                    break;
                }
            }
        }

        return conflict;
    }

    /**
     * Iterates along all the stored discovery peers and return any conflicting IPs. These IPs
     * should be reported to nearby peers to inform them to change their conflicting IPs.
     *
     * @return Comma concatenated string of the conflicted IPs
     */
    String getConflictedPeerIPs() {
        String cStr = "";
        for (int i = 0; i < peersInfo.size() - 1; i++)
            for (int j = i + 1; j < peersInfo.size(); j++) {
                if (peersInfo.get(i).proposedIP.equals(peersInfo.get(j).proposedIP)) {
                    cStr += "," + peersInfo.get(i).proposedIP;
                }
            }
        return cStr;
    }

    String extractConflictedIpsFromPeer(String receivedString) {
        String result = "";

        String[] str = receivedString.split(",");
        if (str.length > 1) {
            result = receivedString.substring(str[0].length() + 1);
        }

        return result;
    }

    private String extractIpFromPeer(String receivedString) {
        String[] str = receivedString.split(",");
        return str[0];
    }

    String getConflictFreeIP(int maxSubnetX, int maxSubnetY) {
        String pIP = Utilities.generateProposedIP(maxSubnetX, maxSubnetY);

        while (isMyProposedIpConflicting(pIP, "")) {
            pIP = Utilities.generateProposedIP(maxSubnetX, maxSubnetY);
        }

        return pIP;
    }

    String toStringGoOnly() {
        String str = "";
        for (DiscoveryPeerInfo discoveryPeerInfo : peersInfo) {
            if (discoveryPeerInfo.getIsGO())
                str += discoveryPeerInfo.toString() + ";";
        }
        //remove the last semicolon
        if (!str.equals(""))
            str = str.substring(0, str.length() - 1);
        return str;
    }

    @Override
    public String toString() {
        String str = "";
        for (DiscoveryPeerInfo discoveryPeerInfo : peersInfo) {
            str += discoveryPeerInfo.toString() + ";";
        }
        //remove the last semicolon
        if (!str.equals(""))
            str = str.substring(0, str.length() - 1);
        return str;
    }
}
