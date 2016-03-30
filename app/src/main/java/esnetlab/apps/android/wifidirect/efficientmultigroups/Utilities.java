package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * Created by eng_a on 3/30/2016.
 */
public class Utilities {
    private static final String TAG = "Utilities";

    private static final Pattern IPV4_ADDR_PATTERN = Pattern.compile(
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");
    private static final Pattern MAC_ADDR_PATTERN = Pattern.compile(
            "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");



    public static boolean isValidIPv4Addr(final String ip) {
        return IPV4_ADDR_PATTERN.matcher(ip).matches();
    }

    public static boolean isValidMacAddr(final String mac) {
        return MAC_ADDR_PATTERN.matcher(mac).matches();
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

    public static void writeStringToFile(String contents, String fileNameStart) {
        if (isExternalStorageWritable()) {
            Date dt = Calendar.getInstance().getTime();
            File pFile;
            FileOutputStream pOsFile;
            String sFileName = "/" + fileNameStart + dt.getTime() + ".txt";
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            try {
                pFile = new File(path, sFileName);
                pOsFile = new FileOutputStream(pFile);
                pOsFile.write(contents.getBytes());
                pOsFile.flush();
                pOsFile.close();
            } catch (Exception ex) {
                Log.d(TAG, "writeStringToFile: Error writing file \n\t" + ex.toString());
            }
        }
    }

    /* Checks if external storage is available for read and write */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
