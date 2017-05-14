package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;
import edu.ucla.cs.ndnmouse.helpers.MousePacket;
import edu.ucla.cs.ndnmouse.helpers.NetworkHelpers;

public class ServerUDPSecure extends ServerUDP {

    private static final String TAG = ServerUDPSecure.class.getSimpleName();

    private String mPassword;
    private SecretKeySpec mOpenKey; // Hashed user password to be used for encryption on the opening message only
    private HashMap<InetAddress, WorkerThreadSecure> mClientThreads;    // Holds all active worker threads that are servicing clients
    private static final int mWorkerDropCounterTheshold = 3;
    private static final int mMaxSeqNum = Integer.MAX_VALUE;

    /**
     * Constructor for server
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     * @param moveSensitivity multiplier for scaling movement
     * @param password from user
     */
    public ServerUDPSecure(MouseActivity activity, int port, float moveSensitivity, boolean scrollInverted, float scrollSensitivity, String password) {
        super(activity, port, moveSensitivity, scrollInverted, scrollSensitivity);

        mPassword = password;
        try {
            mOpenKey = mMouseActivity.makeKeyFromPassword(password);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            Log.e(TAG, "Error: failed to create KeySpec! Aborting...");
            mMouseActivity.finish();
        }
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
                    // If existing client sent us a message...
                    if (mClientThreads.containsKey(packet.getAddress())) {
                        WorkerThreadSecure worker = mClientThreads.get(packet.getAddress());
                        MousePacket mousePacket = new MousePacket(data, worker.getKey());

                        // Use mouse packet to decrypt the message and get the seq num
                        String msg = mousePacket.getMessage();
                        int clientSeqNum = mousePacket.getSeqNum();

                        // If existing client is requesting heartbeat...
                        if (msg.startsWith(mMouseActivity.getString(R.string.protocol_heartbeat_request))) {
                            if (mClientThreads.containsKey(packet.getAddress())) {
                                // Only acknowledge if seq num is valid
                                if (clientSeqNum > worker.getSeqNum()) {
                                    worker.setSeqNum(clientSeqNum);
                                    worker.sendAck(false);
                                }
                            }
                        // If existing client no longer wants updates...
                        } else if (msg.startsWith(mMouseActivity.getString(R.string.protocol_closing_request))) {
                            // Look up its thread and stop it
                            if (mClientThreads.containsKey(packet.getAddress())) {
                                // Only stop worker thread if seq num is valid
                                if (clientSeqNum > worker.getSeqNum()) {
                                    worker.stop();
                                    mClientThreads.remove(packet.getAddress());
                                }
                            }
                        // Otherwise existing client sent bad message, increment their drop counter
                        } else {
                            // If client sent too many bad messages, drop its session
                            if (++worker.mDropCounter >= mWorkerDropCounterTheshold) {
                                worker.stop();
                                mClientThreads.remove(packet.getAddress());
                            }
                        }

                    // Otherwise must be a new client...
                    } else {
                        MousePacket mousePacket = new MousePacket(data, mOpenKey);

                        // Use mouse packet to decrypt the message and get the seq num
                        String msg = mousePacket.getMessage();
                        int clientSeqNum = mousePacket.getSeqNum();

                        if (msg.startsWith(mMouseActivity.getString(R.string.protocol_opening_request))) {
                            // If seq num not correct, throw out packet and loop
                            if (0 != clientSeqNum)
                                continue;

                            // Start a new worker thread for the client
                            WorkerThreadSecure worker = new WorkerThreadSecure(mSocket, packet);
                            worker.start();
                            mClientThreads.put(packet.getAddress(), worker);
                            Log.d(TAG, "Number of clients: " + mClientThreads.size());
                        }
                    }
                } catch (ShortBufferException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | NegativeArraySizeException | NoSuchAlgorithmException e) {
                    // Existing client sent bad message, increment their drop counter
                    if (mClientThreads.containsKey(packet.getAddress())) {
                        WorkerThreadSecure worker = mClientThreads.get(packet.getAddress());
                        // If client sent too many bad messages, drop its session
                        if (++worker.mDropCounter >= mWorkerDropCounterTheshold) {
                            worker.stop();
                            mClientThreads.remove(packet.getAddress());
                        }
                    }
                    Log.e(TAG, "Error during data decrypt!");
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
    @Override
    public void executeCommand(int command) throws IOException {
        for (WorkerThreadSecure client : mClientThreads.values()) {
            if (null != client.mReplyAddr && 0 != client.mReplyPort)
                client.executeCommand(command);
        }
    }

    /**
     * Server parent thread spins off worker threads to do the actual transmissions
     */
    private class WorkerThreadSecure extends WorkerThread {

        private int mSeqNum;
        private SecretKeySpec mKey;     // Hashed and salted user password to be used for encryption on everything else
        private int mDropCounter;

        /**
         * Constructor
         * @param socket shared UDP socket from that parent is managing
         * @param packet initial packet that client uses to establish a connection with the server
         */
        WorkerThreadSecure(DatagramSocket socket, DatagramPacket packet) throws UnsupportedEncodingException, NoSuchAlgorithmException {
            super(socket, packet);
            mSeqNum = 0;
            mDropCounter = 0;

            // Generate the salted password key from the opening IV (to be used for the rest of the session)
            IvParameterSpec passwordSalt = MousePacket.getEncryptedPacketIV(packet.getData());
            mKey = mMouseActivity.makeKeyFromPassword(mPassword, passwordSalt.getIV());
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
                MousePacket mousePacket = new MousePacket(msg, getNextSeqNum(), mKey);
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
                    // Skip update if no relative movement since last update
                    if (position.equals(0, 0))
                        continue;

                    String moveType = mMouseActivity.getMoveType();
                    boolean scrollActivated = moveType.equals(mMouseActivity.getString(R.string.protocol_move_scrolling));

                    // Find scaled x and y position according to appropriate sensitivity
                    int scaledX, scaledY;
                    if (scrollActivated) {
                        scaledX = (int) (position.x * mScrollSensitivity);
                        scaledY = (int) (position.y * mScrollSensitivity);
                        if (!mScrollInverted) {
                            scaledX = -scaledX;
                            scaledY = -scaledY;
                        }
                    } else {
                        scaledX = (int) (position.x * mMoveSensitivity);
                        scaledY = (int) (position.y * mMoveSensitivity);
                    }

                    // Build move message, create mouse packet from it, and send out encrypted reply
                    byte[] msg = NetworkHelpers.buildMoveMessage(moveType, scaledX, scaledY);
                    try {
                        MousePacket mousePacket = new MousePacket(msg, getNextSeqNum(), mKey);
                        byte[] encryptedMsg = mousePacket.getEncryptedPacket();
                        Log.d(TAG, "Sending update: " + Arrays.toString(encryptedMsg));
                        DatagramPacket packet = new DatagramPacket(encryptedMsg, encryptedMsg.length, mReplyAddr, mReplyPort);
                        mSocket.send(packet);

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
        void executeCommand(int click) throws IOException {
            // Build reply message, create mouse packet from it, and send out encrypted reply
            byte[] msg = (mMouseActivity.getString(click)).getBytes();
            try {
                MousePacket mousePacket = new MousePacket(msg, getNextSeqNum(), mKey);
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

        /**
         * Get the next unused seq number. Handle if it overflows.
         * @return next unused seq number for server
         */
        private int getNextSeqNum() {
            if (mSeqNum == mMaxSeqNum) {
                mSeqNum = 0;
            } else {
                mSeqNum++;
            }
            return mSeqNum;
        }

        /**
         * Get the worker's general encryption key
         * @return key of worker
         */
        SecretKeySpec getKey() {
            return mKey;
        }
    }
}
