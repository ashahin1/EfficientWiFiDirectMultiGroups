package esnetlab.apps.android.wifidirect.efficientmultigroups;

public enum MessageType {
    MGMNT_KEEP_ALIVE,
    MGMNT_GM_TO_GO_FOUND_LEGACY_AP,
    MGMNT_GO_TO_GM_SELECTED_LEGACY,
    MGMNT_GO_TO_GM_SPARE_LEGACY,
    MGMNT_GO_TO_GM_TEAR_DOWN,
    MGMNT_PROXY_TO_LEGACY_AP_TEAR_DOWN,
    DATA_GM_TO_GROUP,
    DATA_PROXY_TO_LEGACY_AP,
    DATA_LEGACY_AP_FROM_PROXY,
    STREAM_DATA_TEST
}
