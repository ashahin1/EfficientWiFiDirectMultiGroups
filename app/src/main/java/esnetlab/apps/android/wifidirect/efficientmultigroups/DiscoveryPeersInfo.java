package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Ahmed on 4/7/2015.
 */
public class DiscoveryPeersInfo implements ProtocolConstants {
    public final List<DiscoveryPeerInfo> peersInfo = new ArrayList<>();
    public final List<WifiP2pDevice> devices = new ArrayList<>();
    public DiscoveryPeerInfo selectedGoPeer = null;
    public DiscoveryPeerInfo spareGoPeer = null;

    public void add(DiscoveryPeerInfo discoveryPeerInfo) {
        peersInfo.add(discoveryPeerInfo);
    }

    public void addOrUpdate(String discoveryPeersInfoString) {
        addOrUpdate(discoveryPeersInfoString, "");
    }

    public void addOrUpdate(String discoveryPeersInfoString, String ignoreDeviceId) {
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

    public boolean addOrUpdate(String deviceId, Map<String, String> record) {
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

    public void addDevice(WifiP2pDevice p2pDevice) {
        WifiP2pDevice device = getDeviceByMacAddress(p2pDevice.deviceAddress);
        if (device != null)
            devices.remove(device);
        devices.add(p2pDevice);
    }

    public WifiP2pDevice getDeviceByMacAddress(String deviceAddress) {
        for (WifiP2pDevice device : devices)
            if (device.deviceAddress.equalsIgnoreCase(deviceAddress))
                return device;
        return null;
    }

    public WifiP2pDevice getSelectedGoDevice() {
        if (selectedGoPeer != null) {
            WifiP2pDevice device = getDeviceByMacAddress(selectedGoPeer.deviceId);
            return device;
        }
        return null;
    }

    public WifiP2pDevice getSpareGoDevice() {
        if (spareGoPeer != null) {
            WifiP2pDevice device = getDeviceByMacAddress(spareGoPeer.deviceId);
            return device;
        }
        return null;
    }

    public void remove(DiscoveryPeerInfo discoveryPeerInfo) {
        peersInfo.remove(discoveryPeerInfo);
    }

    public void clear() {
        peersInfo.clear();
    }

    public DiscoveryPeerInfo getPeerInfo(String deviceId) {
        for (DiscoveryPeerInfo discoveryPeerInfo : peersInfo) {
            if (discoveryPeerInfo.deviceId.equals(deviceId)) {
                return discoveryPeerInfo;
            }
        }
        return null;
    }

    public String getPeerLegacyInfoStr(String deviceId) {
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
    public String decideGoAndSpareToConnect() {
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
    public String getBestGoIsMe(Context context) {
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

    public boolean isMyProposedIpConflicting(String proposedIp, String conflictedIpsString) {
        boolean conflict = false;

        if (!conflictedIpsString.isEmpty()) {
            //Try to check if my proposed IP is in the conflict list sent by any of the other neighbors
            String[] ipStr = conflictedIpsString.split(",");
            for (int i = 0; i < ipStr.length; i++) {
                if (ipStr[i].equals(proposedIp)) {
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
    public String getConflictedPeerIPs() {
        String cStr = "";
        for (int i = 0; i < peersInfo.size() - 1; i++)
            for (int j = i + 1; j < peersInfo.size(); j++) {
                if (peersInfo.get(i).proposedIP.equals(peersInfo.get(j).proposedIP)) {
                    cStr += "," + peersInfo.get(i).proposedIP;
                }
            }
        return cStr;
    }

    public String extractConflictedIpsFromPeer(String receivedString) {
        String result = "";

        String[] str = receivedString.split(",");
        if (str.length > 1) {
            result = receivedString.substring(str[0].length() + 1);
        }

        return result;
    }

    public String extractIpFromPeer(String receivedString) {
        String[] str = receivedString.split(",");
        return str[0];
    }

    public String getConflictFreeIP(int maxsubnetX, int maxSubnetY) {
        String pIP = Utilities.generateProposedIP(maxsubnetX, maxSubnetY);

        while (isMyProposedIpConflicting(pIP, "")) {
            pIP = Utilities.generateProposedIP(maxsubnetX, maxSubnetY);
        }

        return pIP;
    }

    public String toStringGoOnly() {
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
