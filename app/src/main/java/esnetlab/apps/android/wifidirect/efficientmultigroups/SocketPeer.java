package esnetlab.apps.android.wifidirect.efficientmultigroups;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by Ahmed on 4/9/2015.
 */

class SocketPeer {

    public String name;
    public String deviceAddress;
    public String ipAddress;
    public boolean isGroupOwner;
    public SocketManager managementSocketManager = null;
    public SocketManager dataSocketManager = null;
    public SocketManager proxyManagementSocketManager = null;
    public SocketManager proxyDataSocketManager = null;
    private int ttl;

    public SocketPeer() {
    }

    public SocketPeer(SocketPeer socketPeer) {
        updatePeer(socketPeer);
    }

    public SocketPeer(String name, String deviceAddress, String ipAddress, boolean isGroupOwner/*, boolean isConnected*/) {
        updatePeer(name, deviceAddress, ipAddress, isGroupOwner/*, isConnected*/);
    }

    public static void closeSocketManager(SocketManager socketManager) {
        if (socketManager != null) {
            try {
                socketManager.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updatePeer(SocketPeer socketPeer) {
        updatePeer(socketPeer.name, socketPeer.deviceAddress, socketPeer.ipAddress, socketPeer.isGroupOwner);
    }

    public void updatePeer(String name, String deviceAddress, String ipAddress, boolean isGroupOwner) {
        this.name = name;
        this.deviceAddress = deviceAddress;
        this.ipAddress = ipAddress;
        this.isGroupOwner = isGroupOwner;
        this.ttl = 30;
    }

    public void decreaseTTL() {
        ttl -= 1;
    }

    public int getTTL() {
        return ttl;
    }

    public String getDataSocketRemoteIpAddress() {
        if (isConnectedToData())
            return dataSocketManager.getSocket().getInetAddress().getHostAddress();

        return null;
    }

    /**
     * Open a data socket connection to the peer.
     */
    public void connectToDataPort(MessageTarget mTarget) {
        Thread handler = new ClientSocketHandler(mTarget.getHandler(),
                ipAddress, EfficientWiFiP2pGroupsActivity.mDataPort);
        handler.start();
    }

    public void connectToProxyDataPort(MessageTarget mTarget) {
        Thread handler = new ClientSocketHandler(mTarget.getHandler(),
                ipAddress, EfficientWiFiP2pGroupsActivity.mProxyDataPort);
        handler.start();
    }

    public boolean isConnectedToManagement() {
        boolean pConnected = false;

        if (managementSocketManager != null) {
            Socket sc = managementSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    public boolean isConnectedToData() {
        boolean pConnected = false;

        if (dataSocketManager != null) {
            Socket sc = dataSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    public boolean isConnectedToProxyManagement() {
        boolean pConnected = false;

        if (proxyManagementSocketManager != null) {
            Socket sc = proxyManagementSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    public boolean isConnectedToProxyData() {
        boolean pConnected = false;

        if (proxyDataSocketManager != null) {
            Socket sc = proxyDataSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    public void removeSocketManagers() {
        closeSocketManager(dataSocketManager);
        closeSocketManager(managementSocketManager);
        closeSocketManager(proxyDataSocketManager);
        closeSocketManager(proxyManagementSocketManager);

        dataSocketManager = null;
        managementSocketManager = null;
        proxyDataSocketManager = null;
        proxyManagementSocketManager = null;
    }

    @Override
    public String toString() {
        String str;
        str = name + ","
                + deviceAddress + ","
                + ipAddress + ","
                + (isGroupOwner ? "1" : "0")
        ;
        return str;
    }
}

