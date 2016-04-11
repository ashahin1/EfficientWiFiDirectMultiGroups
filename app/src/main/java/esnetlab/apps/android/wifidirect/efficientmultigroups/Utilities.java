package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
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

    private static Random random = null;


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


    public static byte[] getWifiMacAddressLeastByte() {
        //adapted from http://robinhenniges.com/en/android6-get-mac-address-programmatically
        byte[] macBytes = null;

        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                macBytes = nif.getHardwareAddress();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return macBytes;
    }

    //Adapted from http://stackoverflow.com/questions/16730711/get-my-wifi-ip-address-android
    public static String convertIntToIpAddress(int ipAddress) {
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

    static public String generateProposedIP() {
        //Initialize the seed based on the least significant byte of the wifi mac address
        if (random == null) {
            byte[] macBytes = getWifiMacAddressLeastByte();
            long seed = 0;

            if (macBytes != null) {
                for (int i = 0; i < macBytes.length - 2; i++) {
                    seed += (macBytes[i] & 0xFF) + (seed << (i * 8));
                }
                random = new Random(seed);
            } else {
                random = new Random();
            }
        }

        //Avoid 10.0.x.x and 10.1.x.x and 10.10.x.x
        int ip2Octet = random.nextInt(254);
        while ((ip2Octet == 0) || (ip2Octet == 1) || (ip2Octet == 10))
            ip2Octet = random.nextInt(254);

        //Avoid 10.x.0.x and 10.x.1.x
        int ip3octet = random.nextInt(254);
        while ((ip3octet == 0) || (ip3octet == 1))
            ip3octet = random.nextInt(254);

        return ip2Octet + "." + ip3octet;
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

    public static boolean writeStringToFile(String contents, String fileNameStart) {
        if (isExternalStorageWritable()) {
            Date dt = Calendar.getInstance().getTime();
            File pFile;
            FileOutputStream pOsFile;
            String sFileName = "/" + fileNameStart + dt.getTime() + ".txt";
            File path = Environment.getExternalStorageDirectory();
            path = new File(path.getAbsolutePath(), "/EMC");
            try {
                if (!path.exists())
                    path.mkdir();
                pFile = new File(path, sFileName);
                pOsFile = new FileOutputStream(pFile);
                pOsFile.write(contents.getBytes());
                pOsFile.flush();
                pOsFile.close();
            } catch (Exception ex) {
                Log.d(TAG, "writeStringToFile: Error writing file \n\t" + ex.toString());
                return false;
            }
        }
        return true;
    }

    /* Checks if external storage is available for read and write */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
