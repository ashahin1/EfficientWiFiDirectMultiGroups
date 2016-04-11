package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * Created by Ahmed on 3/26/2015.
 */
public class BatteryInformation {
    public boolean isCharging = false;
    public int level = 0;
    public int scale = 0;
    public float percent = 0.0f;
    public int capacity = 0;

    public BatteryInformation getBatteryStats(Context context) {
        if (context != null) {
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, iFilter);

            if (batteryStatus != null) {
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
                level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                percent = level / (float) scale;
                capacity = getBatteryCapacity(context).intValue();
                return this;
            }
        }
        return null;
    }

    //Taken from http://stackoverflow.com/questions/22285179/query-for-battery-capacity
    public Double getBatteryCapacity(Context context) {
        // Power profile class instance
        Object mPowerProfile_ = null;
        // Reset variable for battery capacity
        double batteryCapacity = 0;
        // Power profile class name
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";

        try {
            // Get power profile class and create instance. We have to do this
            // dynamically because android.internal package is not part of public API
            mPowerProfile_ = Class.forName(POWER_PROFILE_CLASS).getConstructor(Context.class).newInstance(context);
        } catch (Exception e) {
            // Class not found?
            e.printStackTrace();
        }

        try {
            // Invoke PowerProfile method "getAveragePower" with param "battery.capacity"
            batteryCapacity = (Double) Class
                    .forName(POWER_PROFILE_CLASS)
                    .getMethod("getAveragePower", java.lang.String.class)
                    .invoke(mPowerProfile_, "battery.capacity");
        } catch (Exception e) {
            // Something went wrong
            e.printStackTrace();
        }

        return batteryCapacity;
    }
}
