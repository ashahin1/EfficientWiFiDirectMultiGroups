package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class EfficientWiFiP2pGroupsActivity extends AppCompatActivity implements WifiP2pManager.ConnectionInfoListener, Handler.Callback, MessageTarget, ProtocolConstants {

    public static final String TAG = "EfficientWiFiP2pGroups";

    //Port parameters
    public static int mMgmntPort = 4546;
    public static int mDataPort = 4545;
    public static int mProxyMgmntPort = 4544;
    public static int mProxyDataPort = 4543;

    //Ranking parameters
    public static float mRankAlpha = 0.34f;
    public static float mRankBeta = 0.33f;
    public static float mRankGamma = 0.33f;
    public static float mRankMaxCapacity = 4000.0f;

    //Protocol timing parameters
    public static int mSendMyInfPeriod = 2000;
    public static int mSendPeersInfoPeriod = 4000;
    public static int mDiscoverServicesPeriod = 6000;
    public static int mAddServicesPeriod = 3000;
    public static int mDeclareGoPeriod = 30000;
    public static int mDecideGroupPeriod = 15000;
    public static int mDecideProxyPeriod = 40000;
    public static int mSendNearbyLegacyApsInfoPeriod = 3000;
    public static int mSendTearDownPeriod = 300000;

    public static WifiP2pInfo p2pInfo = null;
    public static WifiP2pGroup p2pGroup = null;
    public static WifiP2pDevice p2pDevice = null;
    public static Boolean wifiScanCompleted = false;
    public static Boolean wifiSupplicantAssociated = false;
    public SharedPreferences sharedPreferences;
    private final IntentFilter wifiIntentFilter = new IntentFilter();
    private final IntentFilter wifiP2pIntentFilter = new IntentFilter();
    private final DiscoveryPeersInfo discoveryPeersInfo = new DiscoveryPeersInfo();
    private final SocketPeers groupSocketPeers = new SocketPeers();
    private final LegacyGroupsInfo legacyGroupsInfo = new LegacyGroupsInfo();
    private final HashMap<String, Map<String, String>> buddies = new HashMap<>();
    private final PerformanceAnalysis performanceAnalysis = new PerformanceAnalysis();

    public ThisDeviceState thisDeviceState = ThisDeviceState.STARTED;

    public int myProposedIP = DiscoveryPeerInfo.generateProposedIP();

    TextView txtLog;
    EditText txtSend;
    TextView txtReceived;

    boolean requestRun = false;

    Thread th1, th2;
    Thread mgntHandler = null;
    Thread dataHandler = null;
    Thread proxyMgntHandler = null;
    Thread proxyDataHandler = null;

    private BatteryInformation batteryInfo = new BatteryInformation();
    private WifiManager wifiManager;
    private WifiBroadcastReceiver wifiBroadcastReceiver;
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiP2pChannel;
    private WiFiP2pBroadcastReceiver wifiP2pBroadcastReceiver;
    private boolean isWifiP2pEnabled = false;

    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pDnsSdServiceInfo serviceDeviceInfo;
    private WifiP2pDnsSdServiceInfo serviceInfoLegacyAp;

    private SocketPeer proxySocketPeer = new SocketPeer();

    private boolean lastWifiState = false;

    private Timer sendMyInfoTimer = new Timer("sendMyInfoTimer");
    private Timer sendPeersInfoTimer = new Timer("sendPeersInfoTimer");
    private Timer discoverServicesTimer = new Timer("discoverServicesTimer");
    private Timer addServicesTimer = new Timer("addServicesTimer");

    //Handlers
    private Handler handler = new Handler(this);
    private final Handler declareGoHandler = new Handler();
    private final Handler decideGroupHandler = new Handler();
    private final Handler decideProxyHandler = new Handler();
    private final Handler sendTearDownHandler = new Handler();
    private final Handler tearDownHandler = new Handler();
    private final Handler declareGoAcceptingGMsHandler = new Handler();
    private final Handler sendNearbyLegacyApsInfoHandler = new Handler();

    //Runnables
    private final Runnable declareGoRunnable = new Runnable() {
        @Override
        public void run() {
            declareGo();
        }
    };
    private final Runnable decideGroupRunnable = new Runnable() {
        @Override
        public void run() {
            decideGroupAndConnect();
        }
    };
    private final Runnable declareGoAcceptingGMsRunnable = new Runnable() {
        @Override
        public void run() {
            declareGoAcceptingGMs();
        }
    };
    private final Runnable decideProxyRunnable = new Runnable() {
        @Override
        public void run() {
            decideProxiesAndInformMembers();
        }
    };
    private final Runnable sendNearbyLegacyApsInfoRunnable = new Runnable() {
        @Override
        public void run() {
            sendNearbyLegacyApsInfo();
        }
    };
    private final Runnable tearDownRunnable = new Runnable() {
        @Override
        public void run() {
            tearDownGroupAndReRun();
        }
    };
    private final Runnable sendTearDownRunnable = new Runnable() {
        @Override
        public void run() {
            sendTearDownToMembers();
        }
    };
    //End of Runnables

    public Handler getHandler() {
        return handler;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupPreferences();
        //Force always on screen.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        thisDeviceState = ThisDeviceState.STARTED;
        setupNetworking();
        setupControls();
    }

    private void setupPreferences() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences != null) {
            mMgmntPort = Integer.parseInt(sharedPreferences.getString(PREF_MGMNT_PORT, String.valueOf(mMgmntPort)));
            mDataPort = Integer.parseInt(sharedPreferences.getString(PREF_DATA_PORT, String.valueOf(mDataPort)));
            mProxyMgmntPort = Integer.parseInt(sharedPreferences.getString(PREF_PROXY_MGMNT_PORT, String.valueOf(mProxyMgmntPort)));
            mProxyDataPort = Integer.parseInt(sharedPreferences.getString(PREF_PROXY_DATA_PORT, String.valueOf(mProxyDataPort)));

            mRankAlpha = Float.parseFloat(sharedPreferences.getString(PREF_RANK_ALPHA, String.valueOf(mRankAlpha)));
            mRankBeta = Float.parseFloat(sharedPreferences.getString(PREF_RANK_BETA, String.valueOf(mRankBeta)));
            mRankGamma = Float.parseFloat(sharedPreferences.getString(PREF_RANK_GAMMA, String.valueOf(mRankGamma)));
            mRankMaxCapacity = Float.parseFloat(sharedPreferences.getString(PREF_RANK_MAX_CAPACITY, String.valueOf(mRankMaxCapacity)));

            mSendMyInfPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_MY_INF_PERIOD, String.valueOf(mSendMyInfPeriod)));
            mSendPeersInfoPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_PEERS_INFO_PERIOD, String.valueOf(mSendPeersInfoPeriod)));
            mDiscoverServicesPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DISCOVER_SERVICES_PERIOD, String.valueOf(mDiscoverServicesPeriod)));
            mAddServicesPeriod = Integer.parseInt(sharedPreferences.getString(PREF_ADD_SERVICES_PERIOD, String.valueOf(mAddServicesPeriod)));
            mDeclareGoPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DECLARE_GO_PERIOD, String.valueOf(mDeclareGoPeriod)));
            mDecideGroupPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DECIDE_GROUP_PERIOD, String.valueOf(mDecideGroupPeriod)));
            mDecideProxyPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DECIDE_PROXY_PERIOD, String.valueOf(mDecideProxyPeriod)));
            mSendNearbyLegacyApsInfoPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_NEARBY_LEGACY_APS_INFO_PERIOD, String.valueOf(mSendNearbyLegacyApsInfoPeriod)));
            mSendTearDownPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_TEAR_DOWN_PERIOD, String.valueOf(mSendTearDownPeriod)));
        }
    }

    private void setupControls() {

        txtLog = (TextView) findViewById(R.id.txt_log);
        txtSend = (EditText) findViewById(R.id.txt_send);
        txtReceived = (TextView) findViewById(R.id.txt_received);

        txtReceived.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ((TextView) v).setText("");
                return false;
            }
        });

        final Button btnSend = (Button) findViewById(R.id.btn_send);
        if (btnSend != null) {
            btnSend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String dataToSend = txtSend.getText().toString();
                    if (!dataToSend.equals("")) {
                        int count = groupSocketPeers.sendToAllDataSockets(dataToSend, MessageType.DATA_GM_TO_GROUP);
                        performanceAnalysis.sentDataSocketMessagesCount += count;
                        forwardIfMeIsProxy(dataToSend);
                        txtReceived.append("Data msg Sent -> [ME]: " + dataToSend + "\n");
                        txtSend.setText("");
                        txtSend.clearFocus();
                    }
                }
            });
            //registerForContextMenu(btnSend);
        }

        final FloatingActionButton btnFab = (FloatingActionButton) findViewById(R.id.fab);
        registerForContextMenu(btnFab);

        //btnFab.createContextMenu();
    }

    private void setupNetworking() {
        wifiIntentFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiIntentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        wifiIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);//  Indicates a change in the Wi-Fi P2P status.
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION); // Indicates a change in the list of available peers.
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);// Indicates the state of Wi-Fi P2P connectivity has changed.
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);// Indicates this device's details have changed.
        wifiP2pIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);//Indicates that peer discovery has either started or stopped.
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        wifiP2pChannel = wifiP2pManager.initialize(this, getMainLooper(), null);

        //Enable WiFi
        wifiManager.setWifiEnabled(true);
        //Clean the WiFi configured networks
        removeConfiguredLegacyAPs("++++++++++++");
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.fab_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_create_group:
                if (checkGroupFormedByMe()) {
                    removeWiFiP2pGroup(true);
                    item.setTitle("Create Group");
                } else {
                    createWifiP2pGroup();
                    item.setTitle("Remove Group");
                }
                return true;
            case R.id.action_discover_peers:
                //discoverPeers();
                appendLogUiThread(performanceAnalysis.getStatistics(""));
                return true;
            case R.id.action_create_service:
                removeDeviceInfoService();
                sleep(1000);
                createDeviceInfoService();
                return true;
            case R.id.action_list_services:
                stopDiscoveringServices();
                sleep(1000);
                discoverServices();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        wifiBroadcastReceiver = new WifiBroadcastReceiver();
        registerReceiver(wifiBroadcastReceiver, wifiIntentFilter);
        wifiP2pBroadcastReceiver = new WiFiP2pBroadcastReceiver(wifiP2pManager, wifiP2pChannel, this);
        registerReceiver(wifiP2pBroadcastReceiver, wifiP2pIntentFilter);

        requestRun = true;

        stopTimers();
        startTimers();
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(wifiBroadcastReceiver);
        unregisterReceiver(wifiP2pBroadcastReceiver);

        requestRun = false;

        stopTimers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tearDownGroupAndReRun(false);

        requestRun = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /*@Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus) {
            tapAcceptInvitationAutomatically();
        }
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            showSettingsDialog();
        } else if (id == R.id.action_toggle_wifi) {
            toggleWiFi();
        } else if (id == R.id.action_clear_log) {
            txtLog.setText("");
        }

        return super.onOptionsItemSelected(item);
    }

    public void showSettingsDialog() {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivity(i);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        p2pInfo = info;
        // InetAddress from WifiP2pInfo struct.
        InetAddress groupOwnerAddress = info.groupOwnerAddress;
        // After the group negotiation, we can determine the group owner.
        if (info.groupFormed && info.isGroupOwner) {

            requestGroupInfo();
            declareGoHandler.removeCallbacks(declareGoRunnable);
            decideGroupHandler.removeCallbacks(decideGroupRunnable);

            /*
            //Handle the case,when the group was formed before opening the program
            if (thisDeviceState != ThisDeviceState.GO_ACCEPTING_CONNECTIONS) {
                thisDeviceState = ThisDeviceState.GO_ACCEPTING_CONNECTIONS;
                sendTearDownHandler.postDelayed(sendTearDownRunnable, mSendTearDownPeriod);
            }*/

            appendLog("Connected as group owner -> Opening Management Sockets");

            //Start a socket connection handler for group management operations
            if (!connectManagementAsServer()) return;

            //TODO Open Proxy sockets for data and management
            if (!connectProxyManagementAsServer()) return;
            if (!connectProxyDataAsServer()) return;

        } else if (info.groupFormed) {
            /*
            //Add the Go info to the Socket Peers List, otherwise we won't be able to
            // reference its socket managers
            String goInfo = getPeerInfoString(groupOwnerAddress.getHostAddress(), true);
            groupSocketPeers.addOrUpdatePeer(goInfo);
            */

            declareGoHandler.removeCallbacks(declareGoRunnable);
            decideGroupHandler.removeCallbacks(decideGroupRunnable);
            //Handle the case when the device is connected to a group before opening the program
            if (thisDeviceState != ThisDeviceState.GM_COMMUNICATING_WITH_GO) {
                thisDeviceState = ThisDeviceState.GM_COMMUNICATING_WITH_GO;
            }

            appendLog("Connected as peer -> Connecting to Management Socket");
            if (!connectManagementAsClient(groupOwnerAddress)) return;

            sendNearbyLegacyApsInfoHandler.postDelayed(sendNearbyLegacyApsInfoRunnable,
                    mSendNearbyLegacyApsInfoPeriod);
        }

        if (info.groupFormed) {
            //Start a socket connection handler for Data exchange. This is done for both GO and non GO.
            if (!connectDataAsServer()) return;
        }
    }

    public boolean connectDataAsServer() {
        try {
            if (dataHandler == null) {
                dataHandler = new GroupDataSocketHandler(this.getHandler());
                dataHandler.start();
            } else if (!dataHandler.isAlive())
                dataHandler.start();

        } catch (Exception e) {
            appendLog("Failed to create a DataSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean connectManagementAsServer() {
        try {
            if (mgntHandler == null) {
                mgntHandler = new GroupManagementSocketHandler(this.getHandler());
                mgntHandler.start();
            } else if (!mgntHandler.isAlive())
                mgntHandler.start();
        } catch (Exception e) {
            appendLog("Failed to create a GroupOwnerSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean connectManagementAsClient(InetAddress groupOwnerAddress) {
        try {
            if (mgntHandler == null) {
                mgntHandler = new ClientSocketHandler(this.getHandler(),
                        groupOwnerAddress, mMgmntPort);
                mgntHandler.start();
            } else //TODO if (!mgntHandler.isAlive())
            {
                //mgntHandler.start();
                ((ClientSocketHandler) mgntHandler).getSocketManager().getSocket().close();
                mgntHandler = new ClientSocketHandler(this.getHandler(),
                        groupOwnerAddress, mMgmntPort);
                mgntHandler.start();
            }

        } catch (Exception e) {
            appendLog("Failed to create a ClientSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean connectProxyDataAsServer() {
        try {
            if (proxyDataHandler == null) {
                proxyDataHandler = new ProxyDataSocketHandler(this.getHandler());
                proxyDataHandler.start();
            } else if (!proxyDataHandler.isAlive())
                proxyDataHandler.start();

        } catch (Exception e) {
            appendLog("Failed to create a ProxyDataSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean connectProxyManagementAsServer() {
        try {
            if (proxyMgntHandler == null) {
                proxyMgntHandler = new ProxyManagementSocketHandler(this.getHandler());
                proxyMgntHandler.start();
            } else if (!proxyMgntHandler.isAlive())
                proxyMgntHandler.start();
        } catch (Exception e) {
            appendLog("Failed to create a GroupOwnerSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    public void requestGroupInfo() {
        wifiP2pManager.requestGroupInfo(wifiP2pChannel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                p2pGroup = group;
                if (p2pGroup.isGroupOwner()) {
                    String legacySSID = group.getNetworkName();
                    String legacyPassPhrase = group.getPassphrase();
                    appendLog("LegacySSID -> " + legacySSID);
                    appendLog("LegacyPassPhrase -> " + legacyPassPhrase);

                    createLegacyApService();
                }
            }
        });
    }

    public void toggleWiFi() {
        if (wifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
            wifiManager.setWifiEnabled(true);
            Toast.makeText(
                    this,
                    "Enabling Wi-Fi; this is required for Wi-Fi direct to work",
                    Toast.LENGTH_SHORT).show();
        } else {
            wifiManager.setWifiEnabled(false);
            Toast.makeText(this,
                    "Disabling Wi-Fi; this disables Wi-Fi direct functionality",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public synchronized void setIsWifiP2pEnabled(boolean state) {
        lastWifiState = isWifiP2pEnabled;
        isWifiP2pEnabled = state;

        if (!state)
            //Wifi is disabled, so tearDown everything until it is enabled
            tearDownGroupAndReRun(false);
        else if (!lastWifiState && state)
            //We are sure now that the change in state is from disabled to enabled, so re-run again
            tearDownGroupAndReRun();
    }

    public void removeWiFiP2pGroup() {
        removeWiFiP2pGroup(false);
    }

    public void removeWiFiP2pGroup(Boolean removeService) {
        if (wifiP2pManager != null) {
            wifiP2pManager.removeGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    appendLog("Group removed successfully.");
                }

                @Override
                public void onFailure(int reason) {
                    appendLog("Group failed to remove");
                }
            });

            if (removeService)
                removeLegacyApService();
        }
    }

    private boolean checkGroupFormedByMe() {
        if (p2pInfo != null) {
            return p2pInfo.groupFormed && p2pInfo.isGroupOwner;
        }
        return false;
    }

    public void createWifiP2pGroup() {
        if (isWifiP2pEnabled) {
            if (wifiP2pManager != null)
                wifiP2pManager.createGroup(wifiP2pChannel, new
                        WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                thisDeviceState = ThisDeviceState.GO_SENDING_LEGACY_INFO;
                                declareGoAcceptingGMsHandler.postDelayed(declareGoAcceptingGMsRunnable, mDecideGroupPeriod);
                                decideProxyHandler.postDelayed(decideProxyRunnable, mDecideProxyPeriod);
                                sendTearDownHandler.postDelayed(sendTearDownRunnable, mSendTearDownPeriod);
                                appendLog("Group created successfully.");
                            }

                            @Override
                            public void onFailure(int reason) {
                                //start over again
                                tearDownGroupAndReRun();
                                appendLog("Group failed to create.");
                            }
                        });
        }
    }

    private void discoverPeers() {
        if (isWifiP2pEnabled)
            if (wifiP2pManager != null)
                wifiP2pManager.discoverPeers(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        appendLog("Peers discovery started succeffuly.");
                    }

                    @Override
                    public void onFailure(int reason) {
                        appendLog("Peers discovery failed to start.");
                    }
                });
    }

    private synchronized void encryptDecryptRecordElement(Map<String, String> record, Boolean encrypt, String recordElement) {
        String oldString = record.get(recordElement);
        String newString = encrypt ? getEncryptedString(oldString) : getDecryptedString(oldString);
        record.remove(recordElement);
        record.put(recordElement, newString);
    }

    private String getEncryptedString(String plainText) {
        String encryptedText;
        encryptedText = SecurityHelper.encodeString(plainText);
        return encryptedText;
    }

    private String getDecryptedString(String encryptedText/*, String saltStr*/) {
        String plainText;
        plainText = SecurityHelper.decodeString(encryptedText);
        return plainText;
    }

    private synchronized void clearAllLocalServices() {
        if (wifiP2pManager != null) {
            wifiP2pManager.clearLocalServices(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onFailure(int reason) {
                }
            });
        }
    }

    private synchronized void clearAllServiceRequests() {
        if (wifiP2pManager != null) {
            wifiP2pManager.clearServiceRequests(wifiP2pChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onFailure(int reason) {
                }
            });
        }
    }

    private synchronized void createDeviceInfoService() {
        batteryInfo = batteryInfo.getBatteryStats(getApplicationContext());
        Map<String, String> record = new HashMap<>();

        //Declare the type as "0" which means a DeviceInfo service record
        record.put(RECORD_TYPE, RECORD_TYPE_DEVICE_INFO);
        record.put(RECORD_LEVEL, Integer.toString(batteryInfo.level));
        record.put(RECORD_CAPACITY, Integer.toString(batteryInfo.capacity));
        record.put(RECORD_CHARGING, Boolean.toString(batteryInfo.isCharging));
        record.put(RECORD_PROPOSED_IP, Integer.toString(myProposedIP));

        serviceDeviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        wifiP2pManager.addLocalService(wifiP2pChannel, serviceDeviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                appendLogUiThread("Service DeviceInfo added successfully", true);
            }

            @Override
            public void onFailure(int arg0) {
                appendLogUiThread("Service DeviceInfo failed to add");
            }
        });
    }

    private void removeDeviceInfoService() {
        if (serviceDeviceInfo != null)
            wifiP2pManager.removeLocalService(wifiP2pChannel, serviceDeviceInfo, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    appendLogUiThread("Remove DeviceInfo local service succeeded");
                    serviceDeviceInfo = null;
                }

                @Override
                public void onFailure(int reason) {
                    appendLogUiThread("Remove DeviceInfo local service failed");
                }
            });
    }

    private synchronized void createLegacyApService() {
        if (p2pGroup != null) {
            Map<String, String> record = new HashMap<>();

            String legacySSID = p2pGroup.getNetworkName();
            String legacyPassPhrase = p2pGroup.getPassphrase();
            //Declare the type as "1" which means a LegacyAP service record
            record.put(RECORD_TYPE, RECORD_TYPE_LEGACY_AP);
            record.put(RECORD_SSID, legacySSID);
            record.put(RECORD_KEY, legacyPassPhrase);

            encryptDecryptRecordElement(record, true, RECORD_KEY);

            serviceInfoLegacyAp =
                    WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
            wifiP2pManager.addLocalService(wifiP2pChannel, serviceInfoLegacyAp, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    appendLogUiThread("Service LegacyAP added successfully", true);
                }

                @Override
                public void onFailure(int arg0) {
                    appendLogUiThread("Service LegacyAP failed to add");
                }
            });
        }
    }

    public void removeLegacyApService() {
        if (serviceInfoLegacyAp != null)
            wifiP2pManager.removeLocalService(wifiP2pChannel, serviceInfoLegacyAp, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    appendLogUiThread("Remove LegacyAP local service succeeded");
                    serviceInfoLegacyAp = null;
                }

                @Override
                public void onFailure(int reason) {
                    appendLogUiThread("Remove LegacyAP local service failed");
                }
            });
    }

    private synchronized void discoverServices() {
        WifiP2pManager.DnsSdTxtRecordListener txtListener =
                new WifiP2pManager.DnsSdTxtRecordListener() {
                    @Override
                    public void onDnsSdTxtRecordAvailable(String fullDomainName,
                                                          Map<String, String> record,
                                                          WifiP2pDevice srcDevice) {
                        if (record.containsKey(RECORD_KEY))
                            encryptDecryptRecordElement(record, false, RECORD_KEY);
                        buddies.put(srcDevice.deviceAddress, record);
                        appendLogUiThread("DnsSdTxtRecord available ->"
                                + "[" + srcDevice.deviceName + "] " + record.toString());
                    }
                };

        WifiP2pManager.DnsSdServiceResponseListener servListener =
                new WifiP2pManager.DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType,
                                                        WifiP2pDevice srcDevice) {

                        if (instanceName.equals(SERVICE_INSTANCE)) {
                            if (buddies.containsKey(srcDevice.deviceAddress)) {
                                //Store/update the record in DiscoveryPeers structure for later use
                                Map<String, String> rec = buddies.get(srcDevice.deviceAddress);
                                boolean groupInfoSeenBefore =
                                        discoveryPeersInfo.addOrUpdate(srcDevice.deviceAddress, rec);
                                discoveryPeersInfo.addDevice(srcDevice);
                                //TODO: Fix the length
                                int length = instanceName.length()
                                        + registrationType.length()
                                        + getRecLength(rec);
                                performanceAnalysis.addDiscoveryStatistic(srcDevice.deviceAddress
                                        , length
                                        , isDeviceInfoRecord(rec));

                                //If I am in collecting device info phase I should check if my
                                // proposed IP is conflicting with other devices or not.
                                if (discoveryPeersInfo.isMyProposedIpConflicting(myProposedIP)) {
                                    performanceAnalysis.conflictIpCount++;
                                    myProposedIP = discoveryPeersInfo.getConflictFreeIP();
                                }

                                // If a new group info (SSID, Key) is received while I am collecting
                                // battery info, the device should declare itself as GM and start
                                // selecting a group. If the device is already in the group selection
                                // phase when it received the new group info, it has to restart the
                                // phase to get a chance to do a better decision.
                                if (!groupInfoSeenBefore)
                                    checkForDiscoveredGroups(rec);
                            }
                            appendLogUiThread("onDnsServiceAvailable -> success -> " + srcDevice.deviceName);
                        } else {
                            appendLogUiThread("onDnsServiceAvailable -> unknown -> " + instanceName);
                        }
                    }

                    private int getRecLength(Map<String, String> rec) {
                        int len = 0;
                        for (String key : rec.keySet()) {
                            len += key.length() + rec.get(key).length();
                        }
                        return len;
                    }
                };

        wifiP2pManager.setDnsSdResponseListeners(wifiP2pChannel, servListener, txtListener);

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        wifiP2pManager.addServiceRequest(wifiP2pChannel,
                serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        // Success!
                        appendLogUiThread("Add service request succeeded.", true);
                    }

                    @Override
                    public void onFailure(int code) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                        appendLogUiThread("Add service request failed.");
                    }
                });

        wifiP2pManager.discoverServices(wifiP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // Success!
                appendLogUiThread("Discover services succeeded.", true);
            }

            @Override
            public void onFailure(int code) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                appendLogUiThread("Discover services failed.");
            }
        });
    }

    private void stopDiscoveringServices() {
        if (serviceRequest != null)
            wifiP2pManager.removeServiceRequest(wifiP2pChannel, serviceRequest,
                    new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            appendLogUiThread("Removed previous service request succeeded");
                        }

                        @Override
                        public void onFailure(int reason) {
                            appendLogUiThread("Remove previous service request failed");
                        }
                    });
    }

    private synchronized void checkForDiscoveredGroups(Map<String, String> record) {
        if (thisDeviceState == ThisDeviceState.COLLECTING_DEVICE_INFO
                || thisDeviceState == ThisDeviceState.GM_SELECTING_GO)
            if (isLegacyApRecord(record))
                if (record.containsKey(RECORD_KEY)) {
                    String ssid = record.get(RECORD_SSID);
                    String key = record.get(RECORD_KEY);

                    if ((ssid != null) && (key != null)) {
                        appendLogUiThread("FOUND GROUPS NEARBY ......................");
                        declareGoHandler.removeCallbacks(declareGoRunnable);
                        declareGM();
                    }
                }
    }

    private boolean isLegacyApRecord(Map<String, String> record) {
        if (record.containsKey(RECORD_TYPE))
            if (record.get(RECORD_TYPE).equals(RECORD_TYPE_LEGACY_AP)) {
                return true;
            }
        return false;
    }

    private boolean isDeviceInfoRecord(Map<String, String> record){
        if (record.containsKey(RECORD_TYPE))
            if (record.get(RECORD_TYPE).equals(RECORD_TYPE_DEVICE_INFO)) {
                return true;
            }
        return false;
    }

    private void connectIfLegacyApRecordFound(Map<String, String> record) {
        try {
            if (record.get(RECORD_TYPE).equals(RECORD_TYPE_LEGACY_AP)) {
                String ssid = record.get(RECORD_SSID);
                String key = record.get(RECORD_KEY);

                appendLogUiThread("Connecting to a Legacy WiFi ......");

                connectToLegacyApAndOpenSockets(ssid, key);
            }
        } catch (Exception ex) {
            appendLog("Something went wrong!! Cannot try connection right now.");
        }
    }

    /**
     * Removes the previously configured Legacy WiFi Direct networks
     *
     * @param knownSSID is the SSID of the network that we want to keep
     * @return -1 if nothing is removed or kept, -100 if one or more is removed,
     * or the networkid of the network that must be kept (if found)
     */
    private int removeConfiguredLegacyAPs(String knownSSID) {
        int foundId = -1;
        int tmpId = 0;
        boolean removed = false;
        boolean found = false;
        if (wifiManager != null) {
            if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                List<WifiConfiguration> confNets = wifiManager.getConfiguredNetworks();
                for (WifiConfiguration confNet : confNets) {
                    if (confNet.SSID.contains("DIRECT-")) {
                        if ((!confNet.SSID.contains(knownSSID)) /*|| lastConnectedSSID.equals("")*/) {
                            wifiManager.removeNetwork(confNet.networkId);
                            removed = true;
                        } else {
                            found = true;
                            tmpId = confNet.networkId;
                        }
                    }
                }
            }
        }

        if (found) foundId = tmpId;
        else if (removed) foundId = -100;

        return foundId;
    }

    public void connectToLegacyApAndOpenSockets(final String SSID, final String passPhrase) {
        Thread conThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Date startDate = new Date();//log the start time
                List<ScanResult> scanResults;
                wifiScanCompleted = false;
                if (wifiManager.startScan()) {
                    //Wait scan completed
                    while (!wifiScanCompleted) {
                        final long timeDiff = (new Date()).getTime() - startDate.getTime();
                        if (timeDiff > 30000) //if we took more than 30 seconds break
                            break;
                        sleep(1000);
                    }
                    if (wifiScanCompleted) {
                        appendLogUiThread("Scan result found");
                        scanResults = wifiManager.getScanResults();

                        for (ScanResult scanResult : scanResults) {
                            if (scanResult.SSID.equals(SSID)) {
                                configureAndConnectToLegacyAp(scanResult.BSSID, SSID, passPhrase);

                                //Try opening sockets connections with legacyAp
                                openLegacySocketConnections();
                                break;
                            }
                        }
                    } else appendLogUiThread("Cannot get the result of a WiFi Scan!!");
                } else appendLogUiThread("Cannot start a WiFi Scan!!");
            }
        });
        conThread.start();
    }

    public void configureAndConnectToLegacyAp(String BSSID, String SSID, String passPhrase) {
        //Create network configuration
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = -100;
        wifiConfiguration.BSSID = BSSID;
        wifiConfiguration.SSID = convertToQuotedString(SSID);
        wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wifiConfiguration.preSharedKey = convertToQuotedString(passPhrase);
        wifiConfiguration.priority = 1;

        sleep(500);
        //Add the created configuration to the list of networks
        int newNetId = wifiManager.addNetwork(wifiConfiguration);
        sleep(500);
        if (newNetId != -1) {
            appendLogUiThread("Configuring legacy WiFi succeeded.");
            wifiSupplicantAssociated = false;
            //Connect to the network
            if (wifiManager.enableNetwork(newNetId, true)) {
                appendLogUiThread("Enabling legacy WiFi succeeded.");
                sleep(500);
                WifiInfo conInfo = wifiManager.getConnectionInfo();
                appendLogUiThread(convertIntToIpAddress(conInfo.getIpAddress())
                        + " -> " + conInfo.getSSID() + ", " + conInfo.getSupplicantState());
            } else {
                appendLogUiThread("Enabling legacy WiFi failed [netid=" + newNetId + "]");
            }
        } else {
            appendLogUiThread("Configuring legacy WiFi failed");
        }
    }

    private void sleep(int duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ignored) {
        }
    }

    public synchronized void connectP2pDevice(final WifiP2pDevice device) {

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        appendLogUiThread("Connecting to GO -> " + device.deviceName);
        wifiP2pManager.connect(wifiP2pChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiP2pBroadcastReceiver will notify us. Ignore for now.
                decideGroupHandler.removeCallbacks(decideGroupRunnable);
                thisDeviceState = ThisDeviceState.GM_COMMUNICATING_WITH_GO;
                appendLogUiThread("Connected to GO successfully -> " + device.deviceName);

                //To account for device losing connection with GO, we schedule a teardown
                tearDownHandler.postDelayed(tearDownRunnable, mSendTearDownPeriod + 1000);
            }

            @Override
            public void onFailure(int reason) {
                //Start over again
                tearDownGroupAndReRun();
                appendLogUiThread("Connecting to GO failed !!!");
                //Toast.makeText(getApplicationContext(), "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public synchronized void appendLogReceived(String dataMsg) {
        if (txtReceived != null) {
            txtReceived.append(dataMsg + "\n");
        }
        Log.d(TAG, "Msg read -> " + dataMsg);
    }

    public void appendLogReceivedUiThread(final String string) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLogReceived(string);
            }
        });
    }

    public synchronized void appendLog(String string, Boolean logCatOnly, Boolean noNewLine) {
        if (!logCatOnly)
            if (txtLog != null)
                txtLog.append(string + (noNewLine ? "" : "\n"));

        Log.d(TAG, string);
    }

    public void appendLog(String string) {
        appendLog(string, false, false);
    }

    public void appendLog(String string, boolean logCatOnly) {
        appendLog(string, logCatOnly, false);
    }

    public void appendLogUiThread(final String string) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog(string, false, false);
            }
        });
    }

    public void appendLogUiThread(final String string, final boolean logCatOnly) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                appendLog(string, logCatOnly, false);
            }
        });
    }

    public void updateThisDevice(WifiP2pDevice device) {
        p2pDevice = device;
        appendLogUiThread("Device Changed -> " /*+ device.toString()*/);
    }

    public void resetP2pStructures() {
        p2pDevice = null;
        p2pGroup = null;
        p2pInfo = null;
    }

    public void requestP2pPeers() {
        wifiP2pManager.requestPeers(wifiP2pChannel, new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                //displayPeers(peers);
            }
        });
    }

    private void displayPeers(WifiP2pDeviceList peers) {
        String str = "";
        for (WifiP2pDevice device : peers.getDeviceList()) {
            str += "\n" + device.deviceName + "\n   " + device.deviceAddress + "\n   "
                    + (device.isGroupOwner() ? "GO" : "--") + "\n   "
                    + Integer.toString(device.status);
        }
        appendLog(str);
    }

    //Adapted from http://stackoverflow.com/questions/16730711/get-my-wifi-ip-address-android
    private String convertIntToIpAddress(int ipAddress) {
        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    private void tapAcceptInvitationAutomatically() {
        Thread thrd = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Build.MODEL.equals("GT-P3113")) {
                        for (int i = 0; i < 1; i++) {
                            Process process = Runtime.getRuntime().exec(
                                    String.format("su -c input tap %d %d", 440, 565 + i));
                            process.waitFor();
                            Log.e("SU input tap", "input tap command finished " + (565 + i));
                        }
                    }
                } catch (IOException | InterruptedException ignored) {
                }
            }
        });
        thrd.start();
    }

    private void declareGM() {
        thisDeviceState = ThisDeviceState.GM_SELECTING_GO;
        decideGroupHandler.removeCallbacks(decideGroupRunnable);
        decideGroupHandler.postDelayed(decideGroupRunnable, mDecideGroupPeriod);
    }

    private void declareGo() {
        if (discoveryPeersInfo.getBestGoIsMe(this)) {
            appendLogUiThread("I AM THE BEST, TRYING TO CREATE A GROUP");
            createWifiP2pGroup();
            //Moved to Group creation callback
            //thisDeviceState = ThisDeviceState.GO_ACCEPTING_CONNECTIONS;
            //sendTearDownHandler.postDelayed(sendTearDownRunnable, mSendTearDownPeriod);
        } else {
            appendLogUiThread("AT LEAST SOMEONE ELSE IS BETTER THAN ME, TRYING TO CHOOSE GROUP");
            declareGM();
        }
    }

    private void declareGoAcceptingGMs() {
        thisDeviceState = ThisDeviceState.GO_ACCEPTING_CONNECTIONS;
    }

    private void decideGroupAndConnect() {
        discoveryPeersInfo.decideGoAndSpareToConnect();
        WifiP2pDevice selGo = discoveryPeersInfo.getSelectedGoDevice();
        if (selGo != null) {
            appendLogUiThread("THE BEST GO IS " + selGo.deviceName + ", TRYING TO CONNECT TO IT");
            connectP2pDevice(selGo);
        } else {
            appendLogUiThread("I COULD NOT DECIDE THE BEST GO");
            tearDownGroupAndReRun();
        }
        //Moved to device connect callback
        //thisDeviceState = ThisDeviceState.GM_COMMUNICATING_WITH_GO;
    }

    private void decideProxiesAndInformMembers() {
        if (legacyGroupsInfo.getLegacyApsList().size() > 0) {
            legacyGroupsInfo.calculateCoverage();

            for (LegacyApsCoverage legacyApsCoverage : legacyGroupsInfo.legacyApsCoverages) {
                if (legacyApsCoverage.sendAssignmentForProxyPeer())
                    appendLogUiThread("Proxy Assignment sent to "
                            + legacyApsCoverage.proxyPeer.socketPeer.toString());
                else
                    appendLogUiThread("Failed to send Proxy Assignment to "
                            + legacyApsCoverage.proxyPeer.socketPeer.toString());
            }
        } else {
            appendLogUiThread("Not sending Proxy Assignment -> No LegacyAps Nearby");
        }
    }

    private void sendNearbyLegacyApsInfo() {
        if (isWifiP2pEnabled) {
            if (p2pInfo != null) {
                if (!p2pInfo.isGroupOwner) {
                    if (thisDeviceState == ThisDeviceState.GM_COMMUNICATING_WITH_GO) {
                        final int size = groupSocketPeers.getOpenManagementSockets().size();
                        if (size == 0) {
                            appendLogUiThread("Not connected to GO Management Socket!!!");
                        } else if (size == 1) {
                            String info = discoveryPeersInfo.toStringGoOnly();
                            groupSocketPeers.sendToAllManagmentSockets(
                                    info, MessageType.MGMNT_GM_TO_GO_FOUND_LEGACY_AP);
                            appendLogUiThread("sendNearbyLegacyApsInfo: sent To GO -> " + info);
                        } else if (size > 1) {
                            appendLogUiThread("Error!!! can't have more than one management socket");
                        }
                    }
                }
            }
        }
    }

    private void sendTearDownToMembers() {
        groupSocketPeers.sendToAllManagmentSockets("Bye", MessageType.MGMNT_GO_TO_GM_TEAR_DOWN);
        tearDownHandler.postDelayed(tearDownRunnable, 1000);
    }

    private void tearDownGroupAndReRun() {
        tearDownGroupAndReRun(true);
    }

    private synchronized void tearDownGroupAndReRun(boolean reRun) {
        thisDeviceState = ThisDeviceState.FINISHED;
        removeWiFiP2pGroup();
        clearAllLocalServices();
        clearAllServiceRequests();
        resetP2pStructures();

        myProposedIP = DiscoveryPeerInfo.generateProposedIP();

        groupSocketPeers.removeAllSocketManagers();
        groupSocketPeers.clear();

        if (proxySocketPeer != null)
            proxySocketPeer.removeSocketManagers();
        proxySocketPeer = reRun ? new SocketPeer() : null;

        discoveryPeersInfo.clear();
        discoveryPeersInfo.devices.clear();

        legacyGroupsInfo.clear();

        buddies.clear();

        performanceAnalysis.reset();

        declareGoHandler.removeCallbacks(declareGoRunnable);
        decideGroupHandler.removeCallbacks(decideGroupRunnable);
        decideProxyHandler.removeCallbacks(decideProxyRunnable);
        sendTearDownHandler.removeCallbacks(sendTearDownRunnable);
        tearDownHandler.removeCallbacks(tearDownRunnable);
        declareGoAcceptingGMsHandler.removeCallbacks(declareGoAcceptingGMsRunnable);
        sendNearbyLegacyApsInfoHandler.removeCallbacks(sendNearbyLegacyApsInfoRunnable);

        //if (!reRun) {
        stopTimers();
        //}

        if (mgntHandler != null)
            try {
                ((GroupManagementSocketHandler) mgntHandler).closeServerSocket();
            } catch (Exception e) {
            }

        if (dataHandler != null)
            try {
                ((GroupDataSocketHandler) dataHandler).closeServerSocket();
            } catch (Exception e) {
            }

        if (proxyMgntHandler != null)
            try {
                ((ProxyDataSocketHandler) proxyMgntHandler).closeServerSocket();
            } catch (Exception e) {
            }

        if (proxyDataHandler != null)
            try {
                ((ProxyManagementSocketHandler) proxyDataHandler).closeServerSocket();
            } catch (Exception e) {
            }

        mgntHandler = null;
        dataHandler = null;
        proxyMgntHandler = null;
        proxyDataHandler = null;

        if (reRun) {
            declareGoHandler.postDelayed(declareGoRunnable, mDeclareGoPeriod);
            thisDeviceState = ThisDeviceState.COLLECTING_DEVICE_INFO;

            startTimers();
            createDeviceInfoService();
        }
    }

    public synchronized void startTimers() {
        try {
            sendMyInfoTimer = new Timer("sendMyInfoTimer");
            sendPeersInfoTimer = new Timer("sendPeersInfoTimer");
            discoverServicesTimer = new Timer("discoverServicesTimer");
            addServicesTimer = new Timer("addServicesTimer");

            sendMyInfoTimer.schedule(new SendMyInfoTask(), 0, mSendMyInfPeriod);
            sendPeersInfoTimer.schedule(new SendPeersInfoTask(), 0, mSendPeersInfoPeriod);
            discoverServicesTimer.schedule(new DiscoverServicesTask(), 0, mDiscoverServicesPeriod);
            addServicesTimer.schedule(new AddServicesTask(), 0, mAddServicesPeriod);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void stopTimers() {
        sendMyInfoTimer.cancel();
        sendMyInfoTimer.purge();
        sendPeersInfoTimer.cancel();
        sendPeersInfoTimer.purge();
        discoverServicesTimer.cancel();
        discoverServicesTimer.purge();
        addServicesTimer.cancel();
        addServicesTimer.purge();
    }

    private void forwardIfMeIsProxy(String messageData) {
        if (proxySocketPeer.isConnectedToProxyData()) {
            proxySocketPeer.proxyDataSocketManager.writeFormattedMessage(
                    messageData,
                    MessageType.DATA_PROXY_TO_LEGACY_AP);
            appendLogUiThread("[PROXY] Forwarding Data to LegacyAp");
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case DATA_MESSAGE_READ:
                SocketManager sm = ((BufferSocket) msg.obj).socketManager;
                byte[] readBuf = ((BufferSocket) msg.obj).buffer;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                //Logging stats
                SocketPeer peer = groupSocketPeers.getPeerFromSocketManager(sm);
                if(peer != null) {
                    performanceAnalysis.addSocketStatistic(peer.deviceAddress + "," + peer.ipAddress
                            , readMessage.length(), false);
                }

                MessageTypeData msgTypeData = MessageHelper.getMessageTypeAndData(readMessage);
                if (msgTypeData != null) {
                    if (msgTypeData.messageType == MessageType.DATA_GM_TO_GROUP) {
                        String peerName = groupSocketPeers.getPeerNameFromSocketManager(sm);
                        appendLogReceived("[" + peerName + "]: " + msgTypeData.messageData);
                        //Forward data if me is a proxy
                        forwardIfMeIsProxy(msgTypeData.messageData);

                    } else if (msgTypeData.messageType == MessageType.DATA_LEGACY_AP_FROM_PROXY) {
                        appendLogReceived("[LEGACY_FROM_PROXY]: " + msgTypeData.messageData);
                    } else {
                        appendLogReceived("Unknown DATA message !!!");
                    }
                } else appendLogReceived("Corrupted DATA message !!!");
                break;

            case DATA_SOCKET_HANDLE:
                sm = (SocketManager) msg.obj;
                groupSocketPeers.addDataSocketManagerToPeer(sm);
                appendLog("New DataSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
                appendLog("No of peers so far = " + groupSocketPeers.getPeers().size());
                break;

            case MGMNT_MESSAGE_READ:
                readBuf = ((BufferSocket) msg.obj).buffer;
                readMessage = new String(readBuf, 0, msg.arg1);
                appendLog("Management msg read: " + readMessage);
                sm = ((BufferSocket) msg.obj).socketManager;
                //Logging stats
                peer = groupSocketPeers.getPeerFromSocketManager(sm);
                if(peer != null) {
                    performanceAnalysis.addSocketStatistic(peer.deviceAddress + "," + peer.ipAddress
                            , readMessage.length(), true);
                }

                processManagementMessage(readMessage, sm);
                break;

            case MGMNT_SOCKET_HANDLE:
                sm = (SocketManager) msg.obj;
                boolean peerIsGo = !p2pInfo.isGroupOwner;
                groupSocketPeers.addManagementSocketManagerToPeer(sm, peerIsGo);
                appendLog("New ManagementSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
                break;

            case MGMNT_SOCKET_PEER_ADDR:

                break;

            case PROXY_DATA_MESSAGE_READ:
                readBuf = ((BufferSocket) msg.obj).buffer;
                readMessage = new String(readBuf, 0, msg.arg1);
                msgTypeData = MessageHelper.getMessageTypeAndData(readMessage);
                if (msgTypeData != null) {
                    if (msgTypeData.messageType == MessageType.DATA_PROXY_TO_LEGACY_AP) {
                        appendLogReceived("[PROXY_TO_LEGACY]: " + msgTypeData.messageData);
                        groupSocketPeers.sendToAllDataSockets(
                                msgTypeData.messageData,
                                MessageType.DATA_LEGACY_AP_FROM_PROXY);
                    } else {
                        appendLogReceived("Unknown DATA message !!!");
                    }
                } else appendLogReceived("Corrupted DATA message !!!");
                break;

            case PROXY_DATA_SOCKET_HANDLE:
                sm = (SocketManager) msg.obj;
                proxySocketPeer.proxyDataSocketManager = sm;
                appendLog("New ProxyDataSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
                break;

            case PROXY_MGMNT_MESSAGE_READ:
                readBuf = ((BufferSocket) msg.obj).buffer;
                readMessage = new String(readBuf, 0, msg.arg1);
                appendLog("Management msg read: " + readMessage);
                sm = ((BufferSocket) msg.obj).socketManager;
                processManagementMessage(readMessage, sm);
                break;

            case PROXY_MGMNT_SOCKET_HANDLE:
                sm = (SocketManager) msg.obj;
                proxySocketPeer.proxyManagementSocketManager = sm;
                Log.d(TAG, "New ProxyManagementSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
                break;
        }
        return true;
    }

    private void processManagementMessage(String readMessage, SocketManager socketManager) {
        MessageTypeData msgTypeData = MessageHelper.getMessageTypeAndData(readMessage);
        if (msgTypeData != null) {
            if (p2pInfo != null) {
                if (p2pInfo.isGroupOwner) {
                    if (msgTypeData.messageType == MessageType.MGMNT_KEEP_ALIVE) {
                        groupSocketPeers.addOrUpdatePeer(msgTypeData.messageData);
                        appendLog("GO: added/updated peer to list");
                        groupSocketPeers.connectToAllPeersDataPorts(this);
                        groupSocketPeers.removeDuplicatedDataSocketManagers();
                    } else if (msgTypeData.messageType == MessageType.MGMNT_GM_TO_GO_FOUND_LEGACY_AP) {
                        SocketPeer socketPeer = groupSocketPeers.getPeerFromSocketManager(socketManager);
                        if (socketPeer != null) {
                            appendLogUiThread("Adding received LEGACYAP_INFO ->" + socketPeer.name + "\n\n");
                            legacyGroupsInfo.add(socketPeer, msgTypeData.messageData, p2pDevice.deviceAddress);
                        } else
                            appendLogUiThread("Failed to add LEGACYAP_INFO!!!\n\n");
                    } else if (msgTypeData.messageType == MessageType.MGMNT_PROXY_TO_LEGACY_AP_TEAR_DOWN) {
                        sendTearDownHandler.removeCallbacks(sendTearDownRunnable);
                        sendTearDownToMembers();
                        //tearDownHandler.postDelayed(tearDownRunnable, 1000);
                        appendLog("PROXY_TEARDOWN: Tear-down scheduled");
                    } else {
                        appendLogReceived("Unknown MGMNT message !!!");
                    }
                } else {
                    if (msgTypeData.messageType == MessageType.MGMNT_KEEP_ALIVE) {
                        String strs[] = msgTypeData.messageData.split(";");
                        for (String str : strs) {
                            groupSocketPeers.addOrUpdatePeer(str);
                        }
                        appendLog("GM: added/updated peer to list");
                        groupSocketPeers.connectToAllPeersDataPorts(this);
                        groupSocketPeers.removeDuplicatedDataSocketManagers();
                    } else if (msgTypeData.messageType == MessageType.MGMNT_GO_TO_GM_SELECTED_LEGACY) {
                        String strs[] = msgTypeData.messageData.split(",");
                        //We have SSID, Key
                        if (strs.length == 2) {
                            //Perform Legacy Connection
                            connectToLegacyApAndOpenSockets(strs[0], strs[1]);
                            //Schedule opening a socket connection with the LegacyAp
                            //openLegacySocketConnections();
                        }
                    } else if (msgTypeData.messageType == MessageType.MGMNT_GO_TO_GM_SPARE_LEGACY) {
                    } else if (msgTypeData.messageType == MessageType.MGMNT_GO_TO_GM_TEAR_DOWN) {
                        //If I am a Proxy then relay the message to the other group
                        if (proxySocketPeer.isConnectedToProxyManagement()) {
                            proxySocketPeer.proxyManagementSocketManager.writeFormattedMessage(
                                    "Bye", MessageType.MGMNT_PROXY_TO_LEGACY_AP_TEAR_DOWN);
                        }
                        //Schedule a tear down
                        tearDownHandler.postDelayed(tearDownRunnable, 1000);
                        appendLog("GO_TEARDOWN: Tear-down scheduled");
                    } else {
                        appendLogReceived("Unknown MGMNT message !!!");
                    }
                }
            }
        } else appendLogReceived("Corrupted MGMNT message !!!" + readMessage);
    }

    private void openLegacySocketConnections() {
        try {
            th1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (wifiManager != null) {
                        if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                            Date startDate = new Date();
                            long timeDiff;

                            while (wifiManager.getConnectionInfo().getSupplicantState() != SupplicantState.COMPLETED) {
                                timeDiff = (new Date()).getTime() - startDate.getTime();
                                //if we took more than 30 seconds break
                                if (timeDiff > 30000) {
                                    appendLogUiThread("Can't open socket connections with LegacyAp -> Not associated");
                                    return;
                                }
                                appendLogUiThread("Waiting for association with LegacyAp to complete to connect sockets...");
                                sleep(1000);
                            }

                            sleep(10000);
                            String legacyApIpAddress = getMyLegacyApIP();
                            if (legacyApIpAddress != null) {
                                appendLogUiThread("Connecting to LegacyAp Sockets -> " + legacyApIpAddress);
                                Thread thrd1 = new ClientSocketHandler(getHandler(),
                                        legacyApIpAddress, mProxyMgmntPort);
                                thrd1.start();

                                Thread thrd2 = new ClientSocketHandler(getHandler(),
                                        legacyApIpAddress, mProxyDataPort);
                                thrd2.start();
                            } else {
                                appendLogUiThread("Can't get the IP Address of the LegacyAp");
                            }
                        }
                    }
                }
            });
            th1.start();

        } catch (Exception e) {
            e.printStackTrace();
            appendLogUiThread("Failed to connect  to LegacyAp Sockets!!!");
        }
    }

    private String getMyLegacyApIP() {
        String ipAddress = null;
        if (wifiManager != null) {
            if (wifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED) {
                if (wifiManager.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED)
                    ipAddress = convertIntToIpAddress(wifiManager.getDhcpInfo().serverAddress);
            }
        }
        return ipAddress;
    }

    //Taken from -> https://code.google.com/p/android-wifi-connecter/
    public static String convertToQuotedString(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }

        final int lastPos = string.length() - 1;
        if (lastPos > 0 && (string.charAt(0) == '"' && string.charAt(lastPos) == '"')) {
            return string;
        }

        return "\"" + string + "\"";
    }

    public static String getWifiDirectIPAddress() {
        // Adapted from
        // http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device/12449111#12449111
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (intf.getDisplayName().contains("p2p")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf
                            .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()
                                && inetAddress instanceof Inet4Address) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public void sendMyInfoToGOTask() {
        if (isWifiP2pEnabled) {
            if (p2pInfo != null) {
                if (!p2pInfo.isGroupOwner) {
                    if (thisDeviceState == ThisDeviceState.GM_COMMUNICATING_WITH_GO) {
                        final int size = groupSocketPeers.getOpenManagementSockets().size();
                        if (size == 0) {
                            appendLogUiThread("Not connected to GO Management Socket!!!");
                        } else if (size == 1) {
                            String pLst = getMyInfo();
                            groupSocketPeers.sendToAllManagmentSockets(pLst, MessageType.MGMNT_KEEP_ALIVE);
                            performanceAnalysis.sentManagementSocketMessagesCount++;
                            appendLogUiThread("sendMyInfoToGOTask: sent -> " + pLst);
                        } else if (size > 1) {
                            appendLogUiThread("Error!!! can't have more than one management socket");
                        }
                    }
                }
                groupSocketPeers.decreasePeersTTL();
            }
        }
    }

    public void sendPeersInfoTask() {
        if (p2pInfo != null) {
            if (isWifiP2pEnabled) {
                groupSocketPeers.prunePeers();
                if (p2pInfo.isGroupOwner) {
                    if (thisDeviceState == ThisDeviceState.GO_ACCEPTING_CONNECTIONS) {
                        String pLst = groupSocketPeers.toString();
                        if (pLst.equals("")) pLst = getMyInfo();
                        else pLst += ";" + getMyInfo();
                        int count = groupSocketPeers.sendToAllManagmentSockets(pLst, MessageType.MGMNT_KEEP_ALIVE);
                        performanceAnalysis.sentManagementSocketMessagesCount += count;
                        appendLogUiThread("sendPeersInfoTask: Sent -> " + pLst + " To "
                                + groupSocketPeers.getOpenManagementSockets().size() + " Peers");
                    }
                }
            }
        }
    }

    private String getMyInfo() {
        String str = "";

        if (p2pDevice != null) {
            str = p2pDevice.deviceName + ","
                    + p2pDevice.deviceAddress + ","
                    + getWifiDirectIPAddress() + ","
                    + (p2pDevice.isGroupOwner() ? "1" : "0")
            ;
        }
        return str;
    }

    InetAddress getBroadcastAddress() throws IOException {

        DhcpInfo dhcp = wifiManager.getDhcpInfo();
        // handle null somehow

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    public void discoverServicesTask() {
        if (isWifiP2pEnabled) {
            clearAllLocalServices();
            sleep(500);
            if (thisDeviceState == ThisDeviceState.COLLECTING_DEVICE_INFO
                    || thisDeviceState == ThisDeviceState.GM_SELECTING_GO
                    /*|| thisDeviceState == ThisDeviceState.GO_SENDING_LEGACY_INFO*/) {
                discoverServices();
                performanceAnalysis.sentServiceDiscoveryRequestCount++;
            }
        }
    }

    public void addServicesTask() {
        if (isWifiP2pEnabled) {
            clearAllLocalServices();
            sleep(500);
            if (thisDeviceState == ThisDeviceState.COLLECTING_DEVICE_INFO
                    || thisDeviceState == ThisDeviceState.GO_SENDING_LEGACY_INFO
                    || thisDeviceState == ThisDeviceState.GO_ACCEPTING_CONNECTIONS) {
                createDeviceInfoService();
            }

            if (thisDeviceState == ThisDeviceState.GO_SENDING_LEGACY_INFO
                    || thisDeviceState == ThisDeviceState.GO_ACCEPTING_CONNECTIONS) {
                createLegacyApService();
            }
        }
    }

    public enum ThisDeviceState {
        STARTED,
        COLLECTING_DEVICE_INFO,
        GO_ACCEPTING_CONNECTIONS,
        GO_SENDING_LEGACY_INFO,
        GM_SELECTING_GO,
        GM_COMMUNICATING_WITH_GO,
        FINISHED,
    }

    class WifiBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            appendLogUiThread("=========" + action + "=========", true);

            if (WifiManager.NETWORK_IDS_CHANGED_ACTION.equals(action)) {

            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {

            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                wifiScanCompleted = true;
            } else if (WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION.equals(action)) {

            } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                final SupplicantState supplicantState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                appendLogUiThread("----" + supplicantState.toString() + "----");

                if (supplicantState == SupplicantState.ASSOCIATED) {
                    wifiSupplicantAssociated = true;
                    appendLogUiThread("+++++Allah Akbar+++++");
                }
            }
        }
    }

    class SendMyInfoTask extends TimerTask {
        public void run() {
            sendMyInfoToGOTask();
        }
    }

    class SendPeersInfoTask extends TimerTask {
        public void run() {
            sendPeersInfoTask();
        }
    }

    class DiscoverServicesTask extends TimerTask {
        public void run() {
            discoverServicesTask();
        }
    }

    class AddServicesTask extends TimerTask {
        public void run() {
            addServicesTask();
        }
    }
}



