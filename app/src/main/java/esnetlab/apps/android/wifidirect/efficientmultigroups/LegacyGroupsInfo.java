package esnetlab.apps.android.wifidirect.efficientmultigroups;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ahmed on 4/9/2015.
 */
public class LegacyGroupsInfo {
    public List<SocketPeerNearbyLegacy> peerNearbyLegacies = new ArrayList<>();
    public List<LegacyApsCoverage> legacyApsCoverages = new ArrayList<>();

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

    public SocketPeerNearbyLegacy getSocketPeerNearbyLegacy(SocketPeer socketPeer) {
        for (SocketPeerNearbyLegacy peerNearbyLegacy : peerNearbyLegacies) {
            if (peerNearbyLegacy.socketPeer == socketPeer)
                return peerNearbyLegacy;
        }
        return null;
    }

    public void calculateCoverage() {
        //Transform the peerNearbyLegacies list into legacyApsCoverages list

        //Get a list of all legacyAps
        List<String> legacyAps = getLegacyApsList();

        //Get socketPeers relation to legacyAps
        buildLegacyApCoverage(legacyAps);

        //Find for each LegacyAp the selected peer and the spare peer
        for (LegacyApsCoverage legacyApsCoverage : legacyApsCoverages) {
            if (legacyApsCoverage.socketPeers.size() > 0)
                legacyApsCoverage.proxyPeer =
                        getSocketPeerNearbyLegacy(legacyApsCoverage.socketPeers.get(0));
        }
    }

    public void buildLegacyApCoverage(List<String> legacyAps) {
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

    public List<String> getLegacyApsList() {
        List<String> legacyAps = new ArrayList<>();
        for (SocketPeerNearbyLegacy peerNearbyLegacy : peerNearbyLegacies)
            for (DiscoveryPeerInfo peerInfo : peerNearbyLegacy.discoveryPeersInfo.peersInfo)
                if (!legacyAps.contains(peerInfo.deviceId))
                    legacyAps.add(peerInfo.deviceId);
        return legacyAps;
    }

    public void clear() {
        peerNearbyLegacies.clear();
        legacyApsCoverages.clear();
    }
}

class SocketPeerNearbyLegacy {
    public SocketPeer socketPeer = null;
    public DiscoveryPeersInfo discoveryPeersInfo = null;
}

class LegacyApsCoverage {
    public String legacyAp = null;
    public List<SocketPeer> socketPeers = null;
    public SocketPeerNearbyLegacy proxyPeer = null;
    public SocketPeerNearbyLegacy spareProxyPeer = null;

    public boolean sendAssignmentForProxyPeer() {
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
