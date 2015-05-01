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
public class ClientSocketHandler extends Thread {

    private static final String TAG = "ClientSocketHandler";
    public PeerConnectionListener pListner = null;
    private Handler handler;
    private SocketManager socketManager;
    private InetAddress mAddress;
    private String sAddress;
    private int port;

    public ClientSocketHandler(Handler handler, InetAddress serverAddress, int port) {
        this.handler = handler;
        this.mAddress = serverAddress;
        this.sAddress = this.mAddress.getHostAddress();
        this.port = port;
    }

    public ClientSocketHandler(Handler handler, String serverAddress, int port) {
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
            return;
        }
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

}

