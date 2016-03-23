package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by Ahmed on 4/8/2015.
 */
public class SocketManager implements Runnable {

    private static final String TAG = "SocketManager";
    public PeerConnectionListener pListner = null;
    private Socket socket = null;
    private Handler handler;
    private InputStream iStream;
    private OutputStream oStream;
    private int whatHandle;
    private int whatRead;
    private int port;

    public SocketManager(Socket socket, Handler handler, int portUsed/*boolean isDataSocket*/) {
        this.socket = socket;
        this.handler = handler;
        this.port = portUsed;
        //this.isDataSocket = isDataSocket;
        if (port == EfficientWiFiP2pGroupsActivity.mMgmntPort) {
            whatHandle = EfficientWiFiP2pGroupsActivity.MGMNT_SOCKET_HANDLE;
            whatRead = EfficientWiFiP2pGroupsActivity.MGMNT_MESSAGE_READ;
        } else if (port == EfficientWiFiP2pGroupsActivity.mDataPort) {
            whatHandle = EfficientWiFiP2pGroupsActivity.DATA_SOCKET_HANDLE;
            whatRead = EfficientWiFiP2pGroupsActivity.DATA_MESSAGE_READ;
        } else if (port == EfficientWiFiP2pGroupsActivity.mProxyMgmntPort) {
            whatHandle = EfficientWiFiP2pGroupsActivity.PROXY_MGMNT_SOCKET_HANDLE;
            whatRead = EfficientWiFiP2pGroupsActivity.PROXY_MGMNT_MESSAGE_READ;
        } else if (port == EfficientWiFiP2pGroupsActivity.mProxyDataPort) {
            whatHandle = EfficientWiFiP2pGroupsActivity.PROXY_DATA_SOCKET_HANDLE;
            whatRead = EfficientWiFiP2pGroupsActivity.PROXY_DATA_MESSAGE_READ;
        }
    }

    @Override
    public void run() {
        try {

            iStream = socket.getInputStream();
            oStream = socket.getOutputStream();
            byte[] buffer = new byte[1024];
            int bytes;
            handler.obtainMessage(whatHandle, this).sendToTarget();

            while (true) {
                try {
                    if (socket.isClosed()) break;
                    // Read from the InputStream
                    bytes = iStream.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    //TODO Enable this
/*
                    //Decrypt data before proceeding------------------------
                    buffer = SecurityHelper.decodeBytes(buffer, bytes);
                    bytes = buffer.length;
                    //-------------------------------------------------------
*/
                    BufferSocket bufferSocket = new BufferSocket();
                    bufferSocket.buffer = buffer;
                    bufferSocket.socketManager = this;

                    // Send the obtained bytes to the UI Activity
                    Log.d(TAG, "Rec:" + String.valueOf(buffer));
                    handler.obtainMessage(whatRead, bytes, -1, bufferSocket).sendToTarget();
                } catch (IOException e) {
                    //SocketPeer.peerDisconnected(pListner, port);
                    Log.e(TAG, "disconnected", e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                //SocketPeer.peerDisconnected(pListner, port);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(byte[] buffer) {
        try {
            //TODO Enable this
/*
            //Encrypt data before proceeding------------------------
            buffer = SecurityHelper.encodeBytes(buffer, buffer.length);
            //-------------------------------------------------------
*/
            oStream.write(buffer);
        } catch (IOException e) {
            //SocketPeer.peerDisconnected(pListner, port);
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void writeFormattedMessage(String dataToSend, MessageType messageType) {
        String formattedMsg = MessageHelper.getFormattedMessage(messageType, dataToSend);
        write(formattedMsg.getBytes());
    }

    public Socket getSocket() {
        return socket;
    }
}

