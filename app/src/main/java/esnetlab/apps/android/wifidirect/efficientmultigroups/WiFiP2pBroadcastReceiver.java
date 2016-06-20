package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;

/**
 * Created by Ahmed on 3/26/2015.
 */
public class WiFiP2pBroadcastReceiver extends BroadcastReceiver {
    private EfficientWiFiP2pGroupsActivity activity;
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;

    WiFiP2pBroadcastReceiver(WifiP2pManager mManager, WifiP2pManager.Channel mChannel, EfficientWiFiP2pGroupsActivity activity) {
        this.activity = activity;
        this.mManager = mManager;
        this.mChannel = mChannel;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        //activity.appendLogUiThread("p2p=========" + action + "========", true);

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Determine if Wifi P2P mode is enabled or not, alert
            // the Activity.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                activity.setIsWifiP2pEnabled(true);
            } else {
                activity.setIsWifiP2pEnabled(false);
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // The peer list has changed!  We should probably do something about
            // that.
            activity.requestP2pPeers();

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            //activity.resetP2pStructures();

            // Connection state changed!  We should probably do something about
            // that.
            if (mManager == null) {
                return;
            }

            final NetworkInfo networkInfo = intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            //activity.appendLogUiThread("p2p---" + networkInfo.toString() + "---", true);

            if (networkInfo.isConnected()) {

                // We are connected with the other device, request connection
                // info to find group owner IP

                mManager.requestConnectionInfo(mChannel, activity);
            }/* else {
                //activity.removeLegacyApService();
                //activity.resetP2pStructures();
            }*/

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            //DeviceListFragment fragment = (DeviceListFragment) activity.getFragmentManager().findFragmentById(R.id.frag_list);
            //fragment.updateThisDevice((WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
            activity.updateThisDevice(intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
        } /*else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
            final int dState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);

            if (dState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED)
                activity.appendLogUiThread("p2p---------Discovery Started-----");
            else activity.appendLogUiThread("p2p---------Discovery Stoped-----");
        }*/
    }
}
