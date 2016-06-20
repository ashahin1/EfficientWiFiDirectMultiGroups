package esnetlab.apps.android.wifidirect.efficientmultigroups;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * Created by Ahmed on 4/8/2015.
 */
class SocketManager implements Runnable, ProtocolConstants {

    private static final String TAG = "SocketManager";
    PeerConnectionListener pListner = null;
    private Socket socket = null;
    private Handler handler;
    private OutputStream oStream;
    private int whatHandle;
    private int whatRead;

    SocketManager(Socket socket, Handler handler, int portUsed/*boolean isDataSocket*/) {
        this.socket = socket;
        this.handler = handler;
        //this.isDataSocket = isDataSocket;
        if (portUsed == EfficientWiFiP2pGroupsActivity.mMgmntPort) {
            whatHandle = MGMNT_SOCKET_HANDLE;
            whatRead = MGMNT_MESSAGE_READ;
        } else if (portUsed == EfficientWiFiP2pGroupsActivity.mDataPort) {
            whatHandle = DATA_SOCKET_HANDLE;
            whatRead = DATA_MESSAGE_READ;
        } else if (portUsed == EfficientWiFiP2pGroupsActivity.mProxyMgmntPort) {
            whatHandle = PROXY_MGMNT_SOCKET_HANDLE;
            whatRead = PROXY_MGMNT_MESSAGE_READ;
        } else if (portUsed == EfficientWiFiP2pGroupsActivity.mProxyDataPort) {
            whatHandle = PROXY_DATA_SOCKET_HANDLE;
            whatRead = PROXY_DATA_MESSAGE_READ;
        }
    }

    @Override
    public void run() {
        try {

            InputStream iStream = socket.getInputStream();
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
                    Log.d(TAG, "Rec:" + Arrays.toString(buffer));
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

    private void write(byte[] buffer) {
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

    void writeFormattedMessage(String dataToSend, MessageType messageType) {
        String formattedMsg = MessageHelper.getFormattedMessage(messageType, dataToSend);
        write(formattedMsg.getBytes());
    }

    Socket getSocket() {
        return socket;
    }
}

