package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Ahmed on 4/8/2015.
 */
public class SocketPeers {

    public static final String TAG = "SocketPeers";
    List<SocketPeer> socketPeerList;

    public SocketPeers() {
        socketPeerList = new ArrayList<SocketPeer>();
    }

    public void clear() {
        removeAllSocketManagers();
        socketPeerList.clear();
    }

    public void addPeer(SocketPeer socketPeer) {
        socketPeerList.add(socketPeer);
    }

    public void addPeer(String name, String macAddress, String ipAddress, boolean isGroupOwner/*, boolean isConnected*/) {
        SocketPeer socketPeer = new SocketPeer(name, macAddress, ipAddress, isGroupOwner/*, isConnected*/);
        socketPeerList.add(socketPeer);
    }

    public SocketPeer addOrUpdatePeer(String peerStr) {
        String pInfo[] = peerStr.split(",");
        String peerName = pInfo[0];
        String peerMacAddress = pInfo[1];
        String peerIpAddress = pInfo[2];
        boolean isGroupOwner = Boolean.valueOf(pInfo[3]);

        SocketPeer nPeer = getPeerByIpAddress(peerIpAddress);
        if (nPeer == null) {
            //peer not exists, so add it to the list
            nPeer = new SocketPeer(peerName, peerMacAddress, peerIpAddress, isGroupOwner);
            addPeer(nPeer);
        } else {
            //update the existing peer
            nPeer.updatePeer(peerName, peerMacAddress, peerIpAddress, isGroupOwner);
        }

        return nPeer;
    }

    public void removePeerByMacAddress(String peerMacAddress) {
        SocketPeer socketPeer = getPeerByMacAddress(peerMacAddress);
        if (socketPeer != null) {
            socketPeer.removeSocketManagers();
            socketPeerList.remove(socketPeer);
        }
    }

    public SocketPeer getPeerByMacAddress(String macAddress) {
        for (SocketPeer socketPeer : socketPeerList)
            if (socketPeer.deviceAddress.equalsIgnoreCase(macAddress))
                return socketPeer;
        return null;
    }

    public void removePeerByIpAddress(String ipAddress) {
        SocketPeer socketPeer = getPeerByIpAddress(ipAddress);
        if (socketPeer != null) {
            socketPeer.removeSocketManagers();
            socketPeerList.remove(socketPeer);
        }
    }

    public SocketPeer getPeerByIpAddress(String ipAddress) {
        for (SocketPeer socketPeer : socketPeerList)
            if (socketPeer.ipAddress.equalsIgnoreCase(ipAddress))
                return socketPeer;
        return null;
    }

    public void decreasePeersTTL() {
        for (SocketPeer socketPeer : socketPeerList) {
            socketPeer.decreaseTTL();
        }
    }

    public void prunePeers() {
        for (int i = socketPeerList.size() - 1; i >= 0; i--) {
            if (socketPeerList.get(i).getTTL() <= 0) {
                //The peer should be disconnected as we didn't hear any heart beat from a long time.
                socketPeerList.get(i).removeSocketManagers();
                Log.d(TAG, "Pruning inactive peer -> " + socketPeerList.get(i).toString());
                socketPeerList.remove(i);
            }
        }
    }

    public void removeDuplicatedDataSocketManagers() {
        String ip0, ip1, ip2;
        String ip0s[], ip1s[];
        int octet0, octet1;

        ip0 = Utilities.getWifiDirectIPAddress();
        if (ip0 != null) {
            ip0s = ip0.split("\\.");
            octet0 = Integer.valueOf(ip0s[3]);
            for (int i = socketPeerList.size() - 1; i >= 1; i--) {
                ip1 = socketPeerList.get(i).getDataSocketRemoteIpAddress();
                if (ip1 == null) continue;
                for (int j = i - 1; j >= 0; j--) {
                    ip2 = socketPeerList.get(j).getDataSocketRemoteIpAddress();
                    if (ip2 == null) continue;
                    ip1s = ip1.split("\\.");
                    octet1 = Integer.valueOf(ip1s[3]);

                    if (ip1.equalsIgnoreCase(ip2)) {
                        //Remove duplicates only if my ip address is greater than the peers being checked.
                        //This is necessary to avoid removing data sockets from both ends. (i.e other peer is trying to remove me as I am removing him)
                        if (octet0 > octet1) {
                            SocketPeer.closeSocketManager(socketPeerList.get(i).dataSocketManager);
                            socketPeerList.get(i).dataSocketManager = null;
                        }
                    }
                }
            }
        }
    }

    /*private void removeSocketManagers(SocketPeer socketPeer) {
        //close all management and data sockets and remove the socket managers.
        socketPeer.removeSocketManagers();
    }*/

    public void removeAllSocketManagers() {
        for (SocketPeer socketPeer : socketPeerList)
            socketPeer.removeSocketManagers();
    }

    public List<SocketPeer> getPeers() {
        return socketPeerList;
    }

    public List<SocketPeer> getConnectedPeers() {
        List<SocketPeer> cSocketPeers = new ArrayList<SocketPeer>();
        for (SocketPeer socketPeer : socketPeerList) {
            if (EfficientWiFiP2pGroupsActivity.p2pDevice != null)
                if (!socketPeer.deviceAddress.equalsIgnoreCase(EfficientWiFiP2pGroupsActivity.p2pDevice.deviceAddress))
                    if (socketPeer.isConnectedToData())
                        cSocketPeers.add(socketPeer);
        }
        return cSocketPeers;
    }

    public List<SocketPeer> getNotConnectedPeers() {
        List<SocketPeer> ncSocketPeers = new ArrayList<SocketPeer>();
        for (SocketPeer socketPeer : socketPeerList) {
            if (EfficientWiFiP2pGroupsActivity.p2pDevice != null)
                if (!socketPeer.deviceAddress.equalsIgnoreCase(EfficientWiFiP2pGroupsActivity.p2pDevice.deviceAddress))
                    if (!socketPeer.isConnectedToData())
                        ncSocketPeers.add(socketPeer);
        }
        return ncSocketPeers;
    }

    /**
     * Iterate through all nearby peers and connect to any unconnected one.
     */
    public void connectToAllPeersDataPorts(MessageTarget mTarget) {
        for (SocketPeer socketPeer : socketPeerList) {
            //sleep for a random time to avoid attempting to connect with the socketPeer at the same time it is trying to connect to this device.
            try {
                Thread.sleep(new Random().nextInt(20));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!socketPeer.deviceAddress.equalsIgnoreCase(EfficientWiFiP2pGroupsActivity.p2pDevice.deviceAddress))
                if (!socketPeer.isConnectedToData()) {
                    socketPeer.connectToDataPort(mTarget);
                    Log.d(TAG, "Connecting to socketPeer -> " + socketPeer.toString());
                }
        }
    }

    @Override
    public String toString() {
        String str = "";
        for (SocketPeer socketPeer : socketPeerList) {
            str += socketPeer.toString() + ";";
        }
        //remove the last semicolon
        if (!"".equals(str))
            str = str.substring(0, str.length() - 1);
        return str;
    }

    public String getPeerNameFromSocketManager(SocketManager socketManager) {
        SocketPeer socketPeer = getPeerFromSocketManager(socketManager);
        if (socketPeer != null)
            return socketPeer.name;
        return "";
    }

    public SocketPeer getPeerFromSocketManager(SocketManager socketManager) {
        for (SocketPeer socketPeer : socketPeerList)
            if (socketPeer.ipAddress.equalsIgnoreCase(socketManager.getSocket().getInetAddress().getHostAddress()))
                return socketPeer;
        return null;
    }

    public void addDataSocketManagerToPeer(SocketManager socketManager) {
        SocketPeer socketPeer = getPeerFromSocketManager(socketManager);
        if (socketPeer != null)
            socketPeer.dataSocketManager = socketManager;
    }

    public void addManagementSocketManagerToPeer(SocketManager socketManager) {
        addManagementSocketManagerToPeer(socketManager, false);
    }

    public void addManagementSocketManagerToPeer(SocketManager socketManager, boolean isGo) {
        SocketPeer socketPeer = getPeerFromSocketManager(socketManager);
        if (socketPeer != null)
            socketPeer.managementSocketManager = socketManager;
        else{
            //We are her handling special case, where the socket is already opened but we still
            //do not have the peer information stored (it should come after a successful exchange
            // between the GM and the GO).
            //Create a new dummy peer and add it to the current peers. This peer would be updated
            //with the correct information soon.
            socketPeer = new SocketPeer("N/A","N/A",
                    socketManager.getSocket().getInetAddress().getHostAddress(), isGo);
            socketPeer.managementSocketManager = socketManager;
            addPeer(socketPeer);
        }
    }

    public void addProxyDataSocketManagerToPeer(SocketManager socketManager) {
        SocketPeer socketPeer = getPeerFromSocketManager(socketManager);
        if (socketPeer != null)
            socketPeer.proxyDataSocketManager = socketManager;
    }

    public void addProxyManagementSocketManagerToPeer(SocketManager socketManager) {
        SocketPeer socketPeer = getPeerFromSocketManager(socketManager);
        if (socketPeer != null)
            socketPeer.proxyManagementSocketManager = socketManager;
    }

    public List<SocketManager> getOpenManagementSockets() {
        List<SocketManager> socketManagerList = new ArrayList<>();

        for (SocketPeer peer : socketPeerList) {
            if (peer.isConnectedToManagement())
                socketManagerList.add(peer.managementSocketManager);
        }

        return socketManagerList;
    }

    public List<SocketManager> getOpenDataSockets() {
        List<SocketManager> socketManagerList = new ArrayList<>();

        for (SocketPeer peer : socketPeerList) {
            if (peer.isConnectedToData())
                socketManagerList.add(peer.dataSocketManager);
        }

        return socketManagerList;
    }

    public List<SocketManager> getOpenProxyManagementSockets() {
        List<SocketManager> socketManagerList = new ArrayList<>();

        for (SocketPeer peer : socketPeerList) {
            if (peer.isConnectedToProxyManagement())
                socketManagerList.add(peer.proxyManagementSocketManager);
        }

        return socketManagerList;
    }

    public List<SocketManager> getOpenProxyDataSockets() {
        List<SocketManager> socketManagerList = new ArrayList<>();

        for (SocketPeer peer : socketPeerList) {
            if (peer.isConnectedToProxyData())
                socketManagerList.add(peer.proxyDataSocketManager);
        }

        return socketManagerList;
    }

    public int sendToAllDataSockets(String dataToSend, MessageType messageType) {
        int smCount = 0;
        for (SocketManager socketManager : getOpenDataSockets()) {
            socketManager.writeFormattedMessage(dataToSend, messageType);
            smCount++;
        }
        return smCount;
    }

    public int sendToAllManagmentSockets(String dataToSend, MessageType messageType) {
        int smCount = 0;
        for (SocketManager socketManager : getOpenManagementSockets()) {
            socketManager.writeFormattedMessage(dataToSend, messageType);
            smCount++;
        }
        return smCount;
    }

    public void sendToAllProxyDataSockets(String dataToSend, MessageType messageType) {
        for (SocketManager socketManager : getOpenProxyDataSockets()) {
            socketManager.writeFormattedMessage(dataToSend, messageType);
        }
    }

    public void sendToAllProxyManagmentSockets(String dataToSend, MessageType messageType) {
        for (SocketManager socketManager : getOpenProxyManagementSockets()) {
            socketManager.writeFormattedMessage(dataToSend, messageType);
        }
    }
}
