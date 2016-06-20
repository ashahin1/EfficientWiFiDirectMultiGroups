package esnetlab.apps.android.wifidirect.efficientmultigroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/**
 * Created by Ahmed on 3/28/2016.
 */
enum ProtocolTestMode {
    NO_TEST,
    IP_CONFLICT_TEST,
    GROUP_FORMATION_TEST,
    PROXY_SELECTION_TEST,
    FULL_EMC_TEST
}

class PerformanceAnalysis {
    private final ArrayList<DiscoveryPeerStatistics> discoveryPeerStatisticsList = new ArrayList<>();
    private final ArrayList<SocketPeerStatistics> socketPeerStatisticsList = new ArrayList<>();

    int sentServiceDiscoveryRequestCount = 0;
    int sentManagementSocketMessagesCount = 0;
    int sentDataSocketMessagesCount = 0;
    int conflictIpCount = 0;
    int runNumber = 1;
    int beGoCount = 0;
    int beGmCount = 0;
    int bePmCount = 0;

    int noOfDevices = -1;
    long startTime = -1;
    private long timeDiff = -1;
    private final HashSet<String> macAddressList = new HashSet<>();

    //TODO: Add proxy mgmnt/data stats
    void addDiscoveryStatistic(String discoveryPeerMacAddr, int length, boolean deviceInfo) {
        DiscoveryPeerStatistics peerStatistics = getDiscoveryPeerStats(discoveryPeerMacAddr);

        if (peerStatistics != null) {
            if (deviceInfo)
                peerStatistics.deviceInfoMessageCounter.addLength(length);
            else
                peerStatistics.legacyApMessageCounter.addLength(length);
        } else {
            peerStatistics = new DiscoveryPeerStatistics();
            peerStatistics.discoveryPeerMacAddr = discoveryPeerMacAddr;
            if (deviceInfo)
                peerStatistics.deviceInfoMessageCounter.addLength(length);
            else
                peerStatistics.legacyApMessageCounter.addLength(length);
            discoveryPeerStatisticsList.add(peerStatistics);
        }
    }

    private DiscoveryPeerStatistics getDiscoveryPeerStats(String peerMacAddr) {
        DiscoveryPeerStatistics stat = null;

        if (peerMacAddr != null)
            for (DiscoveryPeerStatistics peerStatistics :
                    discoveryPeerStatisticsList) {
                if (peerStatistics.discoveryPeerMacAddr != null)
                    if (peerMacAddr.equalsIgnoreCase(peerStatistics.discoveryPeerMacAddr)) {
                        stat = peerStatistics;
                        break;
                    }
            }

        return stat;
    }

    void addSocketStatistic(String socketPeerMacAddr, String socketPeerIpAddr, int length, boolean management) {
        SocketPeerStatistics peerStatistics = getSocketPeerStats(socketPeerIpAddr);

        if (peerStatistics != null) {
            if (!Utilities.isValidMacAddr(peerStatistics.socketPeerMacAddr))
                peerStatistics.socketPeerMacAddr = socketPeerMacAddr;

            if (management)
                peerStatistics.managementSocketMessageCounter.addLength(length);
            else
                peerStatistics.dataSocketMessageCounter.addLength(length);
        } else {
            peerStatistics = new SocketPeerStatistics();
            peerStatistics.socketPeerIpAddr = socketPeerIpAddr;
            peerStatistics.socketPeerMacAddr = socketPeerMacAddr;

            if (management)
                peerStatistics.managementSocketMessageCounter.addLength(length);
            else
                peerStatistics.dataSocketMessageCounter.addLength(length);
            socketPeerStatisticsList.add(peerStatistics);
        }
    }

    private SocketPeerStatistics getSocketPeerStats(String peerAddr) {
        SocketPeerStatistics stat = null;

        if (peerAddr != null)
            for (SocketPeerStatistics peerStatistics :
                    socketPeerStatisticsList) {
                if (peerStatistics.socketPeerIpAddr != null)
                    if (peerAddr.equalsIgnoreCase(peerStatistics.socketPeerIpAddr)) {
                        stat = peerStatistics;
                        break;
                    }
            }

        return stat;
    }

    String getStatistics(String thisDeviceMAC) {
        String str = "";
        String pStr = "";

        int totalMgtCount = 0;
        int totalDataCount = 0;
        int totalDeviceInfoCount = 0;
        int totalLegacyApCount = 0;


        for (DiscoveryPeerStatistics peerStats : discoveryPeerStatisticsList) {
            if (peerStats.discoveryPeerMacAddr != null) {
                pStr += peerStats.getStatistics();

                totalDeviceInfoCount += peerStats.deviceInfoMessageCounter.getTotalNumberOfMessages();
                totalLegacyApCount += peerStats.legacyApMessageCounter.getTotalNumberOfMessages();
            }
        }

        for (SocketPeerStatistics peerStats : socketPeerStatisticsList) {
            if (peerStats.socketPeerIpAddr != null) {
                pStr += peerStats.getStatistics();

                totalMgtCount += peerStats.managementSocketMessageCounter.getTotalNumberOfMessages();
                totalDataCount += peerStats.dataSocketMessageCounter.getTotalNumberOfMessages();
            }
        }

        str += String.format(Locale.US,
                "----------------My Statistics [%s]----------------\n" +
                        "Total Service Discovery Requests: %d\n" +
                        "Total Management Msg Sent: %d\n" +
                        "Total Data Msg Sent: %d\n" +
                        "Total IP Conflicts: %d\n" +
                        "Total Times of Being GO: %d\n" +
                        "Total Times of Being GM: %d\n" +
                        "Total Times of Being PM: %d\n" +
                        "----------------------------------------------------------\n" +
                        "----------------Peers Info---------------------\n" +
                        "Total Device Info Received: %d\n" +
                        "Total LegacyAp Info Received: %d\n" +
                        "Total Management Msg Received: %d\n" +
                        "Total Data Msg Received: %d\n" +
                        "Total Service Discovery Bandwidth: %f Bytes/Sec\n\n"+
                        "%s" +
                        "------------------------------------------------\n"
                , thisDeviceMAC
                , sentServiceDiscoveryRequestCount
                , sentManagementSocketMessagesCount
                , sentDataSocketMessagesCount
                , conflictIpCount
                , beGoCount
                , beGmCount
                , bePmCount
                , totalDeviceInfoCount
                , totalLegacyApCount
                , totalMgtCount
                , totalDataCount
                ,getTotalDiscoveryBandwidth()
                , pStr);

        return str;
    }

    public boolean addMac(String macAddress) {
        boolean res = false;

        macAddressList.add(macAddress);
        if (macAddressList.size() == noOfDevices - 1) {
            timeDiff = System.currentTimeMillis() - startTime;
            res = true;
        }

        return res;
    }

    public String getDiscoveryTestStats() {
        String str;
        str = "============================="
                + "\nNo of Devices: " + noOfDevices
                + "\nResponse Time: " + timeDiff
                + "\n================================";
        return str;
    }

    private float getTotalDiscoveryBandwidth() {
        float bw = 0;

        for (DiscoveryPeerStatistics peerStatistics:
             discoveryPeerStatisticsList) {
            bw += peerStatistics.deviceInfoMessageCounter.getBandwidth();
        }

        return bw;
    }

    void reset() {
        sentServiceDiscoveryRequestCount = 0;
        sentManagementSocketMessagesCount = 0;
        sentDataSocketMessagesCount = 0;
        conflictIpCount = 0;
        runNumber = 1;
        beGoCount = 0;
        beGmCount = 0;
        bePmCount = 0;

        noOfDevices = -1;
        startTime = -1;
        timeDiff = -1;
        macAddressList.clear();

        discoveryPeerStatisticsList.clear();
        socketPeerStatisticsList.clear();
    }


}

class DiscoveryPeerStatistics {
    final MessageCounter deviceInfoMessageCounter = new MessageCounter();
    final MessageCounter legacyApMessageCounter = new MessageCounter();
    //public DiscoveryPeerInfo discoveryPeerInfo = null;
    String discoveryPeerMacAddr = null;

    String getStatistics() {
        String str = "";

        str += String.format(Locale.US, "*******Discovery Stats for Device [%s]*******\n", discoveryPeerMacAddr);
        str += String.format(Locale.US, "======DeviceInfo Msg Stats=====\n%s", deviceInfoMessageCounter.toString());
        str += String.format(Locale.US, "=======LegacyAp Msg Stats======\n%s", legacyApMessageCounter.toString());

        return str;
    }
}

class SocketPeerStatistics {
    final MessageCounter managementSocketMessageCounter = new MessageCounter();
    final MessageCounter dataSocketMessageCounter = new MessageCounter();
    //public SocketPeer socketPeer = null;
    String socketPeerIpAddr = null;
    String socketPeerMacAddr = null;

    String getStatistics() {

        String str = "";
        str += String.format(Locale.US, "*******Socket Stats for Device [%s,%s]*******\n", socketPeerMacAddr, socketPeerIpAddr);
        str += String.format(Locale.US, "======Management Stats=====\n%s", managementSocketMessageCounter.toString());
        str += String.format(Locale.US, "=========Data Stats========\n%s", dataSocketMessageCounter.toString());

        return str;
    }
}

class MessageCounter {
    private final HashMap<Integer, Integer> lengthCounter = new HashMap<>();
    private long startTime = 0;

    void addLength(int length) {
        if (lengthCounter.size() == 0)
            setStartTime();

        if (lengthCounter.containsKey(length)) {
            int count = lengthCounter.get(length);
            lengthCounter.put(length, ++count);
        } else {
            lengthCounter.put(length, 1);
        }
    }

    private long getTotalLength() {
        long totalLength = 0;

        for (int keyLength :
                lengthCounter.keySet()) {
            totalLength += keyLength * lengthCounter.get(keyLength);
        }

        return totalLength;
    }

    int getTotalNumberOfMessages() {
        int num = 0;

        for (int keyLength :
                lengthCounter.keySet()) {
            num += lengthCounter.get(keyLength);
        }

        return num;
    }

    private void setStartTime() {
        startTime = System.currentTimeMillis();
    }


    float getBandwidth() {
        long curTime = System.currentTimeMillis();
        long timeDiff = curTime - startTime;

        return (getTotalLength() * 1000.0f) / (timeDiff * 1.0f);
    }

    public void reset() {
        lengthCounter.clear();
        startTime = 0;
    }

    @Override
    public String toString() {
        String str = "";

        str += String.format(Locale.US, "Total Number of Messages: %d\n", getTotalNumberOfMessages());
        str += String.format(Locale.US, "Total Length of Messages: %d\n", getTotalLength());
        str += String.format(Locale.US, "Bandwidth: %f Bytes/Sec\n\n", getBandwidth());
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
