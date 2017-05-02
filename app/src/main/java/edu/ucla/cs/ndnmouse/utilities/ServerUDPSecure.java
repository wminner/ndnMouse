package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;
import edu.ucla.cs.ndnmouse.helpers.MousePacket;

public class ServerUDPSecure extends ServerUDP {

    private static final String TAG = ServerUDPSecure.class.getSimpleName();

    private SecretKeySpec mKey;     // Hashed user password to be used for encryption
    private HashMap<InetAddress, WorkerThreadSecure> mClientThreads;    // Holds all active worker threads that are servicing clients

    /**
     * Constructor for server
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     * @param sensitivity multiplier for scaling movement
     * @param key derived from user's password
     */
    public ServerUDPSecure(MouseActivity activity, int port, float sensitivity, SecretKeySpec key) {
        super(activity, port, sensitivity);

        mKey = key;
        mClientThreads = new HashMap<>();
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
                // Log.d(TAG, "Incoming data: " + Arrays.toString(data));
                try {
                    MousePacket mousePacket = new MousePacket(data, mKey);

                    // Use mouse packet to decrypt the message and get the seq num
                    String msg = mousePacket.getMessage();
                    int clientSeqNum = mousePacket.getSeqNum();

                    // If new client...
                    if (msg.startsWith(mMouseActivity.getString(R.string.protocol_opening_request))) {
                        // If seq num not correct, throw out packet and loop
                        if (0 != clientSeqNum)
                            continue;

                        // If client is already being serviced, kill its worker and start a new one
                        if (mClientThreads.containsKey(packet.getAddress())) {
                            mClientThreads.get(packet.getAddress()).stop();
                            mClientThreads.remove(packet.getAddress());
                        }

                        // Start a new worker thread for the client
                        WorkerThreadSecure worker = new WorkerThreadSecure(mSocket, packet);
                        worker.start();
                        mClientThreads.put(packet.getAddress(), worker);
                        Log.d(TAG, "Number of clients: " + mClientThreads.size());
                    }

                    // Otherwise if existing client is requesting heartbeat...
                    else if (msg.startsWith(mMouseActivity.getString(R.string.protocol_heartbeat_request))) {
                        if (mClientThreads.containsKey(packet.getAddress())) {
                            WorkerThreadSecure worker = mClientThreads.get(packet.getAddress());
                            // Only acknowledge if seq num is valid
                            if (clientSeqNum > worker.getSeqNum()) {
                                worker.setSeqNum(clientSeqNum);
                                worker.sendAck(false);
                            }
                        }

                    // Otherwise if existing client no longer wants updates...
                    } else if (msg.startsWith(mMouseActivity.getString(R.string.protocol_closing_request))) {
                        // Look up its thread and stop it
                        if (mClientThreads.containsKey(packet.getAddress())) {
                            WorkerThreadSecure client = mClientThreads.get(packet.getAddress());
                            // Only stop worker thread if seq num is valid
                            if (clientSeqNum > client.getSeqNum()) {
                                client.stop();
                                mClientThreads.remove(packet.getAddress());
                            }
                        }
                    }
                } catch (ShortBufferException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | NegativeArraySizeException e) {
                    Log.e(TAG, "Error during data decrypt!");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Web server was interrupted and is now closed.", e);
        }
    }

    /**
     * Send a click command to all current clients
     * @param click identifier for the type of click
     * @throws IOException for socket IO error
     */
    @Override
    public void executeClick(int click) throws IOException {
        for (WorkerThreadSecure client : mClientThreads.values()) {
            if (null != client.mReplyAddr && 0 != client.mReplyPort)
                client.executeClick(click);
        }
    }

    /**
     * Server parent thread spins off worker threads to do the actual transmissions
     */
    private class WorkerThreadSecure extends WorkerThread {

        private int mSeqNum;

        /**
         * Constructor
         * @param socket shared UDP socket from that parent is managing
         * @param packet initial packet that client uses to establish a connection with the server
         */
        WorkerThreadSecure(DatagramSocket socket, DatagramPacket packet) {
            super(socket, packet);
            mSeqNum = 0;
        }

        /**
         * Send acknowledgement to client that you received keep alive
         * @param openAck if this ack is replying to an OPEN message
         * @throws IOException for error during socket sending
         */
        @Override
        void sendAck(boolean openAck) throws IOException {
            byte[] msg;
            if (openAck)
                msg = (mMouseActivity.getString(R.string.protocol_open_ack)).getBytes();
            else
                msg = (mMouseActivity.getString(R.string.protocol_heartbeat_ack)).getBytes();

            try {
                // Create mouse packet from message, and send out encrypted reply
                MousePacket mousePacket = new MousePacket(msg, ++mSeqNum, mKey);
                byte[] encryptedReply = mousePacket.getEncryptedPacket();
                DatagramPacket replyPacket = new DatagramPacket(encryptedReply, encryptedReply.length, mReplyAddr, mReplyPort);
                mSocket.send(replyPacket);
            } catch (InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException | ShortBufferException | IllegalBlockSizeException e) {
                e.printStackTrace();
                Log.e(TAG, "Error during data encryption!");
            }
        }

        @Override
        public void run() {
            try {
                sendAck(true);
                while (mWorkerIsRunning) {
                    // Don't send too many updates (may require tuning)
                    Thread.sleep(mUpdateIntervalMillis);
                    Point position = mMouseActivity.getRelativePosition();
                    String moveType = mMouseActivity.getString(R.string.protocol_move_relative);

                    // Skip update if no relative movement since last update
                    if (position.equals(0, 0))
                        continue;

                    // Find scaled x and y position according to sensitivity
                    int scaledX = (int) (position.x * mSensitivity);
                    int scaledY = (int) (position.y * mSensitivity);

                    // Build reply message, create mouse packet from it, and send out encrypted reply
                    byte[] msg = (moveType + " " + scaledX + "," + scaledY).getBytes();
                    try {
                        MousePacket mousePacket = new MousePacket(msg, ++mSeqNum, mKey);
                        byte[] encryptedReply = mousePacket.getEncryptedPacket();
                        Log.d(TAG, "Sending update: " + Arrays.toString(encryptedReply));
                        DatagramPacket replyPacket = new DatagramPacket(encryptedReply, encryptedReply.length, mReplyAddr, mReplyPort);
                        mSocket.send(replyPacket);

                    } catch (ShortBufferException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Error during data encryption!");
                    }
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error during socket send!");
            }
        }

        /**
         * Executes click for this specific server/client session
         * @param click type
         * @throws IOException when sending through socket
         */
        void executeClick(int click) throws IOException {
            // Build reply message, create mouse packet from it, and send out encrypted reply
            byte[] msg = (mMouseActivity.getString(click)).getBytes();
            try {
                MousePacket mousePacket = new MousePacket(msg, ++mSeqNum, mKey);
                byte[] encryptedReply = mousePacket.getEncryptedPacket();
                DatagramPacket replyPacket = new DatagramPacket(encryptedReply, encryptedReply.length, mReplyAddr, mReplyPort);
                mSocket.send(replyPacket);
            } catch (InvalidAlgorithmParameterException | InvalidKeyException | ShortBufferException | BadPaddingException | IllegalBlockSizeException e) {
                e.printStackTrace();
                Log.e(TAG, "Error encrypting mouse click!");
            }
        }

        /**
         * Sequence number helpers
         * @return sequence number for this specific server/client session
         */
        int getSeqNum() {
            return mSeqNum;
        }

        /**
         * Set the sequence number for this specific server/client session
         * @param newSeqNum to set mSeqNum to
         */
        void setSeqNum(int newSeqNum) {
            mSeqNum = newSeqNum;
        }
    }
}
