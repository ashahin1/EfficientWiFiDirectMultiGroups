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
/**
 * The implementation of a ServerSocket handler. This is used by the wifi p2p
 * members including the group owner.
 */
public class GroupDataSocketHandler extends Thread {

    private static final String TAG = "GroupDataSocketHandler";
    private final int THREAD_COUNT = 10;
    /**
     * A ThreadPool for client sockets.
     */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            THREAD_COUNT, THREAD_COUNT, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());
    ServerSocket socket = null;
    private Handler handler;

    public GroupDataSocketHandler(Handler handler) throws IOException {
        try {
            socket = new ServerSocket(EfficientWiFiP2pGroupsActivity.mDataPort);
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
                pool.execute(new SocketManager(socket.accept(), handler, EfficientWiFiP2pGroupsActivity.mDataPort));
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


