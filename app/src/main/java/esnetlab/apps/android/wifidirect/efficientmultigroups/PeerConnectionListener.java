package esnetlab.apps.android.wifidirect.efficientmultigroups;

/**
 * Created by Ahmed on 4/8/2015.
 */
interface PeerConnectionListener {

    void onPeerDataConnected();

    void onPeerManagementConnected();

    void onPeerProxyDataConnected();

    void onPeerProxyManagementConnected();

    void onPeerDataDisconnected();

    void onPeerManagementDisconnected();

    void onPeerProxyDataDisconnected();

    void onPeerProxyManagementDisconnected();
}
