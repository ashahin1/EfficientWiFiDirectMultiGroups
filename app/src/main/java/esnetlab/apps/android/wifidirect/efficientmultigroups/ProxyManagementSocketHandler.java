package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ahmed on 4/8/2015.
 */
public class ProxyManagementSocketHandler extends Thread {

    private static final String TAG = "ProxyManagementSH";
    private final int THREAD_COUNT = 10;
    /**
     * A ThreadPool for Group Management sockets.
     */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            THREAD_COUNT, THREAD_COUNT, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
    ServerSocket socket = null;
    private Handler handler;

    public ProxyManagementSocketHandler(Handler handler) throws IOException {
        try {
            socket = new ServerSocket(EfficientWiFiP2pGroupsActivity.PROXY_MGMNT_PORT);
            this.handler = handler;
            Log.d(TAG, "Socket Started");
        } catch (IOException e) {
            e.printStackTrace();
            pool.shutdownNow();
            throw e;
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // A blocking operation. Initiate a SocketManager instance when
                // there is a new connection
                pool.execute(new SocketManager(socket.accept(), handler, EfficientWiFiP2pGroupsActivity.PROXY_MGMNT_PORT));
                Log.d(TAG, "Launching the I/O handler");

            } catch (IOException e) {
                try {
                    if (socket != null && !socket.isClosed())
                        socket.close();
                } catch (IOException ioe) {

                }
                e.printStackTrace();
                pool.shutdownNow();
                break;
            }
        }
    }

    public void closeServerSocket() {
        if (socket != null)
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
}

