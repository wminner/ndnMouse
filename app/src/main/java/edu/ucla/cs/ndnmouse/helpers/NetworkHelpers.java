package edu.ucla.cs.ndnmouse.helpers;

import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helpers to format/process data sent and received by network
 */
class NetworkHelpers {

    private static final String TAG = NetworkHelpers.class.getSimpleName();

    private static final int mAesBlockSize = 16;
    private static final int mIvBytes = mAesBlockSize;
    private static SecureRandom mRandom;

    /**
     * Converts integer to 4 byte big endian (in order to send via network)
     *
     * @param x integer to convert
     * @return byte array (size 4) of the converted integer
     */
    @NonNull
    static byte[] intToBytes(int x) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(x);
        return buf.array();
    }

    /**
     * Converts big endian byte array (assumed to be size 4) to integer.
     *
     * @param xbytes array of bytes to convert (must be size 4)
     * @return converted integer
     */
    static int intFromBytes(byte[] xbytes) {
        return ByteBuffer.wrap(xbytes).getInt();
    }

    /**
     * PKCS5 padding extended to allow for greater than 16 byte pads
     *
     * @param data to be padded
     * @param maxPad the maximum number of bytes that can be padded
     * @return resulting padded data
     */
    private static byte[] PKCS5Pad(byte[] data, int maxPad) {
        byte padChar = (byte) (maxPad - data.length % maxPad);
        int newLen = data.length + padChar;
        byte[] newData = Arrays.copyOf(data, newLen);
        for (int i = data.length; i < newLen; i++) {
            newData[i] = padChar;
        }
        return newData;
    }

    /**
     * Convenience overload
     */
    private byte[] PKCS5Pad(byte[] data) {
        return PKCS5Pad(data, mAesBlockSize);
    }

    /**
     * PKCS5 standard unpadder
     *
     * @param data to be unpadded
     * @return resulting unpadded data
     */
    @NonNull
    private static byte[] PKCS5Unpad(byte[] data) throws NegativeArraySizeException {
        byte padChar = data[data.length-1];
        if (data.length - padChar <= 0)
            throw new NegativeArraySizeException();
        return Arrays.copyOf(data, data.length - padChar);
    }

    /**
     * Pad data with null bytes
     *
     * @param data to be padded
     * @param newLen of the resulting padded data
     * @return padded data
     */
    public static byte[] padNullBytes(byte[] data, int newLen) {
        if (data.length >= newLen)
            return data;
        byte[] newData = Arrays.copyOf(data, newLen);
        for (int i = data.length; i < newLen; i++) {
            newData[i] = 0;
        }
        return newData;
    }

    /**
     * Trim null bytes from data
     *
     * @param data to be trimmed
     * @return resulting trimmed data
     */
    public static byte[] trimNullBytes(byte[] data) {
        for (int i = data.length-1; i >= 0; i--) {
            if (data[i] != 0)
                return Arrays.copyOfRange(data, 0, i+1);
        }
        return new byte[0];
    }

    /**
     * Encrypts data using user key and specified IV
     *
     * @param message to encrypt
     * @param iv (initialization vector) to use with CBC
     * @return bytes of encrypted message
     * @throws InvalidAlgorithmParameterException during encryption
     * @throws InvalidKeyException during encryption
     * @throws ShortBufferException during encryption
     * @throws BadPaddingException during encryption
     * @throws IllegalBlockSizeException during encryption
     */
    static byte[] encryptData(byte[] message, Cipher cipher, SecretKeySpec key, IvParameterSpec iv) throws InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, BadPaddingException, IllegalBlockSizeException {
        Log.d(TAG, "Encrypt data BEFORE: " + new String(message));
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        // Log.d(TAG, "Encrypt data AFTER (length " + encryptLen + "): " + Arrays.toString(encrypted));
        return cipher.doFinal(NetworkHelpers.PKCS5Pad(message, MousePacket.mPacketBytes - mIvBytes));
    }

    /**
     * Decrypts data using user key and specified IV
     *
     * @param encrypted bytes to decrypt
     * @param iv (initialization vector) to use with CBC
     * @return bytes of decrypted message
     * @throws InvalidAlgorithmParameterException during encryption
     * @throws InvalidKeyException during encryption
     * @throws ShortBufferException during encryption
     * @throws BadPaddingException during encryption
     * @throws IllegalBlockSizeException during encryption
     */
    static byte[] decryptData(byte[] encrypted, Cipher cipher, SecretKeySpec key, IvParameterSpec iv) throws InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException, BadPaddingException, IllegalBlockSizeException, NegativeArraySizeException {
        // Log.d(TAG, "Decrypt data BEFORE: " + Arrays.toString(encrypted));
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        // Log.d(TAG, "Decrypt data AFTER (length " + decryptLen + "): " + new String(decrypted));
        return NetworkHelpers.PKCS5Unpad(cipher.doFinal(encrypted));
    }

    /**
     * Gets a new random IV
     *
     * @return random IV
     */
    static IvParameterSpec getNewIV() {
        if (null == mRandom)
            mRandom = new SecureRandom();
        byte[] newIv = new byte[mIvBytes];
        mRandom.nextBytes(newIv);
        return new IvParameterSpec(newIv);
    }
}
