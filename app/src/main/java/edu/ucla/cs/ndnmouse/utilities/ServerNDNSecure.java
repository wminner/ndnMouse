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

public class ServerNDNSecure extends ServerNDN {

    private static final String TAG = ServerNDNSecure.class.getSimpleName();

    private String mPassword;
    private SecretKeySpec mKey;
    private int mSeqNum;
    private static final int mMaxSeqNum = Integer.MAX_VALUE;

    /**
     * Constructor for server
     * @param activity of the caller (so we can get position points)
     * @param sensitivity multiplier for scaling movement
     * @param password from user
     */
    public ServerNDNSecure(MouseActivity activity, float sensitivity, String password) {
        super(activity, sensitivity);

        mPassword = password;
        try {
            mKey = mMouseActivity.makeKeyFromPassword(password);
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
        // Prefix for movement updates (synchronous)
        Name prefix_move = new Name(mMouseActivity.getString(R.string.ndn_prefix_mouse_move));
        long prefixId = mFace.registerPrefix(prefix_move,
                new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        // Log.d(TAG, "Got interest: " + interest.getName());
                        Data replyData = new Data(interest.getName());
                        replyData.getMetaInfo().setFreshnessPeriod(mFreshnessPeriod);
                        Point position = mMouseActivity.getRelativePosition();
                        String moveType = mMouseActivity.getString(R.string.protocol_move_relative);

                        // Skip update if no relative movement since last update
                        if (position.equals(0, 0))
                            return;

                        // Find scaled x and y position according to sensitivity (absolute movement deprecated for now)
                        int scaledX = (int) (position.x * mSensitivity);
                        int scaledY = (int) (position.y * mSensitivity);
                        // Build reply string and set data contents
                        byte[] msg = (moveType + " " + scaledX + "," + scaledY).getBytes();
                        // Log.d(TAG, "Sending update: " + replyString);

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
                        mRegisteredPrefixIds.remove(mMouseActivity.getString(R.string.ndn_prefix_mouse_move));
                        mPrefixRegisterError = true;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
        mRegisteredPrefixIds.put(mMouseActivity.getString(R.string.ndn_prefix_mouse_move), prefixId);

        // Prefix for click commands (asynchronous events)
        Name prefix_click = new Name(mMouseActivity.getString(R.string.ndn_prefix_mouse_click));
        prefixId = mFace.registerPrefix(prefix_click,
                new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        // Log.d(TAG, "Got interest: " + interest.getName());
                        Data replyData = new Data(interest.getName());
                        replyData.getMetaInfo().setFreshnessPeriod(mFreshnessPeriod);

                        // If no click has occurred, return let the interest timeout
                        if (mClickQueue.isEmpty())
                            return;

                        // Build reply string and set data contents
                        byte[] msg = (mMouseActivity.getString(mClickQueue.remove())).getBytes();
                        // Log.d(TAG, "Sending update: " + replyString);

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
                            e.printStackTrace();
                            Log.e(TAG, "Error during data encryption!");
                        }
                    }
                },
                new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name name) {
                        mRegisteredPrefixIds.remove(mMouseActivity.getString(R.string.ndn_prefix_mouse_click));
                        mPrefixRegisterError = true;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
        mRegisteredPrefixIds.put(mMouseActivity.getString(R.string.ndn_prefix_mouse_click), prefixId);

        // Prefix for seq num updates (special interest for cases of desync only)
        Name prefix_update_seq = new Name(mMouseActivity.getString(R.string.ndn_prefix_update_seq));
        prefixId = mFace.registerPrefix(prefix_update_seq,
                new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {

                        Log.d(TAG, "Got interest: " + interest.getName());

                        try {
                            // Sync the server's seq num using last interest component (if larger than current seq num)
                            int syncSeqNum = Integer.valueOf(interest.getName().getSubName(-1).toUri().substring(1));
                            mSeqNum = Math.max(syncSeqNum, mSeqNum);
                            Log.d(TAG, "Setting server seq num to " + mSeqNum);

                            Data replyData = new Data(interest.getName());
                            replyData.getMetaInfo().setFreshnessPeriod(mFreshnessPeriod);

                            // Build reply string and set data contents
                            byte[] msg = (mMouseActivity.getString(R.string.ndn_update_seq_reply)).getBytes();
                            // Log.d(TAG, "Sending update seq reply: " + replyString);

                            // Encrypt reply
                            MousePacket mousePacket = new MousePacket(msg, getNextSeqNum(), mKey);
                            byte[] encryptedReply = mousePacket.getEncryptedPacket();

                            // Set content of data
                            replyData.setContent(new Blob(encryptedReply));

                            // Send data out face
                            face.putData(replyData);

                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Invalid seq num update!");
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
                        mRegisteredPrefixIds.remove(mMouseActivity.getString(R.string.ndn_prefix_mouse_click));
                        mPrefixRegisterError = true;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
        mRegisteredPrefixIds.put(mMouseActivity.getString(R.string.ndn_prefix_update_seq), prefixId);
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
