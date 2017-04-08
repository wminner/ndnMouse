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
import edu.ucla.cs.ndnmouse.R;

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
    private InetAddress mReplyAddr;
    private int mReplyPort;
    private final int mPort;
    private boolean mIsRunning;

    private int mPCWidth;
    private int mPCHeight;
    private int mPhoneWidth;
    private int mPhoneHeight;
    private double mRatioWidth;
    private double mRatioHeight;

    private Point mLastPos = new Point(0,0);

    /**
     * Constructor for server
     *
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     */
    public ServerUDP(MouseActivity activity, int port, int width, int height) {
        mActivity = activity;
        mPort = port;
        mPhoneWidth = width;
        mPhoneHeight = height;
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
        mReplyAddr = packet.getAddress();
        mReplyPort = packet.getPort();
        String data = new String(packet.getData());
        // Trim null bytes off end
        data = data.substring(0, data.indexOf('\0'));

        Log.d(TAG, "Received request data: " + data);

        try {
            if (data.startsWith("GET ")) {
                int start = data.indexOf(' ') + 1;
                int end = data.indexOf('\n', start);
                String[] monitorRes = data.substring(start, end).split("x");
                if (monitorRes.length == 2) {
                    mPCWidth = Integer.valueOf(monitorRes[0]);
                    mPCHeight = Integer.valueOf(monitorRes[1]);
                    Log.d(TAG, "Client's monitor resolution is " + mPCWidth + "x" + mPCHeight);

                    // Calculate ratios between server screen (phone) and client screen (pc)
                    mRatioWidth = (float) mPCWidth / mPhoneWidth;
                    mRatioHeight = (float) mPCHeight / mPhoneHeight;

                    Log.d(TAG, "RatioWidth: " + mRatioWidth + ", RatioHeight: " + mRatioHeight);

                    // Start mouse in middle of monitor
                    byte[] reply = (mPCWidth /2 + "," + mPCHeight /2 + "\n").getBytes();
                    DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, mReplyAddr, mReplyPort);
                    mSocket.send(replyPacket);

                    // TODO spin off worker thread to do this work (so we don't block the server)
                    while (mIsRunning) {
                        Thread.sleep(100);
                        Point currPos = mActivity.getCurrentPosition();
                        // Skip update if no movement happened since the last update
                        if (!currPos.equals(mLastPos)) {
                            mLastPos.set(currPos.x, currPos.y);
                            int scaledX = (int) (currPos.x * mRatioWidth);
                            int scaledY = (int) (currPos.y * mRatioHeight);
                            reply = (scaledX + "," + scaledY + "\n").getBytes();
                            replyPacket = new DatagramPacket(reply, reply.length, mReplyAddr, mReplyPort);
                            mSocket.send(replyPacket);
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to get client's resolution!");
                }
            }
        } catch (InterruptedException | SocketException e) {
            e.printStackTrace();
        }
    }

    public void ExecuteClick(int click) throws IOException {
//        byte[] reply;
//        if (click.equals(mActivity.getString(R.string.action_left_click_down))) {
//            reply = (mActivity.getString(R.string.action_left_click_down) + "\n").getBytes();
//        } else if (click.equals(mActivity.getString(R.string.action_right_click_down))) {
//            reply = (mActivity.getString(R.string.action_right_click_down) + "\n").getBytes();
//        } else {
//            Log.e(TAG, "Improper click type: " + click);
//            return;
//        }
        byte[] reply = (mActivity.getString(click)).getBytes();

        DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, mReplyAddr, mReplyPort);
        mSocket.send(replyPacket);
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
