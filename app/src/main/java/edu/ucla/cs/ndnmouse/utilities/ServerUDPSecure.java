package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;

public class ServerUDPSecure extends ServerUDP {

    private static final String TAG = ServerUDPSecure.class.getSimpleName();

    private SecretKeySpec mKey;
    private Cipher mCipher;
    private SecureRandom mRandom;
    private final static int mIvBytes = 16;
    private final static int mSeqNumBytes = 4;

    private HashMap<InetAddress, WorkerThreadSecure> mClientThreads;    // Holds all active worker threads that are servicing clients

    /**
     * Constructor for server
     *
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     * @param sensitivity multiplier for scaling movement
     * @param key derived from user's password
     */
    public ServerUDPSecure(MouseActivity activity, int port, float sensitivity, SecretKeySpec key) {
        super(activity, port, sensitivity);

        mKey = key;
        mClientThreads = new HashMap<>();

        // Create CSRNG to produce IVs
        mRandom = new SecureRandom();

        // Get and init cipher algorithm
        try {
            mCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Create a new UDP socket
            mSocket = new DatagramSocket(mPort);
            while (mServerIsRunning) {
                byte[] buf = new byte[48];
                // Get incoming packet
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                mSocket.receive(packet);  // Blocks program flow

                // Get data from packet
                byte[] data = trimNullBytes(packet.getData());
                Log.d(TAG, "Incoming data: " + Arrays.toString(data));

                IvParameterSpec server_iv = new IvParameterSpec(Arrays.copyOfRange(data, 0, mIvBytes));
                byte[] encrypted = Arrays.copyOfRange(data, mIvBytes, data.length);

                try {
                    byte[] decrypted = decryptData(encrypted, server_iv);

                    int clientSeqNum = ByteBuffer.wrap(Arrays.copyOf(decrypted, mSeqNumBytes)).getInt();
                    String msg = new String(Arrays.copyOfRange(decrypted, mSeqNumBytes, decrypted.length));

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
                } catch (ShortBufferException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error during data decrypt!");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Web server was interrupted and is now closed.", e);
        }
    }

    /**
     * Send a click command to all current clients
     *
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

    private byte[] encryptData(byte[] message, IvParameterSpec iv) throws InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, BadPaddingException, IllegalBlockSizeException {
        Log.d(TAG, "Encrypt data BEFORE: " + new String(message));
        mCipher.init(Cipher.ENCRYPT_MODE, mKey, iv);
        byte[] encrypted = new byte[mCipher.getOutputSize(message.length)];
        int encryptLen = mCipher.update(message, 0, message.length, encrypted, 0);
        encryptLen += mCipher.doFinal(encrypted, encryptLen);
//        Log.d(TAG, "Encrypt data AFTER (length " + encryptLen + "): " + Arrays.toString(encrypted));
        return encrypted;
    }

    private byte[] decryptData(byte[] encrypted, IvParameterSpec iv) throws InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, BadPaddingException, IllegalBlockSizeException {
//        Log.d(TAG, "Decrypt data BEFORE: " + Arrays.toString(encrypted));
        mCipher.init(Cipher.DECRYPT_MODE, mKey, iv);
        byte[] decrypted = new byte[mCipher.getOutputSize(encrypted.length)];
        int decryptLen = mCipher.update(encrypted, 0, encrypted.length, decrypted, 0);
        decryptLen += mCipher.doFinal(decrypted, decryptLen);
        Log.d(TAG, "Decrypt data AFTER (length " + decryptLen + "): " + new String(decrypted));
        return decrypted;
    }

    private IvParameterSpec getNewIV() {
        byte[] newIv = new byte[mIvBytes];
        mRandom.nextBytes(newIv);
        return new IvParameterSpec(newIv);
    }

    @NonNull
    private byte[] intToBytes(int x) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(x);
        return buf.array();
    }

    private int intFromBytes(byte[] xbytes) {
        return ByteBuffer.wrap(xbytes).getInt();
    }

    private byte[] prependIV(byte[] data, IvParameterSpec iv) {
        byte[] ivBytes = iv.getIV();
        byte[] res = new byte[data.length + ivBytes.length];
        System.arraycopy(ivBytes, 0, res, 0, ivBytes.length);
        System.arraycopy(data, 0, res, ivBytes.length, data.length);
        return res;
    }

    private byte[] trimNullBytes(byte[] data) {
        for (int i = data.length-1; i >= 0; i--) {
            if (data[i] != 0)
                return Arrays.copyOfRange(data, 0, i+1);
        }
        return new byte[0];
    }

    /**
     * Server parent thread spins off worker threads to do the actual transmissions
     */
    private class WorkerThreadSecure extends WorkerThread {

        private int mSeqNum;

        /**
         * Constructor
         *
         * @param socket shared UDP socket from that parent is managing
         * @param packet initial packet that client uses to establish a connection with the server
         */
        WorkerThreadSecure(DatagramSocket socket, DatagramPacket packet) {
            super(socket, packet);
            mSeqNum = 0;
        }

        /**
         * Send acknowledgement to client that you received keep alive
         *
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

            // Add seq num, encrypt, and prepend IV
            byte[] reply = prependSeqNum(msg);
            IvParameterSpec iv = getNewIV();
            try {
                byte[] encryptedReply = encryptData(reply, iv);
                byte[] encryptedReplyWithIv = prependIV(encryptedReply, iv);
                DatagramPacket replyPacket = new DatagramPacket(encryptedReplyWithIv, encryptedReplyWithIv.length, mReplyAddr, mReplyPort);
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

                    // Build reply packet using seq num and message
                    byte[] msg = (moveType + " " + scaledX + "," + scaledY).getBytes();
                    byte[] reply = prependSeqNum(msg);

                    // Encrypt reply and prepend cleartext IV
                    IvParameterSpec iv = getNewIV();
                    try {
                        byte[] encryptedReply = encryptData(reply, iv);
                        byte[] encryptedReplyWithIv = prependIV(encryptedReply, iv);

                        // Send out socket
                        Log.d(TAG, "Sending update: " + Arrays.toString(encryptedReplyWithIv));
                        DatagramPacket replyPacket = new DatagramPacket(encryptedReplyWithIv, encryptedReplyWithIv.length, mReplyAddr, mReplyPort);
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

        void executeClick(int click) throws IOException {
            // Build reply packet using seq num and message
            byte[] msg = (mMouseActivity.getString(click)).getBytes();
            byte[] reply = prependSeqNum(msg);

            // Encrypt reply and prepend cleartext IV
            IvParameterSpec iv = getNewIV();

            try {
                byte[] encryptedReply = encryptData(reply, iv);
                byte[] encryptedReplyWithIv = prependIV(encryptedReply, iv);
                DatagramPacket replyPacket = new DatagramPacket(encryptedReplyWithIv, encryptedReplyWithIv.length, mReplyAddr, mReplyPort);
                mSocket.send(replyPacket);
            } catch (InvalidAlgorithmParameterException | InvalidKeyException | ShortBufferException | BadPaddingException | IllegalBlockSizeException e) {
                e.printStackTrace();
                Log.e(TAG, "Error encrypting mouse click!");
            }
        }

        private byte[] prependSeqNum(byte[] msg) {
            // Increment seq num and convert to bytes
            byte[] seqNum = intToBytes(++mSeqNum);
            byte[] reply = new byte[seqNum.length + msg.length];
            System.arraycopy(seqNum, 0, reply, 0, seqNum.length);
            System.arraycopy(msg, 0, reply, seqNum.length, msg.length);
            return reply;
        }

        int getSeqNum() {
            return mSeqNum;
        }

        void setSeqNum(int newSeqNum) {
            mSeqNum = newSeqNum;
        }
    }


}
