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
class SocketHandler extends Thread {

    private static final String TAG = "SocketHandler";
    private final int THREAD_COUNT = 10;
    /**
     * A ThreadPool for client sockets.
     */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            THREAD_COUNT, THREAD_COUNT, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());
    private ServerSocket socket = null;
    private Handler handler;
    private int portNo;

    SocketHandler(Handler handler, int portNo) throws IOException {
        try {
            this.handler = handler;
            this.portNo = portNo;

            socket = new ServerSocket(portNo);
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
                pool.execute(new SocketManager(socket.accept(), handler, portNo));
                Log.d(TAG, "Launching the I/O handler");

            } catch (IOException e) {
                try {
                    if (socket != null && !socket.isClosed())
                        socket.close();
                } catch (IOException ignored) {

                }
                e.printStackTrace();
                pool.shutdownNow();
                break;
            }
        }
    }

    void closeServerSocket() {
        if (socket != null)
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        pool.shutdownNow();
    }
}


