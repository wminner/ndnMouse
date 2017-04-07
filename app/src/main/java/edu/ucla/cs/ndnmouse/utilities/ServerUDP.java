package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.util.Log;

import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import edu.ucla.cs.ndnmouse.MouseActivity;

/**
 * Class to provide UDP communication with the PC client
 *
 * Based off of example code at:
 * https://developer.android.com/samples/PermissionRequest/src/com.example.android.permissionrequest/SimpleWebServer.html
 */
public class ServerUDP implements Runnable {

    private static final String TAG = ServerUDP.class.getSimpleName();

    private MouseActivity mActivity;

    private DatagramSocket mSocket;
    private final int mPort;
    private boolean mIsRunning;

    /**
     * Constructor for server
     *
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     */
    public ServerUDP(MouseActivity activity, int port) {
        mActivity = activity;
        mPort = port;
    }

    public void start() {
        mIsRunning = true;
        new Thread(this).start();
        mActivity.setServerThread(Thread.currentThread());
        Log.d(TAG, "Started UDP server... " + getIPAddress(true) + ":" + mPort);
    }

    public void stop() {
        try {
            mIsRunning = false;
            if (null != mSocket) {
                mSocket.close();
                mSocket = null;
            }
            Log.d(TAG, "Stopped UDP server...");
        } catch (Exception e) {
            Log.e(TAG, "Error closing the server socket.", e);
        }
    }

    @Override
    public void run() {
        try {
            mSocket = new DatagramSocket(mPort);
            while (mIsRunning) {
                byte[] buf = new byte[64];

                // Get request
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                mSocket.receive(packet);  // Blocks program flow
                Log.d(TAG, "Received client request...");

                // Process request and send reply
                handle(packet);
            }
        } catch (SocketException e) {
            Log.d(TAG, "Socket got disconnected!");
        } catch (IOException e) {
            Log.e(TAG, "Web server error.", e);
        }
    }

    private void handle(DatagramPacket packet) throws IOException {
        // Get address and port to send reply to
        InetAddress replyAddr = packet.getAddress();
        int replyPort = packet.getPort();
        String data = new String(packet.getData());
        // Trim null bytes off end
        data = data.substring(0, data.indexOf('\0'));

        Log.d(TAG, "Received request data: " + data);

        try {
            if (data.equals("GET position\n")) {
                while (mIsRunning) {
                    Point lastPos = mActivity.getLastPosition();
                    byte[] reply = (lastPos.x + "," + lastPos.y + "\n").getBytes();
                    DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, replyAddr, replyPort);
                    mSocket.send(replyPacket);
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException | SocketException e) {
            e.printStackTrace();
        }
    }

    private void writeServerError(PrintStream output) {
        output.println("HTTP/1.0 500 Internal Server Error");
        output.flush();;
    }

    /**
     * Get IP address from first non-localhost interface
     * Based on code from:
     * http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    private static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return (delim < 0) ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
