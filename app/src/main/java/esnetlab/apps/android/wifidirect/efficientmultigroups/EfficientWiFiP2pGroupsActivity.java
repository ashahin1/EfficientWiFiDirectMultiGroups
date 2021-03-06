package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.support.annotation.NonNull;
import android.support.design.internal.NavigationMenu;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.SupportMenuInflater;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuPopupHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class EfficientWiFiP2pGroupsActivity extends AppCompatActivity implements WifiP2pManager.ConnectionInfoListener, Handler.Callback, MessageTarget, ProtocolConstants {

    static final String TAG = "EfficientWiFiP2pGroups";
    private static final int REQUEST_WRITE_STORAGE = 112;

    //Port parameters
    static int mMgmntPort;
    static int mDataPort;
    static int mProxyMgmntPort;
    static int mProxyDataPort;

    //Ranking parameters
    static float mRankAlpha;
    static float mRankBeta;
    static float mRankGamma;
    static float mRankMaxCapacity;

    //Protocol timing parameters
    private static int mSendMyInfPeriod;
    private static int mSendPeersInfoPeriod;
    private static int mDiscoverServicesPeriod;
    private static int mAddServicesPeriod;
    private static int mDeclareGoPeriod;
    private static int mDecideGroupPeriod;
    private static int mDecideProxyPeriod;
    private static int mSendNearbyLegacyApsInfoPeriod;
    private static int mSendTearDownPeriod;

    //Testing parameters
    private static int mRequestedNoOfRuns;
    private static boolean mAddServiceOnChange;
    private static boolean mDiscoverServiceOnDemand;
    private static int mNoOfDevices;
    private static int mMaxSubnetX;
    private static int mMaxSubnetY;

    private static WifiP2pInfo p2pInfo = null;
    private static WifiP2pGroup p2pGroup = null;
    static WifiP2pDevice p2pDevice = null;
    private static Boolean wifiScanCompleted = false;
    private static Boolean wifiSupplicantAssociated = false;
    private final IntentFilter wifiIntentFilter = new IntentFilter();
    private final IntentFilter wifiP2pIntentFilter = new IntentFilter();
    private final DiscoveryPeersInfo discoveryPeersInfo = new DiscoveryPeersInfo();
    private final SocketPeers groupSocketPeers = new SocketPeers();
    private final LegacyGroupsInfo legacyGroupsInfo = new LegacyGroupsInfo();
    private final HashMap<String, Map<String, String>> buddies = new HashMap<>();
    private final PerformanceAnalysis performanceAnalysis = new PerformanceAnalysis();
    private final DiscoveryPeerInfo myDiscoveryInfo = new DiscoveryPeerInfo();

    private ThisDeviceState thisDeviceState = ThisDeviceState.STARTED;
    private ProtocolTestMode protocolTestMode = ProtocolTestMode.NO_TEST;

    private String myProposedIP;// = Utilities.generateProposedIP();

    private TextView txtLog;
    private EditText txtSend;
    private TextView txtReceived;

    private boolean streamingStarted = false;

    private Thread mgntHandler = null;
    private Thread dataHandler = null;
    private Thread proxyMgntHandler = null;
    private Thread proxyDataHandler = null;

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
    private final Runnable declareGoRunnable = this::declareGo;
    private final Runnable decideGroupRunnable = this::decideGroupAndConnect;
    private final Runnable declareGoAcceptingGMsRunnable = this::declareGoAcceptingGMs;
    private final Runnable decideProxyRunnable = this::decideProxiesAndInformMembers;
    private final Runnable sendNearbyLegacyApsInfoRunnable = this::sendNearbyLegacyApsInfo;
    private final Runnable tearDownRunnable = new Runnable() {
        @Override
        public void run() {
            if ((mRequestedNoOfRuns == -1) || (mRequestedNoOfRuns > performanceAnalysis.runNumber)) {
                tearDownGroupAndReRun();
                performanceAnalysis.runNumber++;
            } else if (mRequestedNoOfRuns == performanceAnalysis.runNumber) {
                stopAllTests();
            }
        }
    };
    private final Runnable sendTearDownRunnable = this::sendTearDownToMembers;
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
        askForWritingPermission();
    }

    private void setupPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences != null) {
            int pInt;
            pInt = getResources().getInteger(R.integer.pref_default_group_management_port);
            mMgmntPort = Integer.parseInt(sharedPreferences.getString(PREF_MGMNT_PORT, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_group_data_port);
            mDataPort = Integer.parseInt(sharedPreferences.getString(PREF_DATA_PORT, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_proxy_management_port);
            mProxyMgmntPort = Integer.parseInt(sharedPreferences.getString(PREF_PROXY_MGMNT_PORT, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_proxy_data_port);
            mProxyDataPort = Integer.parseInt(sharedPreferences.getString(PREF_PROXY_DATA_PORT, String.valueOf(pInt)));

            String pStr;
            pStr = getResources().getString(R.string.pref_default_rank_alpha);
            mRankAlpha = Float.parseFloat(sharedPreferences.getString(PREF_RANK_ALPHA, pStr));
            pStr = getResources().getString(R.string.pref_default_rank_beta);
            mRankBeta = Float.parseFloat(sharedPreferences.getString(PREF_RANK_BETA, pStr));
            pStr = getResources().getString(R.string.pref_default_rank_gamma);
            mRankGamma = Float.parseFloat(sharedPreferences.getString(PREF_RANK_GAMMA, pStr));
            pStr = getResources().getString(R.string.pref_default_rank_max_capacity);
            mRankMaxCapacity = Float.parseFloat(sharedPreferences.getString(PREF_RANK_MAX_CAPACITY, pStr));

            pInt = getResources().getInteger(R.integer.pref_default_send_my_info);
            mSendMyInfPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_MY_INF_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_send_peers_info);
            mSendPeersInfoPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_PEERS_INFO_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_discover_services);
            mDiscoverServicesPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DISCOVER_SERVICES_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_add_services);
            mAddServicesPeriod = Integer.parseInt(sharedPreferences.getString(PREF_ADD_SERVICES_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_declare_go);
            mDeclareGoPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DECLARE_GO_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_decide_group);
            mDecideGroupPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DECIDE_GROUP_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_decide_proxy);
            mDecideProxyPeriod = Integer.parseInt(sharedPreferences.getString(PREF_DECIDE_PROXY_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_send_nearby_legacy_aps_info);
            mSendNearbyLegacyApsInfoPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_NEARBY_LEGACY_APS_INFO_PERIOD, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_send_tear_down);
            mSendTearDownPeriod = Integer.parseInt(sharedPreferences.getString(PREF_SEND_TEAR_DOWN_PERIOD, String.valueOf(pInt)));

            pInt = getResources().getInteger(R.integer.pref_default_requested_no_of_runs);
            mRequestedNoOfRuns = Integer.parseInt(sharedPreferences.getString(PREF_REQUESTED_NO_OF_RUNS, String.valueOf(pInt)));
            boolean pBool = getResources().getBoolean(R.bool.pref_default_add_service_on_change);
            mAddServiceOnChange = sharedPreferences.getBoolean(PREF_ADD_SERVICE_ON_CHANGE, pBool);
            pBool = getResources().getBoolean(R.bool.pref_default_discover_service_on_demand);
            mDiscoverServiceOnDemand = sharedPreferences.getBoolean(PREF_DISCOVER_SERVICES_ON_DEMAND, pBool);
            pInt = getResources().getInteger(R.integer.pref_default_no_of_devices);
            mNoOfDevices = Integer.parseInt(sharedPreferences.getString(PREF_NO_OF_DEVICES, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_subnet_x);
            mMaxSubnetX = Integer.parseInt(sharedPreferences.getString(PREF_SUBNET_X, String.valueOf(pInt)));
            pInt = getResources().getInteger(R.integer.pref_default_subnet_y);
            mMaxSubnetY = Integer.parseInt(sharedPreferences.getString(PREF_SUBNET_Y, String.valueOf(pInt)));

        }
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

    private void setupControls() {

        txtLog = (TextView) findViewById(R.id.txt_log);
        txtSend = (EditText) findViewById(R.id.txt_send);
        txtReceived = (TextView) findViewById(R.id.txt_received);

        txtReceived.setOnLongClickListener(v -> {
            ((TextView) v).setText("");
            return false;
        });

        final Button btnSend = (Button) findViewById(R.id.btn_send);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> {
                String dataToSend = txtSend.getText().toString();
                if (!dataToSend.equals("")) {
                    int count = groupSocketPeers.sendToAllDataSockets(dataToSend, MessageType.DATA_GM_TO_GROUP);
                    if (count > 0) {
                        performanceAnalysis.sentDataSocketMessagesCount += count;
                        forwardIfMeIsProxy(dataToSend);
                        txtReceived.append("Data msg Sent -> [ME]: " + dataToSend + "\n");
                        txtSend.setText("");
                        txtSend.clearFocus();
                    } else {
                        Toast.makeText(v.getContext(), "Not connected to any peers!", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        final FloatingActionButton btnFab = (FloatingActionButton) findViewById(R.id.fab);

        if (btnFab != null) {
            btnFab.setOnClickListener(this::initPopMenu);
        }
    }

    private void initPopMenu(View v) {
        //http://stackoverflow.com/questions/6805756/is-it-possible-to-display-icons-in-a-popupmenu
        createMenu(R.menu.fab_menu, v, new MenuBuilder.Callback() {
            @Override
            public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_create_group:
                        if (checkGroupFormedByMe()) {
                            removeWiFiP2pGroup(true);
                            item.setTitle("Create Group");
                        } else {
                            myProposedIP = Utilities.generateProposedIP(mMaxSubnetX, mMaxSubnetY);
                            createWifiP2pGroup();
                            item.setTitle("Remove Group");
                        }
                        break;
                    case R.id.action_discover_peers:
                        discoverPeers();
                        break;
                    case R.id.action_create_service:
                        removeDeviceInfoService();
                        sleep(1000);
                        updateMyDeviceDiscoveryInfo();
                        createDeviceInfoService();
                        break;
                    case R.id.action_list_services:
                        stopDiscoveringServices();
                        sleep(1000);
                        discoverServices();
                        performanceAnalysis.reset();
                        performanceAnalysis.startTime = System.currentTimeMillis();
                        performanceAnalysis.noOfDevices = mNoOfDevices;
                        performanceAnalysis.sentServiceDiscoveryRequestCount++;
                        break;
                    case R.id.action_ip_conflict_test:
                        startIpConflictTest();
                        break;
                    case R.id.action_group_formation_test:
                        startGroupFormationTest();
                        break;
                    case R.id.action_proxy_selection_test:
                        startProxySelectionTest();
                        break;
                    case R.id.action_emc_full:
                        startEmcFullTest();
                        break;
                    case R.id.action_stop_tests:
                        stopAllTests();
                        break;
                    case R.id.action_continuous_data:
                        if (!streamingStarted) {
                            streamContinuousData();
                        } else {
                            streamingStarted = false;
                        }
                        break;
                }
                return true;
            }

            @Override
            public void onMenuModeChange(MenuBuilder menu) {

            }
        });
    }

    private void createMenu(int menuRes, View anchor, MenuBuilder.Callback callback) {
        //http://stackoverflow.com/questions/6805756/is-it-possible-to-display-icons-in-a-popupmenu
        Context context = anchor.getContext();

        NavigationMenu navigationMenu = new NavigationMenu(context);
        navigationMenu.setCallback(callback);

        SupportMenuInflater supportMenuInflater = new SupportMenuInflater(context);
        supportMenuInflater.inflate(menuRes, navigationMenu);

        MenuPopupHelper menuPopupHelper = new MenuPopupHelper(context, navigationMenu, anchor);
        menuPopupHelper.setForceShowIcon(true);
        menuPopupHelper.show();
    }

    private void askForWritingPermission() {
        boolean hasPermission = (ContextCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,"Premission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "The app was not allowed to write to your storage. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
                }
            }
        }

    }


    private void startEmcFullTest() {
        performanceAnalysis.reset();
        appendLogUiThread("[*] Starting EMC Full Test .................\n");
        protocolTestMode = ProtocolTestMode.FULL_EMC_TEST;
        tearDownGroupAndReRun();
    }

    private void startProxySelectionTest() {
        performanceAnalysis.reset();
        appendLogUiThread("[*] Starting Proxy Selection Test .................\n");
        protocolTestMode = ProtocolTestMode.PROXY_SELECTION_TEST;
        tearDownGroupAndReRun();
    }

    private void startGroupFormationTest() {
        performanceAnalysis.reset();
        appendLogUiThread("[*] Starting Group Formation Test .................\n");
        protocolTestMode = ProtocolTestMode.GROUP_FORMATION_TEST;
        tearDownGroupAndReRun();
    }

    private void startIpConflictTest() {
        performanceAnalysis.reset();
        appendLogUiThread("[*] Starting IP Conflict Test .................\n");
        protocolTestMode = ProtocolTestMode.IP_CONFLICT_TEST;
        tearDownGroupAndReRun();
    }

    private void stopAllTests() {
        saveLog();
        clearLog();
        saveStatistics();
        displayStatistics();
        protocolTestMode = ProtocolTestMode.NO_TEST;
        tearDownGroupAndReRun(false);
    }

    private void displayStatistics() {
        String str = getHeaderString();
        str += performanceAnalysis.getStatistics(p2pDevice != null ? p2pDevice.deviceAddress : "");
        txtLog.setText(str);
    }

    private void saveStatistics() {
        String str = getHeaderString();
        str += performanceAnalysis.getStatistics(p2pDevice != null ? p2pDevice.deviceAddress : "");
        Utilities.writeStringToFile(str, "EMC_Stats_" + Build.MODEL + "_");
    }

    private void clearLog() {
        runOnUiThread(() -> txtLog.setText(""));
    }

    private void saveLog() {
        runOnUiThread(() -> {
            String str = getHeaderString();
            str += txtLog.getText().toString();

            if (Utilities.writeStringToFile(str, "EMC_log_" + Build.MODEL + "_")) {
                Toast.makeText(getApplicationContext(), "Log saved successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Log failed to save", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @NonNull
    private String getHeaderString() {
        return "...................EMC-IRMC....................\n"
                + "NUMBER OF RUNS: " + performanceAnalysis.runNumber + "\n"
                + "TEST TYPE: " + protocolTestMode.toString() + "\n"
                + "DATE: " + Calendar.getInstance().getTime().toString() + "\n"
                + "DEVICE MODEL: " + Build.MODEL + "\n"
                + "DEVICE NAME: " + (p2pDevice != null ? p2pDevice.deviceName : "") + "\n"
                + ".........................................\n\n";
    }

    private void streamContinuousData() {
        if ((thisDeviceState == ThisDeviceState.GM_COMMUNICATING_WITH_GO)
                || thisDeviceState == ThisDeviceState.GO_ACCEPTING_CONNECTIONS) {
            streamingStarted = true;
            Thread th2 = new Thread(() -> {
                String randomDataToSend = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
                while (streamingStarted) {
                    int count = groupSocketPeers.sendToAllDataSockets(randomDataToSend, MessageType.STREAM_DATA_TEST);
                    if (count > 0) {
                        performanceAnalysis.sentDataSocketMessagesCount += count;
                        forwardIfMeIsProxy(randomDataToSend);
                        appendLogReceivedUiThread("StreamData -> [ME]: ...");
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            th2.start();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        wifiBroadcastReceiver = new WifiBroadcastReceiver();
        registerReceiver(wifiBroadcastReceiver, wifiIntentFilter);
        wifiP2pBroadcastReceiver = new WiFiP2pBroadcastReceiver(wifiP2pManager, wifiP2pChannel, this);
        registerReceiver(wifiP2pBroadcastReceiver, wifiP2pIntentFilter);

        //requestRun = true;

        stopTimers();
        startTimers();
    }

    @Override
    public void onPause() {
        super.onPause();

        unregisterReceiver(wifiBroadcastReceiver);
        unregisterReceiver(wifiP2pBroadcastReceiver);

        //requestRun = false;

        stopTimers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tearDownGroupAndReRun(false);

        //requestRun = false;
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
        } else if (id == R.id.action_save_log) {
            saveLog();
        } else if (id == R.id.action_display_stats) {
            appendLogUiThread(performanceAnalysis.getStatistics(
                    p2pDevice != null ? p2pDevice.deviceAddress : ""));
        } else if (id == R.id.action_save_stats) {
            saveStatistics();
        }
        return super.onOptionsItemSelected(item);
    }

    private int settingsReqCode = 0x1313;

    private void showSettingsDialog() {
        Intent i = new Intent(this, SettingsActivity.class);
        startActivityForResult(i, settingsReqCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == settingsReqCode)
            //this.recreate();
            setupPreferences();
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
            if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST
                    || protocolTestMode == ProtocolTestMode.PROXY_SELECTION_TEST) {
                //Start a socket connection handler for group management operations
                if (!connectManagementAsServer()) return;
            }
            if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST) {
                //TODO Open Proxy sockets for data and management
                if (!connectProxyManagementAsServer()) return;
                if (!connectProxyDataAsServer()) return;
            }

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
            if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST
                    || protocolTestMode == ProtocolTestMode.PROXY_SELECTION_TEST) {
                if (!connectManagementAsClient(groupOwnerAddress)) return;
            }
            sendNearbyLegacyApsInfoHandler.postDelayed(sendNearbyLegacyApsInfoRunnable,
                    mSendNearbyLegacyApsInfoPeriod);

        }
        if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST) {
            if (info.groupFormed) {
                //Start a socket connection handler for Data exchange. This is done for both GO and non GO.
                if (!connectDataAsServer()) return;
            }
        }
    }

    private boolean connectDataAsServer() {
        try {
            if (dataHandler == null) {
                dataHandler = new SocketHandler(this.getHandler(), mDataPort);
                dataHandler.start();
            } else if (!dataHandler.isAlive())
                dataHandler.start();

        } catch (Exception e) {
            appendLog("Failed to create a DataSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean connectManagementAsServer() {
        try {
            if (mgntHandler == null) {
                mgntHandler = new SocketHandler(this.getHandler(), mMgmntPort);
                mgntHandler.start();
            } else if (!mgntHandler.isAlive())
                mgntHandler.start();
        } catch (Exception e) {
            appendLog("Failed to create a GroupOwnerSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean connectManagementAsClient(InetAddress groupOwnerAddress) {
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

    private boolean connectProxyDataAsServer() {
        try {
            if (proxyDataHandler == null) {
                proxyDataHandler = new SocketHandler(this.getHandler(), mProxyDataPort);
                proxyDataHandler.start();
            } else if (!proxyDataHandler.isAlive())
                proxyDataHandler.start();

        } catch (Exception e) {
            appendLog("Failed to create a ProxyDataSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean connectProxyManagementAsServer() {
        try {
            if (proxyMgntHandler == null) {
                proxyMgntHandler = new SocketHandler(this.getHandler(), mProxyMgmntPort);
                proxyMgntHandler.start();
            } else if (!proxyMgntHandler.isAlive())
                proxyMgntHandler.start();
        } catch (Exception e) {
            appendLog("Failed to create a GroupOwnerSocketHandler thread - " + e.getMessage());
            return false;
        }
        return true;
    }

    private void requestGroupInfo() {
        wifiP2pManager.requestGroupInfo(wifiP2pChannel, group -> {
            p2pGroup = group;
            if (p2pGroup.isGroupOwner()) {
                String legacySSID = group.getNetworkName();
                String legacyPassPhrase = group.getPassphrase();
                appendLog("LegacySSID -> " + legacySSID);
                appendLog("LegacyPassPhrase -> " + legacyPassPhrase);

                updateMyLegacyApDiscoveryInfo();
                createLegacyApService();
            }
        });
    }

    private void toggleWiFi() {
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

    synchronized void setIsWifiP2pEnabled(boolean state) {
        lastWifiState = isWifiP2pEnabled;
        isWifiP2pEnabled = state;

        if (!state)
            //Wifi is disabled, so tearDown everything until it is enabled
            tearDownGroupAndReRun(false);
        //else if (!lastWifiState && state)
        //We are sure now that the change in state is from disabled to enabled, so re-run again
        //tearDownGroupAndReRun();
    }

    private void removeWiFiP2pGroup() {
        removeWiFiP2pGroup(false);
    }

    private void removeWiFiP2pGroup(Boolean removeService) {
        if (wifiP2pManager != null) {
            if (p2pInfo != null && p2pInfo.groupFormed) {
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
            }

            if (removeService)
                removeLegacyApService();
        }
    }

    private boolean checkGroupFormedByMe() {
        return p2pInfo != null && p2pInfo.groupFormed && p2pInfo.isGroupOwner;
    }

    private void createWifiP2pGroup() {
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

    private synchronized boolean updateMyDeviceDiscoveryInfo() {
        BatteryInformation bInfo = new BatteryInformation();
        bInfo = bInfo.getBatteryStats(getApplicationContext());

        boolean result = !myDiscoveryInfo.deviceInfoAreEqual(bInfo.level
                , bInfo.capacity
                , bInfo.isCharging
                , myProposedIP);

        if (result)
            myDiscoveryInfo.updatePeerInfo(myDiscoveryInfo.deviceId
                    , bInfo.level
                    , bInfo.capacity
                    , bInfo.isCharging
                    , myProposedIP
                    , myDiscoveryInfo.legacySSID
                    , myDiscoveryInfo.legacyKey
                    , myDiscoveryInfo.numOfMembers);

        return result;
    }

    private boolean isReSendingDeviceInfoRequired() {
        boolean updated = updateMyDeviceDiscoveryInfo();
        return ((!mAddServiceOnChange) || (updated));
    }

    private boolean updateMyLegacyApDiscoveryInfo() {
        boolean result = false;
        if (p2pGroup != null) {
            result = !myDiscoveryInfo.legacyApInfoAreEqual(p2pGroup.getNetworkName()
                    , p2pGroup.getPassphrase(), p2pGroup.getClientList().size());

            if (result) {
                myDiscoveryInfo.legacySSID = p2pGroup.getNetworkName();
                myDiscoveryInfo.legacyKey = p2pGroup.getPassphrase();
                myDiscoveryInfo.numOfMembers = p2pGroup.getClientList().size();
            }
        }
        return result;
    }

    private boolean isReSendingLegacyApInfoRequired() {
        boolean updated = updateMyLegacyApDiscoveryInfo();
        return ((!mAddServiceOnChange) || (updated));
    }

    private synchronized void createDeviceInfoService() {
        Map<String, String> record = new HashMap<>();

        //Declare the type as "0" which means a DeviceInfo service record
        record.put(RECORD_TYPE, RECORD_TYPE_DEVICE_INFO);
        record.put(RECORD_LEVEL, Integer.toString(myDiscoveryInfo.batteryLevel));
        record.put(RECORD_CAPACITY, Integer.toString(myDiscoveryInfo.batteryCapacity));
        record.put(RECORD_CHARGING, Boolean.toString(myDiscoveryInfo.batteryIsCharging));
        record.put(RECORD_PROPOSED_IP, myProposedIP + discoveryPeersInfo.getConflictedPeerIPs());

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
                    appendLogUiThread("Remove DeviceInfo local service succeeded", true);
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

            //Declare the type as "1" which means a LegacyAP service record
            record.put(RECORD_TYPE, RECORD_TYPE_LEGACY_AP);
            record.put(RECORD_SSID, myDiscoveryInfo.legacySSID);
            record.put(RECORD_KEY, myDiscoveryInfo.legacyKey);
            record.put(RECORD_NUMBER_OF_MEMBERS, String.valueOf(myDiscoveryInfo.numOfMembers));

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

    private void removeLegacyApService() {
        if (serviceInfoLegacyAp != null)
            wifiP2pManager.removeLocalService(wifiP2pChannel, serviceInfoLegacyAp, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    appendLogUiThread("Remove LegacyAP local service succeeded", true);
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
                (fullDomainName, record, srcDevice) -> {
                    if (record.containsKey(RECORD_KEY))
                        encryptDecryptRecordElement(record, false, RECORD_KEY);
                    buddies.put(srcDevice.deviceAddress, record);
                    appendLogUiThread("DnsSdTxtRecord available ->"
                            + "[" + srcDevice.deviceName + "] " + record.toString());

                    /*
                    //Testing response time
                    if(performanceAnalysis.addMac(srcDevice.deviceAddress)) {
                        appendLogUiThread("\nDiscovery Test RESULT\n"
                                + performanceAnalysis.getDiscoveryTestStats());
                    }
                    */
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
                                if (discoveryPeersInfo.isMyProposedIpConflicting(myProposedIP
                                        , discoveryPeersInfo.extractConflictedIpsFromPeer(getProposedIpRecordElement(rec)))) {
                                    performanceAnalysis.conflictIpCount++;
                                    appendLogUiThread("My Proposed IP [" + myProposedIP + "] is conflicting, trying a new one");
                                    myProposedIP = discoveryPeersInfo.getConflictFreeIP(mMaxSubnetX, mMaxSubnetY);
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

                    private String getProposedIpRecordElement(Map<String, String> rec) {
                        if (rec.containsKey(RECORD_PROPOSED_IP))
                            return rec.get(RECORD_PROPOSED_IP);
                        else return "";
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
                            appendLogUiThread("Removed previous service request succeeded", true);
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

    private boolean isDeviceInfoRecord(Map<String, String> record) {
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

    private void connectToLegacyApAndOpenSockets(final String SSID, final String passPhrase) {
        Thread conThread = new Thread(() -> {
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

                            performanceAnalysis.bePmCount++;
                            //Try opening sockets connections with legacyAp
                            openLegacySocketConnections();
                            break;
                        }
                    }
                } else appendLogUiThread("Cannot get the result of a WiFi Scan!!");
            } else appendLogUiThread("Cannot start a WiFi Scan!!");
        });
        conThread.start();
    }

    private void configureAndConnectToLegacyAp(String BSSID, String SSID, String passPhrase) {
        //Create network configuration
        WifiConfiguration wifiConfiguration = new WifiConfiguration();
        wifiConfiguration.networkId = -100;
        wifiConfiguration.BSSID = BSSID;
        wifiConfiguration.SSID = Utilities.convertToQuotedString(SSID);
        wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wifiConfiguration.preSharedKey = Utilities.convertToQuotedString(passPhrase);
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
                appendLogUiThread(Utilities.convertIntToIpAddress(conInfo.getIpAddress())
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

    private synchronized void connectP2pDevice(final WifiP2pDevice device) {

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

                performanceAnalysis.beGmCount++;

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

    private synchronized void appendLogReceived(String dataMsg) {
        if (txtReceived != null) {
            txtReceived.append(dataMsg + "\n");
        }
        Log.d(TAG, "Msg read -> " + dataMsg);
    }

    private void appendLogReceivedUiThread(final String string) {
        runOnUiThread(() -> appendLogReceived(string));
    }

    private synchronized void appendLog(String string, Boolean logCatOnly, Boolean noNewLine) {
        if (!logCatOnly)
            if (txtLog != null)
                txtLog.append(string + (noNewLine ? "" : "\n"));

        Log.d(TAG, string);
    }

    private void appendLog(String string) {
        appendLog(string, false, false);
    }

    public void appendLog(String string, boolean logCatOnly) {
        appendLog(string, logCatOnly, false);
    }

    private void appendLogUiThread(final String string) {
        runOnUiThread(() -> appendLog(string, false, false));
    }

    private void appendLogUiThread(final String string, final boolean logCatOnly) {
        runOnUiThread(() -> appendLog(string, logCatOnly, false));
    }

    void updateThisDevice(WifiP2pDevice device) {
        p2pDevice = device;
        appendLogUiThread("Device Changed -> " /*+ device.toString()*/);
    }

    private void resetP2pStructures() {
        p2pDevice = null;
        p2pGroup = null;
        p2pInfo = null;
    }

    void requestP2pPeers() {
        wifiP2pManager.requestPeers(wifiP2pChannel, peers -> {
            //displayPeers(peers);
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

    private void tapAcceptInvitationAutomatically() {
        Thread thrd = new Thread(() -> {
            try {
                if (Build.MODEL.equals("GT-P3113")) {
                    for (int i = 0; i < 1; i++) {
                        Process process = Runtime.getRuntime().exec(
                                String.format(Locale.US,"su -c input tap %d %d", 440, 565 + i));
                        process.waitFor();
                        Log.e("SU input tap", "input tap command finished " + (565 + i));
                    }
                }
            } catch (IOException | InterruptedException ignored) {
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
        String rankStr = discoveryPeersInfo.getBestGoIsMe(this);
        appendLogUiThread("THE RANKS ARE AS FOLLOW:\n" + rankStr);
        if (rankStr.contains("YES")) {
            appendLogUiThread("I AM THE BEST, TRYING TO CREATE A GROUP");
            performanceAnalysis.beGoCount++;
            if (protocolTestMode == ProtocolTestMode.IP_CONFLICT_TEST) {
                if ((mRequestedNoOfRuns == -1) || (mRequestedNoOfRuns > performanceAnalysis.runNumber)) {
                    //As we are just testing the conflict in IPs, we do not have to proceed
                    //in the next EMC steps.
                    performanceAnalysis.runNumber++;
                    tearDownGroupAndReRun();
                } else if (mRequestedNoOfRuns == performanceAnalysis.runNumber) {
                    stopAllTests();
                }
            } else if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST
                    || protocolTestMode == ProtocolTestMode.GROUP_FORMATION_TEST
                    || protocolTestMode == ProtocolTestMode.PROXY_SELECTION_TEST) {
                createWifiP2pGroup();
            }
        } else {
            appendLogUiThread("AT LEAST SOMEONE ELSE IS BETTER THAN ME, TRYING TO CHOOSE GROUP");
            if (protocolTestMode == ProtocolTestMode.IP_CONFLICT_TEST) {
                if ((mRequestedNoOfRuns == -1) || (mRequestedNoOfRuns > performanceAnalysis.runNumber)) {
                    //As we are just testing the conflict in IPs, we do not have to proceed
                    //in the next EMC steps.
                    performanceAnalysis.runNumber++;
                    //The no. of times of being GM should not increased here, as the device may retry
                    //several times before connecting actually to a group. However, as we are testing
                    //for ip conflicts only, we are not going to suffer from this problem.
                    performanceAnalysis.beGmCount++;
                    tearDownGroupAndReRun();
                } else if (mRequestedNoOfRuns == performanceAnalysis.runNumber) {
                    stopAllTests();
                }
            } else if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST
                    || protocolTestMode == ProtocolTestMode.GROUP_FORMATION_TEST
                    || protocolTestMode == ProtocolTestMode.PROXY_SELECTION_TEST) {
                declareGM();
            }
        }
    }

    private void declareGoAcceptingGMs() {
        thisDeviceState = ThisDeviceState.GO_ACCEPTING_CONNECTIONS;
    }

    private void decideGroupAndConnect() {
        String rankStr = discoveryPeersInfo.decideGoAndSpareToConnect();
        appendLogUiThread("THE RANKS ARE AS FOLLOW:\n" + rankStr);
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
            String str = legacyGroupsInfo.calculateCoverage();
            appendLogUiThread("PROXY ASSIGNMENTS ARE AS FOLLOWS:\n\n" + str);

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
                        if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST
                                || protocolTestMode == ProtocolTestMode.PROXY_SELECTION_TEST) {
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
                        } else if (protocolTestMode == ProtocolTestMode.GROUP_FORMATION_TEST) {
                            appendLogUiThread("sendNearbyLegacyApsInfo: sent To GO -> "
                                    + discoveryPeersInfo.toStringGoOnly());
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

        groupSocketPeers.removeAllSocketManagers();
        groupSocketPeers.clear();

        if (proxySocketPeer != null)
            proxySocketPeer.removeSocketManagers();
        proxySocketPeer = reRun ? new SocketPeer() : null;

        discoveryPeersInfo.clear();
        discoveryPeersInfo.devices.clear();

        legacyGroupsInfo.clear();

        buddies.clear();

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
                ((SocketHandler) mgntHandler).closeServerSocket();
            } catch (Exception ignored) {
            }

        if (dataHandler != null)
            try {
                ((SocketHandler) dataHandler).closeServerSocket();
            } catch (Exception ignored) {
            }

        if (proxyMgntHandler != null) {
            try {
                ((SocketHandler) proxyMgntHandler).closeServerSocket();
            } catch (Exception ignored) {
            }
        }

        if (proxyDataHandler != null)
            try {
                ((SocketHandler) proxyDataHandler).closeServerSocket();
            } catch (Exception ignored) {
            }

        mgntHandler = null;
        dataHandler = null;
        proxyMgntHandler = null;
        proxyDataHandler = null;

        if (protocolTestMode == ProtocolTestMode.NO_TEST) {
            performanceAnalysis.reset();
        }

        if (reRun) {

            declareGoHandler.postDelayed(declareGoRunnable, mDeclareGoPeriod);
            thisDeviceState = ThisDeviceState.COLLECTING_DEVICE_INFO;

            startTimers();

            myProposedIP = Utilities.generateProposedIP(mMaxSubnetX, mMaxSubnetY);
            appendLogUiThread("My Proposed IP address is [" + myProposedIP + "]");
            updateMyDeviceDiscoveryInfo();
            createDeviceInfoService();
        }
    }

    private synchronized void startTimers() {
        try {
            if (protocolTestMode == ProtocolTestMode.FULL_EMC_TEST) {
                sendMyInfoTimer = new Timer("sendMyInfoTimer");
                sendPeersInfoTimer = new Timer("sendPeersInfoTimer");
                sendMyInfoTimer.schedule(new SendMyInfoTask(), 0, mSendMyInfPeriod);
                sendPeersInfoTimer.schedule(new SendPeersInfoTask(), 0, mSendPeersInfoPeriod);
            }

            discoverServicesTimer = new Timer("discoverServicesTimer");
            addServicesTimer = new Timer("addServicesTimer");
            discoverServicesTimer.schedule(new DiscoverServicesTask(), 0, mDiscoverServicesPeriod);
            addServicesTimer.schedule(new AddServicesTask(), 0, mAddServicesPeriod);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void stopTimers() {
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
                processDataMessageRead(msg);
                break;

            case DATA_SOCKET_HANDLE:
                processDataSocketHandle(msg);
                break;

            case MGMNT_MESSAGE_READ:
                processManagementMessageRead(msg);
                break;

            case MGMNT_SOCKET_HANDLE:
                processManagementSocketHandle(msg);
                break;

            case MGMNT_SOCKET_PEER_ADDR:
                break;

            case PROXY_DATA_MESSAGE_READ:
                processProxyDataMessageRead(msg);
                break;

            case PROXY_DATA_SOCKET_HANDLE:
                processProxyDataSocketHandle(msg);
                break;

            case PROXY_MGMNT_MESSAGE_READ:
                processProxyManagementMessageRead(msg);
                break;

            case PROXY_MGMNT_SOCKET_HANDLE:
                processProxyManagementSocketHandle(msg);
                break;
        }
        return true;
    }

    private void processProxyManagementSocketHandle(Message msg) {
        SocketManager sm;
        sm = (SocketManager) msg.obj;
        proxySocketPeer.proxyManagementSocketManager = sm;
        Log.d(TAG, "New ProxyManagementSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
    }

    private void processProxyManagementMessageRead(Message msg) {
        byte[] readBuf;
        String readMessage;
        SocketManager sm;
        readBuf = ((BufferSocket) msg.obj).buffer;
        readMessage = new String(readBuf, 0, msg.arg1);
        appendLog("Management msg read: " + readMessage);
        sm = ((BufferSocket) msg.obj).socketManager;
        processManagementMessage(readMessage, sm);
    }

    private void processProxyDataSocketHandle(Message msg) {
        SocketManager sm;
        sm = (SocketManager) msg.obj;
        proxySocketPeer.proxyDataSocketManager = sm;
        appendLog("New ProxyDataSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
    }

    private void processProxyDataMessageRead(Message msg) {
        byte[] readBuf;
        String readMessage;
        MessageTypeData msgTypeData;
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
    }

    private void processManagementSocketHandle(Message msg) {
        SocketManager sm;
        sm = (SocketManager) msg.obj;
        boolean peerIsGo = !p2pInfo.isGroupOwner;
        groupSocketPeers.addManagementSocketManagerToPeer(sm, peerIsGo);
        appendLog("New ManagementSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
    }

    private void processManagementMessageRead(Message msg) {
        byte[] readBuf;
        String readMessage;
        SocketManager sm;
        SocketPeer peer;
        readBuf = ((BufferSocket) msg.obj).buffer;
        readMessage = new String(readBuf, 0, msg.arg1);
        appendLog("Management msg read: " + readMessage);
        sm = ((BufferSocket) msg.obj).socketManager;
        //Logging stats
        peer = groupSocketPeers.getPeerFromSocketManager(sm);
        if (peer != null) {
            performanceAnalysis.addSocketStatistic(peer.deviceAddress, peer.ipAddress
                    , readMessage.length(), true);
        }

        processManagementMessage(readMessage, sm);
    }

    private void processDataSocketHandle(Message msg) {
        SocketManager sm;
        sm = (SocketManager) msg.obj;
        groupSocketPeers.addDataSocketManagerToPeer(sm);
        appendLog("New DataSocketHandle received -> " + sm.getSocket().getInetAddress().toString());
        appendLog("No of peers so far = " + groupSocketPeers.getPeers().size());
    }

    private void processDataMessageRead(Message msg) {
        SocketManager sm = ((BufferSocket) msg.obj).socketManager;
        byte[] readBuf = ((BufferSocket) msg.obj).buffer;
        // construct a string from the valid bytes in the buffer
        String readMessage = new String(readBuf, 0, msg.arg1);
        //Logging stats
        SocketPeer peer = groupSocketPeers.getPeerFromSocketManager(sm);
        if (peer != null) {
            performanceAnalysis.addSocketStatistic(peer.deviceAddress, peer.ipAddress
                    , readMessage.length(), false);
        }

        MessageTypeData msgTypeData = MessageHelper.getMessageTypeAndData(readMessage);
        if (msgTypeData != null) {
            if (msgTypeData.messageType == MessageType.DATA_GM_TO_GROUP) {
                String peerName = groupSocketPeers.getPeerNameFromSocketManager(sm);
                appendLogReceived("[" + peerName + "]: " + msgTypeData.messageData);
                //Forward data if me is a proxy
                forwardIfMeIsProxy(msgTypeData.messageData);

            } else if (msgTypeData.messageType == MessageType.STREAM_DATA_TEST) {
                String peerName = groupSocketPeers.getPeerNameFromSocketManager(sm);
                appendLogReceived("[" + peerName + "]: ...");
                //Forward data if me is a proxy
                forwardIfMeIsProxy(msgTypeData.messageData);
            } else if (msgTypeData.messageType == MessageType.DATA_LEGACY_AP_FROM_PROXY) {
                appendLogReceived("[LEGACY_FROM_PROXY]: " + msgTypeData.messageData);
            } else {
                appendLogReceived("Unknown DATA message !!!");
            }
        } else appendLogReceived("Corrupted DATA message !!!");
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
                    //} else if (msgTypeData.messageType == MessageType.MGMNT_GO_TO_GM_SPARE_LEGACY) {
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
            Thread th1 = new Thread(() -> {
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
                    ipAddress = Utilities.convertIntToIpAddress(wifiManager.getDhcpInfo().serverAddress);
            }
        }
        return ipAddress;
    }

    private void sendMyInfoToGOTask() {
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

    private void sendPeersInfoTask() {
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
                                + count + " Peers");
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
                    + Utilities.getWifiDirectIPAddress() + ","
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

    private void discoverServicesTask() {
        if (isWifiP2pEnabled) {
            if (!mDiscoverServiceOnDemand) {
                //clearAllLocalServices();
                clearAllServiceRequests();
                sleep(500);
                if (thisDeviceState == ThisDeviceState.COLLECTING_DEVICE_INFO
                        || thisDeviceState == ThisDeviceState.GM_SELECTING_GO
                    /*|| thisDeviceState == ThisDeviceState.GO_SENDING_LEGACY_INFO*/) {

                    discoverServices();
                    performanceAnalysis.sentServiceDiscoveryRequestCount++;
                }
            }
        }
    }

    private void addServicesTask() {
        if (isWifiP2pEnabled) {
            Runnable r1 = () -> {
                if (thisDeviceState == ThisDeviceState.COLLECTING_DEVICE_INFO
                        || thisDeviceState == ThisDeviceState.GO_SENDING_LEGACY_INFO
                        || thisDeviceState == ThisDeviceState.GO_ACCEPTING_CONNECTIONS) {
                    if (isReSendingDeviceInfoRequired()) {
                        removeDeviceInfoService();
                        sleep(500);
                        createDeviceInfoService();
                    }
                }
            };
            r1.run();

            Runnable r2 = () -> {
                if (thisDeviceState == ThisDeviceState.GO_SENDING_LEGACY_INFO
                        || thisDeviceState == ThisDeviceState.GO_ACCEPTING_CONNECTIONS) {
                    if (isReSendingLegacyApInfoRequired()) {
                        removeLegacyApService();
                        sleep(500);
                        createLegacyApService();
                    }
                }
            };
            r2.run();
        }
    }

    private enum ThisDeviceState {
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

            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                wifiScanCompleted = true;
            } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                final SupplicantState supplicantState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                if (supplicantState == SupplicantState.ASSOCIATED) {
                    wifiSupplicantAssociated = true;
                }
            }
        }
    }

    private class SendMyInfoTask extends TimerTask {
        public void run() {
            sendMyInfoToGOTask();
        }
    }

    private class SendPeersInfoTask extends TimerTask {
        public void run() {
            sendPeersInfoTask();
        }
    }

    private class DiscoverServicesTask extends TimerTask {
        public void run() {
            discoverServicesTask();
        }
    }

    private class AddServicesTask extends TimerTask {
        public void run() {
            addServicesTask();
        }
    }
}



