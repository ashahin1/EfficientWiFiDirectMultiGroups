package esnetlab.apps.android.wifidirect.efficientmultigroups;

/**
 * Created by eng_a on 3/28/2016.
 */
interface ProtocolConstants {

    String SERVICE_INSTANCE = "_wfd_emc";
    String SERVICE_REG_TYPE = "_emc._tcp";

    //Constants used in HandleMessage method
    int DATA_MESSAGE_READ = 0x400 + 1;
    int DATA_SOCKET_HANDLE = 0x400 + 2;
    int MGMNT_MESSAGE_READ = 0x400 + 3;
    int MGMNT_SOCKET_HANDLE = 0x400 + 4;
    int MGMNT_SOCKET_PEER_ADDR = 0x400 + 5;
    int PROXY_DATA_MESSAGE_READ = 0x400 + 6;
    int PROXY_DATA_SOCKET_HANDLE = 0x400 + 7;
    int PROXY_MGMNT_MESSAGE_READ = 0x400 + 8;
    int PROXY_MGMNT_SOCKET_HANDLE = 0x400 + 9;

    //Record specific constants
    String RECORD_TYPE = "tp";
    String RECORD_TYPE_DEVICE_INFO = "0";
    String RECORD_TYPE_LEGACY_AP = "1";
    String RECORD_LEVEL = "lvl";
    String RECORD_CAPACITY = "cap";
    String RECORD_CHARGING = "chrg";
    String RECORD_SSID = "ssid";
    String RECORD_KEY = "key";
    String RECORD_PROPOSED_IP = "pIP";
    String RECORD_NUMBER_OF_MEMBERS = "num";
    String PASSWORD = "AllahAkbarAllahA";

    //Pref_Keys for port parameters
    String PREF_MGMNT_PORT = "group_management_port_text";
    String PREF_DATA_PORT = "group_data_port_text";
    String PREF_PROXY_MGMNT_PORT = "proxy_management_port_text";
    String PREF_PROXY_DATA_PORT = "proxy_data_port_text";

    //Pref_Keys for ranking parameters
    String PREF_RANK_ALPHA = "rank_alpha_text";
    String PREF_RANK_BETA = "rank_beta_text";
    String PREF_RANK_GAMMA = "rank_gamma_text";
    String PREF_RANK_MAX_CAPACITY = "rank_max_capacity_text";

    //Pref_Keys for protocol timing parameters
    String PREF_SEND_MY_INF_PERIOD = "send_my_info_text";
    String PREF_SEND_PEERS_INFO_PERIOD = "send_peers_info_text";
    String PREF_DISCOVER_SERVICES_PERIOD = "discover_services_text";
    String PREF_ADD_SERVICES_PERIOD = "add_services_text";
    String PREF_DECLARE_GO_PERIOD = "declare_go_text";
    String PREF_DECIDE_GROUP_PERIOD = "decide_group_text";
    String PREF_DECIDE_PROXY_PERIOD = "decide_proxy_text";
    String PREF_SEND_NEARBY_LEGACY_APS_INFO_PERIOD = "send_nearby_legacy_aps_text";
    String PREF_SEND_TEAR_DOWN_PERIOD = "send_tear_down_text";

    //Pref_Keys for testing parameters
    String PREF_REQUESTED_NO_OF_RUNS = "requested_no_of_runs_text";
    String PREF_ADD_SERVICE_ON_CHANGE = "add_service_on_change_switch";
    String PREF_DISCOVER_SERVICES_ON_DEMAND = "discover_service_on_demand_switch";
    String PREF_NO_OF_DEVICES = "no_of_devices_text";
    String PREF_SUBNET_X = "subnet_x_text";
    String PREF_SUBNET_Y = "subnet_y_text";
}
