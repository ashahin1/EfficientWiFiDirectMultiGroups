package esnetlab.apps.android.wifidirect.efficientmultigroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by Ahmed on 3/28/2016.
 */
public class PerformanceAnalysis {
    public final ArrayList<DiscoveryPeerStatistics> discoveryPeerStatisticsList = new ArrayList<>();
    public final ArrayList<SocketPeerStatistics> socketPeerStatisticsList = new ArrayList<>();

    public int sentServiceDiscoveryRequestCount = 0;
    public int sentManagementSocketMessagesCount = 0;
    public int sentDataSocketMessagesCount = 0;
    public int conflictIpCount = 0;

    public void addDiscoveryStatistic(DiscoveryPeerInfo peerInfo, int length, boolean deviceInfo) {
        DiscoveryPeerStatistics peerStatistics = getDiscoveryPeerStats(peerInfo);

        if (peerStatistics != null) {
            if (deviceInfo)
                peerStatistics.deviceInfoMessageCounter.addLength(length);
            else
                peerStatistics.legacyApMessageCounter.addLength(length);
        } else {
            peerStatistics = new DiscoveryPeerStatistics();
            peerStatistics.discoveryPeerInfo = peerInfo;
            if (deviceInfo)
                peerStatistics.deviceInfoMessageCounter.addLength(length);
            else
                peerStatistics.legacyApMessageCounter.addLength(length);
        }
    }

    public DiscoveryPeerStatistics getDiscoveryPeerStats(DiscoveryPeerInfo peerInfo) {
        DiscoveryPeerStatistics stat = null;

        for (DiscoveryPeerStatistics peerStatistics :
                discoveryPeerStatisticsList) {
            if (peerInfo != null)
                if (peerStatistics.discoveryPeerInfo != null)
                    if (peerInfo.deviceId.equalsIgnoreCase(peerStatistics.discoveryPeerInfo.deviceId)) {
                        stat = peerStatistics;
                        break;
                    }
        }

        return stat;
    }

    public void addSocketStatistic(SocketPeer peerInfo, int length, boolean management) {
        SocketPeerStatistics peerStatistics = getSocketPeerStats(peerInfo);

        if (peerStatistics != null) {
            if (management)
                peerStatistics.managementSocketMessageCounter.addLength(length);
            else
                peerStatistics.dataSocketMessageCounter.addLength(length);
        } else {
            peerStatistics = new SocketPeerStatistics();
            peerStatistics.socketPeer = peerInfo;
            if (management)
                peerStatistics.managementSocketMessageCounter.addLength(length);
            else
                peerStatistics.dataSocketMessageCounter.addLength(length);
        }
    }

    public SocketPeerStatistics getSocketPeerStats(SocketPeer peerInfo) {
        SocketPeerStatistics stat = null;

        for (SocketPeerStatistics peerStatistics :
                socketPeerStatisticsList) {
            if (peerInfo != null)
                if (peerStatistics.socketPeer != null)
                    if (peerInfo.deviceAddress.equalsIgnoreCase(peerStatistics.socketPeer.deviceAddress)) {
                        stat = peerStatistics;
                        break;
                    }
        }

        return stat;
    }

    public String getStatistics() {
        String str = "";
        String pStr = "";

        int totalMgtCount = 0;
        int totalDataCount = 0;
        int totalDeviceInfoCount = 0;
        int totalLegacyApCount = 0;


        for (DiscoveryPeerStatistics peerStats : discoveryPeerStatisticsList) {
            if (peerStats.discoveryPeerInfo != null) {
                pStr += String.format(Locale.US, "****Discovery Stats for Device [%s]****\n", peerStats.discoveryPeerInfo.deviceId);
                pStr += String.format(Locale.US, "===DeviceInfo Msg Stats==\n%s", peerStats.deviceInfoMessageCounter.toString());
                pStr += String.format(Locale.US, "===LegacyAp Msg Stats==\n%s", peerStats.legacyApMessageCounter.toString());

                totalDeviceInfoCount += peerStats.deviceInfoMessageCounter.getTotalNumberOfMessages();
                totalLegacyApCount += peerStats.legacyApMessageCounter.getTotalNumberOfMessages();
            }
        }

        for (SocketPeerStatistics peerStats : socketPeerStatisticsList) {
            if (peerStats.socketPeer != null) {
                pStr += String.format(Locale.US, "****Socket Stats for Device [%s]****\n", peerStats.socketPeer.deviceAddress);
                pStr += String.format(Locale.US, "===Management Stats==\n%s", peerStats.managementSocketMessageCounter.toString());
                pStr += String.format(Locale.US, "===Data Stats==\n%s", peerStats.dataSocketMessageCounter.toString());

                totalMgtCount += peerStats.managementSocketMessageCounter.getTotalNumberOfMessages();
                totalDataCount += peerStats.dataSocketMessageCounter.getTotalNumberOfMessages();
            }
        }

        str += String.format(Locale.US,
                "----------------My Statistics----------------\n" +
                        "Total Service Discovery Requests: %d\n" +
                        "Total Management Msg Sent: %d" +
                        "Total Data Msg Sent: %d" +
                        "Total IP Conflicts: %d" +
                        "----------------------------------------------\n" +
                        "----------Peers Info---------------------\n" +
                        "Total Device Info Received: %d\n" +
                        "Total LegacyAp Received: %d\n" +
                        "Total Management Msg Received: %d\n" +
                        "Total Data Msg Received: %d\n" +
                        "%s" +
                        "-----------------------------------------\n"
                , sentServiceDiscoveryRequestCount
                , sentManagementSocketMessagesCount
                , sentDataSocketMessagesCount
                , conflictIpCount
                , totalDeviceInfoCount
                , totalLegacyApCount
                , totalMgtCount
                , totalDataCount
                , pStr);

        return str;
    }

    public void reset() {
        sentServiceDiscoveryRequestCount = 0;
        sentManagementSocketMessagesCount = 0;
        sentDataSocketMessagesCount = 0;
        conflictIpCount = 0;

        discoveryPeerStatisticsList.clear();
        socketPeerStatisticsList.clear();
    }
}

class DiscoveryPeerStatistics {
    public final MessageCounter deviceInfoMessageCounter = new MessageCounter();
    public final MessageCounter legacyApMessageCounter = new MessageCounter();
    public DiscoveryPeerInfo discoveryPeerInfo = null;
}

class SocketPeerStatistics {
    public final MessageCounter managementSocketMessageCounter = new MessageCounter();
    public final MessageCounter dataSocketMessageCounter = new MessageCounter();
    public SocketPeer socketPeer = null;
}

class MessageCounter {
    final HashMap<Integer, Integer> lengthCounter = new HashMap<>();
    long startTime = 0;

    public void addLength(int length) {
        if (lengthCounter.size() == 0)
            setStartTime();

        if (lengthCounter.containsKey(length)) {
            int count = lengthCounter.get(length);
            lengthCounter.put(length, ++count);
        } else {
            lengthCounter.put(length, 1);
        }
    }

    public long getTotalLength() {
        long totalLength = 0;

        for (int keyLength :
                lengthCounter.keySet()) {
            totalLength += keyLength * lengthCounter.get(keyLength);
        }

        return totalLength;
    }

    public int getTotalNumberOfMessages() {
        int num = 0;

        for (int keyLength :
                lengthCounter.keySet()) {
            num += lengthCounter.get(keyLength);
        }

        return num;
    }

    public void setStartTime() {
        startTime = System.currentTimeMillis();
    }


    public float getBandwidth() {
        float bw = 0;
        long curTime = System.currentTimeMillis();
        long timeDiff = curTime - startTime;

        bw = (getTotalLength() * 1000.0f) / (timeDiff * 1.0f);

        return bw;
    }

    public void reset() {
        lengthCounter.clear();
        startTime = 0;
    }

    @Override
    public String toString() {
        String str = "";

        str += String.format(Locale.US, "Total Number of Messages: %d\n", getTotalNumberOfMessages());
        str += String.format(Locale.US, "Bandwidth: %f\n\n", getBandwidth());
        str += String.format(Locale.US, "%20s%20s\n", "Length", "Count");
        str += String.format(Locale.US, "%40s\n", " ").replace(" ", "-");
        for (int keyLength :
                lengthCounter.keySet()) {
            str += String.format(Locale.US, "%20d%20d\n",
                    keyLength,
                    lengthCounter.get(keyLength));
        }

        return str;
    }
}
