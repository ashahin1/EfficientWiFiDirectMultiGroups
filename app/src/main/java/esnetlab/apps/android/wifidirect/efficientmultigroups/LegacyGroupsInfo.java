package esnetlab.apps.android.wifidirect.efficientmultigroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by Ahmed on 4/9/2015.
 */
class LegacyGroupsInfo {
    private List<SocketPeerNearbyLegacy> peerNearbyLegacies = new ArrayList<>();
    List<LegacyApsCoverage> legacyApsCoverages = new ArrayList<>();

    public void add(SocketPeer socketPeer, String peerNearbyLegaciesString, String goDeviceId) {
        DiscoveryPeersInfo discoveryPeersInfo = new DiscoveryPeersInfo();
        discoveryPeersInfo.addOrUpdate(peerNearbyLegaciesString, goDeviceId);

        SocketPeerNearbyLegacy peerNearbyLegacy = getSocketPeerNearbyLegacy(socketPeer);

        if (peerNearbyLegacy != null) {
            peerNearbyLegacy.discoveryPeersInfo = discoveryPeersInfo;
        } else {
            SocketPeerNearbyLegacy legacy = new SocketPeerNearbyLegacy();
            legacy.socketPeer = socketPeer;
            legacy.discoveryPeersInfo = discoveryPeersInfo;
            peerNearbyLegacies.add(legacy);
        }
    }

    private SocketPeerNearbyLegacy getSocketPeerNearbyLegacy(SocketPeer socketPeer) {
        for (SocketPeerNearbyLegacy peerNearbyLegacy : peerNearbyLegacies) {
            if (peerNearbyLegacy.socketPeer == socketPeer)
                return peerNearbyLegacy;
        }
        return null;
    }

    String calculateCoverage() {
        String str = String.format(Locale.US, "%20s%20s\n", "SSID", "Assigned PM");
        str += String.format(Locale.US, "%40s\n", " ").replace(" ", "-");

        //Transform the peerNearbyLegacies list into legacyApsCoverages list

        //Get a list of all legacyAps
        List<String> legacyAps = getLegacyApsList();

        //Get socketPeers relation to legacyAps
        buildLegacyApCoverage(legacyAps);

        //Find for each LegacyAp the selected peer and the spare peer
        for (LegacyApsCoverage legacyApsCoverage : legacyApsCoverages) {
            if (legacyApsCoverage.socketPeers.size() > 0) {
                legacyApsCoverage.proxyPeer =
                        getSocketPeerNearbyLegacy(legacyApsCoverage.socketPeers.get(0));
                if (legacyApsCoverage.socketPeers != null) {
                    str += String.format(Locale.US
                            , "%20s%20s\n"
                            , legacyApsCoverage.legacyAp
                            , legacyApsCoverage.proxyPeer != null ? legacyApsCoverage.proxyPeer.socketPeer.toString() : null);
                }
            }
        }

        return str;
    }

    private void buildLegacyApCoverage(List<String> legacyAps) {
        legacyApsCoverages = new ArrayList<>();

        for (String legacyAp : legacyAps) {
            LegacyApsCoverage coverage = new LegacyApsCoverage();
            coverage.legacyAp = legacyAp;
            coverage.socketPeers = new ArrayList<>();

            for (SocketPeerNearbyLegacy nearbyLegacy : peerNearbyLegacies)
                if (nearbyLegacy.discoveryPeersInfo.getPeerInfo(legacyAp) != null)
                    coverage.socketPeers.add(nearbyLegacy.socketPeer);

            legacyApsCoverages.add(coverage);
        }
    }

    List<String> getLegacyApsList() {
        List<String> legacyAps = new ArrayList<>();
        for (SocketPeerNearbyLegacy peerNearbyLegacy : peerNearbyLegacies)
            for (DiscoveryPeerInfo peerInfo : peerNearbyLegacy.discoveryPeersInfo.peersInfo)
                if (!legacyAps.contains(peerInfo.deviceId))
                    legacyAps.add(peerInfo.deviceId);
        return legacyAps;
    }

    void clear() {
        peerNearbyLegacies.clear();
        legacyApsCoverages.clear();
    }
}

class SocketPeerNearbyLegacy {
    SocketPeer socketPeer = null;
    DiscoveryPeersInfo discoveryPeersInfo = null;
}

class LegacyApsCoverage {
    String legacyAp = null;
    List<SocketPeer> socketPeers = null;
    SocketPeerNearbyLegacy proxyPeer = null;
    public SocketPeerNearbyLegacy spareProxyPeer = null;

    boolean sendAssignmentForProxyPeer() {
        boolean done = false;

        if (legacyAp != null)
            if (proxyPeer != null)
                if (proxyPeer.socketPeer.isConnectedToManagement()) {
                    String info = proxyPeer.discoveryPeersInfo.getPeerLegacyInfoStr(legacyAp);
                    proxyPeer.socketPeer.managementSocketManager.writeFormattedMessage(
                            info, MessageType.MGMNT_GO_TO_GM_SELECTED_LEGACY);
                    done = true;
                }

        return done;
    }
}
