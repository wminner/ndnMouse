package edu.ucla.cs.ndnmouse.helpers;

import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.crypto.spec.IvParameterSpec;

/**
 * Helpers to format/process data sent and received by network
 */
public class NetworkHelpers {

    private final static int mAesBlockSize = 16;

    /**
     * Converts integer to 4 byte big endian (in order to send via network)
     *
     * @param x integer to convert
     * @return byte array (size 4) of the converted integer
     */
    @NonNull
    public static byte[] intToBytes(int x) {
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
    public static int intFromBytes(byte[] xbytes) {
        return ByteBuffer.wrap(xbytes).getInt();
    }

    public static byte[] prependIV(byte[] data, IvParameterSpec iv) {
        byte[] ivBytes = iv.getIV();
        byte[] res = new byte[data.length + ivBytes.length];
        System.arraycopy(ivBytes, 0, res, 0, ivBytes.length);
        System.arraycopy(data, 0, res, ivBytes.length, data.length);
        return res;
    }

    /**
     * PKCS5 padding extended to allow for greater than 16 byte pads
     *
     * @param data to be padded
     * @param maxPad the maximum number of bytes that can be padded
     * @return resulting padded data
     */
    public static byte[] PKCS5Pad(byte[] data, int maxPad) {
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
    public static byte[] PKCS5Unpad(byte[] data) {
        byte padChar = data[data.length-1];
        return Arrays.copyOf(data, data.length - padChar);
    }

    /**
     * Pad data with null bytes
     *
     * @param data to be padded
     * @param newLen of the resulting padded data
     * @return padded data
     */
    private byte[] padNullBytes(byte[] data, int newLen) {
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
    private byte[] trimNullBytes(byte[] data) {
        for (int i = data.length-1; i >= 0; i--) {
            if (data[i] != 0)
                return Arrays.copyOfRange(data, 0, i+1);
        }
        return new byte[0];
    }
}
