package edu.ucla.cs.ndnmouse.helpers;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mouse command packet to help assemble and process packets. Only used for secure connections.
 * Secure packet description:
 *                     1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2
 * -----------------------------------------------------------------
 * |              IV               |  Seq  |  Message (PKCS5 pad)  |
 * -----------------------------------------------------------------
 * <~~~~~~~~~ plaintext ~~~~~~~~~~~><~~~~~~~~~ ciphertext ~~~~~~~~~>
 *
*/
public class MousePacket {

    private static final String TAG = MousePacket.class.getSimpleName();

    private SecretKeySpec mKey;
    private static Cipher mCipher;

    public final static int mPacketBytes = 32;
    private final static int mIvBytes = 16;
    private final static int mSeqNumBytes = 4;
    private byte[] mPayload;
    private byte[] mEncryptedPayload;
    private IvParameterSpec mIv;

    /**
     * Constructor for incoming mouse packet that is encrypted
     * @param encryptedPacket received from network
     * @param key to decrypt packet with
     * @throws IllegalBlockSizeException for encryption
     * @throws BadPaddingException for encryption
     * @throws InvalidAlgorithmParameterException for encryption
     * @throws ShortBufferException for encryption
     * @throws InvalidKeyException for encryption
     */
    public MousePacket(byte[] encryptedPacket, SecretKeySpec key) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException, InvalidKeyException, NegativeArraySizeException {
        this(key);
        // Decrypt and break down the packet
        mIv = getEncryptedPacketIV(encryptedPacket);
        mEncryptedPayload = Arrays.copyOfRange(encryptedPacket, mIvBytes, mPacketBytes);
        mPayload = NetworkHelpers.decryptData(mEncryptedPayload, mCipher, mKey, mIv);
    }

    public MousePacket(byte[] encryptedPacket, SecretKeySpec key, int packetBytes) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException, InvalidKeyException, NegativeArraySizeException {
        this(key);
        // Decrypt and break down the packet
        mIv = getEncryptedPacketIV(encryptedPacket);
        mEncryptedPayload = Arrays.copyOfRange(encryptedPacket, mIvBytes, packetBytes);
        mPayload = NetworkHelpers.decryptData(mEncryptedPayload, mCipher, mKey, mIv);
    }

    /**
     * Constructor for outgoing mouse packet that will be encrypted
     * @param message bytes that contain a mouse command
     * @param seqNum for the particular server/client session
     * @param key to encrypt packet with
     * @throws IllegalBlockSizeException for encryption
     * @throws BadPaddingException for encryption
     * @throws InvalidAlgorithmParameterException for encryption
     * @throws ShortBufferException for encryption
     * @throws InvalidKeyException for encryption
     */
    public MousePacket(byte[] message, int seqNum, SecretKeySpec key) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException, InvalidKeyException {
        this(key);
        // Encrypt and assemble the packet
        mPayload = prependSeqNum(message, seqNum);
        mIv = NetworkHelpers.getNewIV();
        mEncryptedPayload = NetworkHelpers.encryptData(mPayload, mCipher, mKey, mIv);
    }

    /**
     * Common constructor that will always be called (not user accessible)
     * @param key to encrypt messages
     */
    private MousePacket(SecretKeySpec key) {
        mKey = key;
        // Get and init cipher algorithm
        try {
            // Padding is handled by my own custom PKCS5 padding function (see NetworkerHelpers.PKCS5Pad)
            if (null == mCipher)
                mCipher = Cipher.getInstance("AES/CBC/NoPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the sequence number from the decrypted payload
     * @return int seq num
     */
    public int getSeqNum() {
        return NetworkHelpers.intFromBytes(Arrays.copyOf(mPayload, mSeqNumBytes));
    }

    /**
     * Gets mouse command message
     * @return String message
     */
    public String getMessage() {
        return new String(Arrays.copyOfRange(mPayload, mSeqNumBytes, mPayload.length));
    }

    /**
     * Gets encrypted packet, ready to send out on network
     * @return bytes of encrypted packet
     */
    public byte[] getEncryptedPacket() {
        return prependIV(mEncryptedPayload, mIv);
    }

    /**
     * Gets the IV of the encrypted packet (sometimes used as a password salt)
     * @param encryptedPacket bytes of the full encrypted packet
     * @return iv from the packet
     */
    public static IvParameterSpec getEncryptedPacketIV(byte[] encryptedPacket) {
        return new IvParameterSpec(Arrays.copyOf(encryptedPacket, mIvBytes));
    }

    /**
     * Prepend IV to the data bytes
     * @param data to prepend iv to
     * @param iv to prepend
     * @return resulting bytes with IV prepended
     */
    private static byte[] prependIV(byte[] data, IvParameterSpec iv) {
        byte[] ivBytes = iv.getIV();
        byte[] res = new byte[data.length + ivBytes.length];
        System.arraycopy(ivBytes, 0, res, 0, ivBytes.length);
        System.arraycopy(data, 0, res, ivBytes.length, data.length);
        return res;
    }

    /**
     * Prepend seq num to message bytes
     * @param msg to prepend the seq num to
     * @param seqNum to prepend
     * @return resulting bytes with big endian seq num prepended
     */
    private static byte[] prependSeqNum(byte[] msg, int seqNum) {
        // Convert seqNum to bytes and prepend it to msg
        byte[] seqNumBytes = NetworkHelpers.intToBytes(seqNum);
        byte[] reply = new byte[seqNumBytes.length + msg.length];
        System.arraycopy(seqNumBytes, 0, reply, 0, seqNumBytes.length);
        System.arraycopy(msg, 0, reply, seqNumBytes.length, msg.length);
        return reply;
    }
}
