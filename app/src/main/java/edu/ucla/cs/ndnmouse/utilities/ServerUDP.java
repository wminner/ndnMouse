package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;

/**
 * Class to provide UDP communication with the PC client
 *
 * Based off of example code at:
 * https://developer.android.com/samples/PermissionRequest/src/com.example.android.permissionrequest/SimpleWebServer.html
 */
public class ServerUDP implements Runnable, Server {

    private static final String TAG = ServerUDP.class.getSimpleName();

    private MouseActivity mMouseActivity;

    private DatagramSocket mSocket;
    private final int mPort;
    private boolean mServerIsRunning = false;
    private boolean mUseRelativeMovement;
    private final static int mUpdateIntervalMillis = 50;  // Number of milliseconds to wait before sending next update. May require tuning.

    private int mPhoneWidth;
    private int mPhoneHeight;
    private HashMap<InetAddress, WorkerThread> mClientThreads;

    /**
     * Constructor for server
     *
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     */
    public ServerUDP(MouseActivity activity, int port, int width, int height, boolean useRelativeMovement) {
        mMouseActivity = activity;
        mPort = port;
        mPhoneWidth = width;
        mPhoneHeight = height;
        mUseRelativeMovement = useRelativeMovement;
        mClientThreads = new HashMap<>();
    }

    /**
     * Starts server by spinning it off as a background thread
     */
    public void start() {
        mServerIsRunning = true;
        Thread thread = new Thread(this);
        thread.start();
        // mMouseActivity.setServerThread(thread);
        Log.d(TAG, "Started UDP server... " + getIPAddress(true) + ":" + mPort);
    }

    /**
     * Stops server thread, cleans up all the worker threads, and closes the socket
     */
    public void stop() {
        try {
            mServerIsRunning = false;
            // Stop all client threads
            for (WorkerThread client : mClientThreads.values()) {
                client.stop();
            }
            mClientThreads.clear();

            // Close the shared socket
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
            // Create a new UDP socket
            mSocket = new DatagramSocket(mPort);
            while (mServerIsRunning) {
                byte[] buf = new byte[64];
                // Get request
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                mSocket.receive(packet);  // Blocks program flow

                // Check message for new client
                String data = new String(packet.getData());
                // Trim null bytes off end
                data = data.substring(0, data.indexOf('\0'));

                // If new client...
                if (data.startsWith(mMouseActivity.getString(R.string.protocol_opening_request))) {
                    if (!mClientThreads.containsKey(packet.getAddress())) {
                        // Start a new worker thread to handle updates, and store its reference
                        WorkerThread worker = new WorkerThread(mSocket, packet);
                        mClientThreads.put(packet.getAddress(), worker);
                        Log.d(TAG, "Number of clients: " + mClientThreads.size());
                    } else {
                        mClientThreads.get(packet.getAddress()).sendAck();
                    }
                // Otherwise if existing client no longer wants updates...
                } else if (data.startsWith(mMouseActivity.getString(R.string.protocol_closing_request))) {
                    // Look up its thread and stop it
                    if (mClientThreads.containsKey(packet.getAddress())) {
                        mClientThreads.get(packet.getAddress()).stop();
                        mClientThreads.remove(packet.getAddress());
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Web server was interrupted and is now closed.", e);
        }
    }

    /**
     * Send a click command to an existing client
     *
     * @param click identifier for the type of click
     * @throws IOException for socket IO error
     */
    public void ExecuteClick(int click) throws IOException {
        for (WorkerThread client : mClientThreads.values()) {
            if (null != client.mReplyAddr && 0 != client.mReplyPort) {
                byte[] reply = (mMouseActivity.getString(click)).getBytes();
                DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, client.mReplyAddr, client.mReplyPort);
                mSocket.send(replyPacket);
            }
        }
    }

    /**
     * Get IP address from first non-localhost interface
     * Based on code from:
     * http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    public static String getIPAddress(boolean useIPv4) {
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

    /**
     * Server parent thread spins off worker threads to do the actual transmissions
     */
    private class WorkerThread implements Runnable {

        private boolean mWorkerIsRunning = false;

        private final DatagramSocket mSocket;
        final InetAddress mReplyAddr;
        final int mReplyPort;

        private int mPCWidth;
        private int mPCHeight;
        private float mRatioWidth;
        private float mRatioHeight;

        private Point mLastPos = new Point(0, 0);

        /**
         * Constructor
         *
         * @param socket shared UDP socket from that parent is managing
         * @param packet initial packet that client uses to establish a connection with the server
         * @throws SocketException for socket IO error
         */
        WorkerThread(DatagramSocket socket, DatagramPacket packet) throws SocketException {
            mSocket = socket;
            // Get address and port to send reply to
            mReplyAddr = packet.getAddress();
            mReplyPort = packet.getPort();

            if (parseInitialRequest(packet))
                start();
            else
                stop();
        }

        /**
         * Spins the class off into its own background thread to do the rest of the position updates
         */
        void start() {
            mWorkerIsRunning = true;
            new Thread(this).start();
            Log.d(TAG, "Started worker thread for client " + mReplyAddr + ":" + mReplyPort);
        }

        /**
         * Stops the worker thread
         */
        void stop() {
            mWorkerIsRunning = false;
            Log.d(TAG, "Stopped worker thread for client " + mReplyAddr + ":" + mReplyPort);
        }

        /**
         * Parses the initial GET packet and retrieves necessary info in order to respond correctly
         *
         * @param initPacket initial packet that the client sends to the server to get position updates
         * @return boolean true if all parsing completed successfully, otherwise false
         */
        private boolean parseInitialRequest(DatagramPacket initPacket) {
            String data = new String(initPacket.getData());
            // Trim null bytes off end
            data = data.substring(0, data.indexOf('\0'));

            Log.d(TAG, "Received request data: " + data);

            if (data.startsWith(mMouseActivity.getString(R.string.protocol_opening_request))) {
                int start = data.indexOf(' ') + 1;
                int end = data.indexOf('\n', start);
                String[] monitorRes = data.substring(start, end).split("x");
                if (monitorRes.length == 2) {
                    mPCWidth = Integer.valueOf(monitorRes[0]);
                    mPCHeight = Integer.valueOf(monitorRes[1]);
                    // Log.d(TAG, "Client's monitor resolution is " + mPCWidth + "x" + mPCHeight);

                    // Calculate ratios between server screen (phone) and client screen (pc)
                    mRatioWidth = (float) mPCWidth / mPhoneWidth;
                    mRatioHeight = (float) mPCHeight / mPhoneHeight;

                    Log.d(TAG, "RatioWidth: " + mRatioWidth + ", RatioHeight: " + mRatioHeight);
                }
            } else {
                Log.e(TAG, "Failed to get client's resolution!");
                return false;
            }
            return true;
        }

        public void sendAck() throws IOException {
            byte[] reply = (mMouseActivity.getString(R.string.protocol_opening_reply)).getBytes();
            DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, mReplyAddr, mReplyPort);
            mSocket.send(replyPacket);
        }

        @Override
        public void run() {
            try {
                sendAck();
                while (mWorkerIsRunning) {
                    // Don't send too many updates (may require tuning)
                    Thread.sleep(mUpdateIntervalMillis);
                    Point position;
                    String moveType;
                    // Using relative movement...
                    if (mUseRelativeMovement) {
                        position = mMouseActivity.getRelativePosition();
                        moveType = mMouseActivity.getString(R.string.protocol_move_relative);
                        // Skip update if no relative movement since last update
                        if (position.equals(0, 0))
                            continue;
                    } else {    // Using absolute movement...
                        position = mMouseActivity.getAbsolutePosition();
                        moveType = mMouseActivity.getString(R.string.protocol_move_absolute);
                        // Skip update if no movement happened since the last update
                        if (position.equals(mLastPos)) {
                            continue;
                        } else
                            mLastPos.set(position.x, position.y);
                    }
                    // Find scaled x and y position according to client's resolution
                    int scaledX = (int) (position.x * mRatioWidth);
                    int scaledY = (int) (position.y * mRatioHeight);
                    // Build reply packet and send out socket
                    byte[] reply = (moveType + " " + scaledX + "," + scaledY).getBytes();
                    Log.d(TAG, "Sending update: " + moveType + " " + scaledX + "," + scaledY);
                    DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, mReplyAddr, mReplyPort);
                    mSocket.send(replyPacket);
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}
