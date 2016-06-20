package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by Ahmed on 4/8/2015.
 */
class ClientSocketHandler extends Thread {

    private static final String TAG = "ClientSocketHandler";
    private PeerConnectionListener pListner = null;
    private Handler handler;
    private SocketManager socketManager;
    private String sAddress;
    private int port;

    ClientSocketHandler(Handler handler, InetAddress serverAddress, int port) {
        this.handler = handler;
        this.sAddress = serverAddress.getHostAddress();
        this.port = port;
    }

    ClientSocketHandler(Handler handler, String serverAddress, int port) {
        this.handler = handler;
        this.sAddress = serverAddress;
        this.port = port;
    }

    @Override
    public void run() {
        Socket socket = new Socket();
        try {
            socket.bind(null);
            socket.connect(new InetSocketAddress(sAddress,
                    port), 5000);
            Log.d(TAG, "Launching the I/O handler");
            socketManager = new SocketManager(socket, handler, port);
            socketManager.pListner = pListner;
            new Thread(socketManager).start();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    SocketManager getSocketManager() {
        return socketManager;
    }

}

