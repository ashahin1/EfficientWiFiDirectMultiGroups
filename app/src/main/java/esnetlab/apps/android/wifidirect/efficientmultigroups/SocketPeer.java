package esnetlab.apps.android.wifidirect.efficientmultigroups;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by Ahmed on 4/9/2015.
 */

class SocketPeer {

    public String name;
    String deviceAddress;
    String ipAddress;
    private boolean isGroupOwner;
    SocketManager managementSocketManager = null;
    SocketManager dataSocketManager = null;
    SocketManager proxyManagementSocketManager = null;
    SocketManager proxyDataSocketManager = null;
    private int ttl;

    SocketPeer() {
    }

    public SocketPeer(SocketPeer socketPeer) {
        updatePeer(socketPeer);
    }

    SocketPeer(String name, String deviceAddress, String ipAddress, boolean isGroupOwner/*, boolean isConnected*/) {
        updatePeer(name, deviceAddress, ipAddress, isGroupOwner/*, isConnected*/);
    }

    static void closeSocketManager(SocketManager socketManager) {
        if (socketManager != null) {
            try {
                socketManager.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updatePeer(SocketPeer socketPeer) {
        updatePeer(socketPeer.name, socketPeer.deviceAddress, socketPeer.ipAddress, socketPeer.isGroupOwner);
    }

    void updatePeer(String name, String deviceAddress, String ipAddress, boolean isGroupOwner) {
        this.name = name;
        this.deviceAddress = deviceAddress;
        this.ipAddress = ipAddress;
        this.isGroupOwner = isGroupOwner;
        this.ttl = 30;
    }

    void decreaseTTL() {
        ttl -= 1;
    }

    int getTTL() {
        return ttl;
    }

    String getDataSocketRemoteIpAddress() {
        if (isConnectedToData())
            return dataSocketManager.getSocket().getInetAddress().getHostAddress();

        return null;
    }

    /**
     * Open a data socket connection to the peer.
     */
    void connectToDataPort(MessageTarget mTarget) {
        Thread handler = new ClientSocketHandler(mTarget.getHandler(),
                ipAddress, EfficientWiFiP2pGroupsActivity.mDataPort);
        handler.start();
    }

    public void connectToProxyDataPort(MessageTarget mTarget) {
        Thread handler = new ClientSocketHandler(mTarget.getHandler(),
                ipAddress, EfficientWiFiP2pGroupsActivity.mProxyDataPort);
        handler.start();
    }

    boolean isConnectedToManagement() {
        boolean pConnected = false;

        if (managementSocketManager != null) {
            Socket sc = managementSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    boolean isConnectedToData() {
        boolean pConnected = false;

        if (dataSocketManager != null) {
            Socket sc = dataSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    boolean isConnectedToProxyManagement() {
        boolean pConnected = false;

        if (proxyManagementSocketManager != null) {
            Socket sc = proxyManagementSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    boolean isConnectedToProxyData() {
        boolean pConnected = false;

        if (proxyDataSocketManager != null) {
            Socket sc = proxyDataSocketManager.getSocket();
            pConnected = sc.isConnected() && (!sc.isClosed());
        }

        return pConnected;
    }

    void removeSocketManagers() {
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

