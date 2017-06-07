package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.util.Log;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;
import edu.ucla.cs.ndnmouse.helpers.MousePacket;
import edu.ucla.cs.ndnmouse.helpers.NetworkHelpers;

public class ServerNDNSecure extends ServerNDN {

    private static final String TAG = ServerNDNSecure.class.getSimpleName();

    // private String mPassword;
    private byte[] mSalt;
    private SecretKeySpec mKey;
    private int mSeqNum;
    private static final int mMaxSeqNum = Integer.MAX_VALUE;

    /**
     * Constructor for server
     * @param activity of the caller (so we can get position points)
     * @param moveSensitivity multiplier for scaling movement
     * @param password from user
     */
    public ServerNDNSecure(MouseActivity activity, float moveSensitivity, boolean scrollInverted, float scrollSensitivity, String password) {
        super(activity, moveSensitivity, scrollInverted, scrollSensitivity);

        // mPassword = password;
        mSalt = NetworkHelpers.getNewIV().getIV();
        try {
            mKey = mMouseActivity.makeKeyFromPassword(password, mSalt);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            Log.e(TAG, "Error: failed to create KeySpec! Aborting...");
            mMouseActivity.finish();
        }
        mSeqNum = 0;
    }

    /**
     * Setup prefixes that this server will respond to
     *
     * @throws IOException for putting data at Face
     * @throws SecurityException for Face registration
     */
    @Override
    void registerPrefixes() throws IOException, SecurityException {
        // Prefix for all updates
        Name prefix_move = new Name(mMouseActivity.getString(R.string.ndn_prefix_mouse_update));
        long prefixId = mFace.registerPrefix(prefix_move,
                new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        // Log.d(TAG, "Got interest: " + interest.getName());
                        Data replyData = new Data(interest.getName());
                        replyData.getMetaInfo().setFreshnessPeriod(mFreshnessPeriod);

                        // Check if any pending commands in command queue first
                        byte[] msg = null;
                        synchronized (mCommandQueue) {
                            // If no click has occurred, return let the interest timeout
                            if (!mCommandQueue.isEmpty())
                                // Build reply string and set data contents
                                msg = (mCommandQueue.remove()).getBytes();
                        }

                        // If there was no pending command, then send the latest mouse movement (if any)
                        if (null == msg) {
                            Point position = mMouseActivity.getRelativePosition();
                            // Skip update if no relative movement since last update
                            if (position.equals(0, 0))
                                return;

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

                            // Build reply message and set data contents
                            msg = NetworkHelpers.buildMoveMessage(moveType, scaledX, scaledY);
                            // Log.d(TAG, "Sending update: " + msg);
                        }

                        try {
                            // Encrypt reply
                            MousePacket mousePacket = new MousePacket(msg, getNextSeqNum(), mKey);
                            byte[] encryptedReply = mousePacket.getEncryptedPacket();

                            // Set content of data
                            replyData.setContent(new Blob(encryptedReply));

                            // Send data out face
                            face.putData(replyData);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Failed to put data.");
                        } catch (ShortBufferException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
                            Log.e(TAG, "Error during data encryption!");
                        }
                    }
                },
                new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name name) {
                        mRegisteredPrefixIds.remove(mMouseActivity.getString(R.string.ndn_prefix_mouse_update));
                        mPrefixRegisterError = true;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
        mRegisteredPrefixIds.put(mMouseActivity.getString(R.string.ndn_prefix_mouse_update), prefixId);

        // Prefix for seq num updates (special interest for cases of desync only)
        Name prefix_update_seq = new Name(mMouseActivity.getString(R.string.ndn_prefix_update_seq));
        prefixId = mFace.registerPrefix(prefix_update_seq,
                new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        Log.d(TAG, "Got interest: " + interest.getName());

                        // Interest name format: /ndnmouse/seq/<iv><seq-num>SEQ
                        //                      |----cleartext----|-ciphertext-|
                        // iv (16 B) + encryptedMsg (16 B) = 32 B
                        try {
                            // Get final component (the message), put into byte array
                            ByteBuffer interestDataByteBuf = interest.getName().get(-1).getValue().buf();
                            byte[] interestData = new byte[interestDataByteBuf.remaining()];
                            interestDataByteBuf.get(interestData);

                            // Use MousePacket to decrypt message
                            MousePacket interestMousePacket = new MousePacket(interestData, mKey);
                            int syncSeqNum = interestMousePacket.getSeqNum();
                            String interestMsg = interestMousePacket.getMessage();

                            // Verify decrypted message is as expected, otherwise return
                            if (!interestMsg.startsWith(mMouseActivity.getString(R.string.protocol_update_seq_request))) {
                                Log.e(TAG, "Invalid seq num update command!");
                                return;
                            }

                            // If requested seq num larger than current seq num, then update server's seq num
                            mSeqNum = Math.max(syncSeqNum, mSeqNum);
                            Log.d(TAG, "Setting server seq num to " + mSeqNum);

                            Data replyData = new Data(interest.getName());
                            replyData.getMetaInfo().setFreshnessPeriod(mFreshnessPeriod);

                            // Build reply string and set data contents
                            byte[] msg = (mMouseActivity.getString(R.string.protocol_update_seq_reply)).getBytes();
                            // Log.d(TAG, "Sending update seq reply: " + replyString);

                            // Encrypt reply
                            MousePacket mousePacket = new MousePacket(msg, getNextSeqNum(), mKey);
                            byte[] encryptedReply = mousePacket.getEncryptedPacket();

                            // Set content of data
                            replyData.setContent(new Blob(encryptedReply));

                            // Send data out face
                            face.putData(replyData);

                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Failed to put data.");
                        } catch (ShortBufferException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Error during data encryption!");
                        }
                    }
                },
                new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name name) {
                        mRegisteredPrefixIds.remove(mMouseActivity.getString(R.string.ndn_prefix_update_seq));
                        mPrefixRegisterError = true;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
        mRegisteredPrefixIds.put(mMouseActivity.getString(R.string.ndn_prefix_update_seq), prefixId);

        // Prefix for providing server's password salt
        Name prefix_salt = new Name(mMouseActivity.getString(R.string.ndn_prefix_salt));
        prefixId = mFace.registerPrefix(prefix_salt,
                new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        Log.d(TAG, "Got interest: " + interest.getName());

                        // Salt sent in cleartext
                        try {
                            Data replyData = new Data(interest.getName());
                            replyData.getMetaInfo().setFreshnessPeriod(mFreshnessPeriod);

                            // Log.d(TAG, "Sending salt reply: " + replyString);

                            // Set content of data
                            replyData.setContent(new Blob(mSalt));

                            // Send data out face
                            face.putData(replyData);

                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Failed to put data.");
                        }
                    }
                },
                new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name name) {
                        mRegisteredPrefixIds.remove(mMouseActivity.getString(R.string.ndn_prefix_salt));
                        mPrefixRegisterError = true;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
        mRegisteredPrefixIds.put(mMouseActivity.getString(R.string.ndn_prefix_salt), prefixId);
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
}
