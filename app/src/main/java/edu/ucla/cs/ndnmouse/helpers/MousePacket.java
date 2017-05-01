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
 * Packet description
 *                     1                   2                   3                   4
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8
 * -------------------------------------------------------------------------------------------------
 * |              IV               |  Seq  |         Message (padding via an extended PKCS5)       |
 * -------------------------------------------------------------------------------------------------
 * <~~~~~~~~~ plaintext ~~~~~~~~~~~><~~~~~~~~~~~~~~~~~~ ciphertext (payload) ~~~~~~~~~~~~~~~~~~~~~~>
 *
*/

public class MousePacket {

    private static final String TAG = MousePacket.class.getSimpleName();

    private SecretKeySpec mKey;
    private Cipher mCipher;

    private final static int mPacketBytes = 48;
    private final static int mIvBytes = 16;
    private final static int mSeqNumBytes = 4;
    private byte[] mPayload;
    private byte[] mEncryptedPayload;
    private IvParameterSpec mIv;

    /**
     * Constructor for incoming mouse packet that is encrypted
     *
     * @param encryptedPacket
     * @param key
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     * @throws ShortBufferException
     * @throws InvalidKeyException
     */
    public MousePacket(byte[] encryptedPacket, SecretKeySpec key) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException, InvalidKeyException {
        this(key);
        // Decrypt and break down the packet
        mIv = new IvParameterSpec(getEncryptedPacketIV(encryptedPacket));
        mEncryptedPayload = Arrays.copyOfRange(encryptedPacket, mIvBytes, mPacketBytes);
        mPayload = NetworkHelpers.decryptData(mEncryptedPayload, mCipher, mKey, mIv);
    }

    /**
     * Constructor for outgoing mouse packet that will be encrypted
     *
     * @param message
     * @param seqNum
     * @param key
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws InvalidAlgorithmParameterException
     * @throws ShortBufferException
     * @throws InvalidKeyException
     */
    public MousePacket(byte[] message, int seqNum, SecretKeySpec key) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, ShortBufferException, InvalidKeyException {
        this(key);
        // Encrypt and assemble the packet
        mPayload = NetworkHelpers.prependSeqNum(message, seqNum);
        mIv = NetworkHelpers.getNewIV();
        mEncryptedPayload = NetworkHelpers.encryptData(mPayload, mCipher, mKey, mIv);
    }

    /**
     * Common constructor that will always be called (not user accessible)
     *
     * @param key
     */
    private MousePacket(SecretKeySpec key) {
        mKey = key;
        // Get and init cipher algorithm
        try {
            // Padding is handled by my own custom PKCS5 padding function (see NetworkerHelpers.PKCS5Pad)
            mCipher = Cipher.getInstance("AES/CBC/NoPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    public int getSeqNum() {
        return NetworkHelpers.intFromBytes(Arrays.copyOf(mPayload, mSeqNumBytes));
    }

    public byte[] getMessage() {
        return Arrays.copyOfRange(mPayload, mSeqNumBytes, mPayload.length);
    }

    public byte[] getEncryptedPacket() {
        return NetworkHelpers.prependIV(mEncryptedPayload, mIv);
    }

    private static byte[] getEncryptedPacketIV(byte[] encrypted) {
        return Arrays.copyOf(encrypted, mIvBytes);
    }
}
