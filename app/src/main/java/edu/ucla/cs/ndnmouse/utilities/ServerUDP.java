package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;
import edu.ucla.cs.ndnmouse.helpers.MousePacket;
import edu.ucla.cs.ndnmouse.helpers.NetworkHelpers;

/**
 * Class to provide UDP communication with the PC client
 */
public class ServerUDP implements Runnable, Server {

    private static final String TAG = ServerUDP.class.getSimpleName();
    MouseActivity mMouseActivity;                   // Reference to calling activity

    DatagramSocket mSocket;                         // UDP socket used to send and receive
    final int mPort;                                // Port number (always 10888)
    boolean mServerIsRunning = false;               // Helps start and stop the server main thread
    float mSensitivity;                             // Sensitivity multiplier for relative movement
    boolean mScrollingInverted = false;             // Inverts the two-finger scroll direction if true

    private HashMap<InetAddress, WorkerThread> mClientThreads;    // Holds all active worker threads that are servicing clients

    /**
     * Constructor for server
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     * @param sensitivity multiplier for scaling movement
     */
    public ServerUDP(MouseActivity activity, int port, float sensitivity) {
        mMouseActivity = activity;
        mPort = port;
        mClientThreads = new HashMap<>();
        mSensitivity = sensitivity;
    }

    /**
     * Starts server by spinning it off as a background thread
     */
    public void start() {
        mServerIsRunning = true;
        Thread thread = new Thread(this);
        thread.start();
        Log.d(TAG, "Started UDP server... " + getIPAddress(true) + ":" + mPort);
    }

    /**
     * Stops server thread, cleans up all the worker threads, and closes the socket
     */
    public void stop() {
        mServerIsRunning = false;
        Log.d(TAG, "Stopped UDP server...");
    }

    @Override
    public void run() {
        try {
            // Create a new UDP socket
            mSocket = new DatagramSocket(mPort);
            while (mServerIsRunning) {
                byte[] buf = new byte[MousePacket.mPacketBytes];
                // Get incoming packet
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                mSocket.receive(packet);  // Blocks program flow

                // Get data from packet
                byte[] data = packet.getData();

                // Trim null bytes off end
                String msg = new String(data);
                try {
                    msg = msg.substring(0, msg.indexOf('\0'));
                } catch (StringIndexOutOfBoundsException e) {
                    Log.e(TAG, "Invalid message: Bad null byte padding!");
                    continue;
                }

                // If new client...
                if (msg.startsWith(mMouseActivity.getString(R.string.protocol_opening_request))) {
                    // If client is already being serviced, kill its worker and start a new one
                    if (mClientThreads.containsKey(packet.getAddress())) {
                        mClientThreads.get(packet.getAddress()).stop();
                        mClientThreads.remove(packet.getAddress());
                    }

                    // Start a new worker thread for the client
                    WorkerThread worker = new WorkerThread(mSocket, packet);
                    worker.start();
                    mClientThreads.put(packet.getAddress(), worker);
                    Log.d(TAG, "Number of clients: " + mClientThreads.size());
                }

                // Otherwise if existing client is requesting heartbeat...
                else if (msg.startsWith(mMouseActivity.getString(R.string.protocol_heartbeat_request))) {
                    if (mClientThreads.containsKey(packet.getAddress()))
                        mClientThreads.get(packet.getAddress()).sendAck(false);

                // Otherwise if existing client no longer wants updates...
                } else if (msg.startsWith(mMouseActivity.getString(R.string.protocol_closing_request))) {
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

        // Shutdown stuff
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
    }

    /**
     * Send a click command to all current clients
     * @param command identifier for the type of click
     * @throws IOException for socket IO error
     */
    public void executeCommand(int command) throws IOException {
        for (WorkerThread client : mClientThreads.values()) {
            if (null != client.mReplyAddr && 0 != client.mReplyPort) {
                byte[] reply = (mMouseActivity.getString(command)).getBytes();
                DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, client.mReplyAddr, client.mReplyPort);
                mSocket.send(replyPacket);
            }
        }
    }

    /**
     * Get IP address from first non-localhost interface
     * Based on code from:
     * http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
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
     * This is called whenever settings are updated, so the server can change its behavior on the fly
     *
     * @param key of the setting being updated
     * @param value of the updated setting (generic type)
     */
    public <T> void UpdateSettings(int key, T value) {
        switch (key) {
            case R.string.pref_sensitivity_key:
                mSensitivity = (Float) value;
                break;
            default:
                Log.e(TAG, "Error: setting to update not recognized!");
        }
        Log.d(TAG, "Updated " + mMouseActivity.getString(key) + " with new value " + value);
    }

    /**
     * Server parent thread spins off worker threads to do the actual transmissions
     */
    class WorkerThread implements Runnable {

        boolean mWorkerIsRunning = false;   // Helps start and stop this worker thread

        private final DatagramSocket mSocket;   // Shared UDP socket for all worker threads
        final InetAddress mReplyAddr;           // Client's address this will reply to
        final int mReplyPort;                   // Client's port this will reply to
        final static int mUpdateIntervalMillis = 50;    // Number of milliseconds to wait before sending next update. May require tuning.

        /**
         * Constructor
         * @param socket shared UDP socket from that parent is managing
         * @param packet initial packet that client uses to establish a connection with the server
         */
        WorkerThread(DatagramSocket socket, DatagramPacket packet) {
            mSocket = socket;
            // Get address and port to send reply to
            mReplyAddr = packet.getAddress();
            mReplyPort = packet.getPort();
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
         * Send acknowledgement to client that you received keep alive
         * @param openAck if this ack is replying to an OPEN message
         * @throws IOException for error during socket sending
         */
        void sendAck(boolean openAck) throws IOException {
            byte[] reply;
            if (openAck)
                reply = (mMouseActivity.getString(R.string.protocol_open_ack)).getBytes();
            else
                reply = (mMouseActivity.getString(R.string.protocol_heartbeat_ack)).getBytes();
            DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, mReplyAddr, mReplyPort);
            Log.d(TAG, "Sending ACK: " + new String(reply));
            mSocket.send(replyPacket);
        }

        @Override
        public void run() {
            try {
                sendAck(true);
                while (mWorkerIsRunning) {
                    // Don't send too many updates (may require tuning)
                    Thread.sleep(mUpdateIntervalMillis);
                    // String moveType = mMouseActivity.getString(R.string.protocol_move_relative);
                    String moveType = mMouseActivity.getMoveType();
                    Point position = mMouseActivity.getRelativePosition();

                    // Skip update if no relative movement since last update
                    if (position.equals(0, 0))
                        continue;

                    // Find scaled x and y position according to sensitivity
                    int scaledX = (int) (position.x * mSensitivity);
                    int scaledY = (int) (position.y * mSensitivity);

                    // Build reply message and send out socket
                    byte[] reply;
                    if (moveType.equals(mMouseActivity.getString(R.string.protocol_move_relative)))
                        reply = NetworkHelpers.buildMoveMessage(moveType, scaledX, scaledY);
                    else {
                        if (!mScrollingInverted) {
                            scaledX = -scaledX;
                            scaledY = -scaledY;
                        }
                        reply = NetworkHelpers.buildMoveMessage(moveType, scaledX, scaledY);
                    }
                    Log.d(TAG, "Sending update: " + new String(reply) + ", x = " + scaledX + ", y = " + scaledY);

                    // Build and send datagram packet
                    DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, mReplyAddr, mReplyPort);
                    mSocket.send(replyPacket);
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}
