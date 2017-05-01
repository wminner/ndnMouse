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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;

public class ServerNDNSecure extends ServerNDN {

    private static final String TAG = ServerNDNSecure.class.getSimpleName();

    private SecretKeySpec mKey;
    private Cipher mCipher;
    private SecureRandom mRandom;
    private final static int mIvBytes = 16;
    private final static int mAesBlockSize = 16;
    private final static int mSeqNumBytes = 4;

    public ServerNDNSecure(MouseActivity activity, float sensitivity, SecretKeySpec key) {
        super(activity, sensitivity);

        mKey = key;
        // Create CSRNG to produce IVs
        mRandom = new SecureRandom();

        // Get and init cipher algorithm
        try {
            // Padding is handled by my own custom PKCS5 padding function (see NetworkHelpers.PKCS5Pad)
            mCipher = Cipher.getInstance("AES/CBC/NoPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
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
                        String replyString = moveType + " " + scaledX + "," + scaledY;
                        // Log.d(TAG, "Sending update: " + replyString);
                        replyData.setContent(new Blob(replyString));

                        // Send data out face
                        try {
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
                        String replyString = mMouseActivity.getString(mClickQueue.remove());
                        // Log.d(TAG, "Sending update: " + replyString);
                        replyData.setContent(new Blob(replyString));

                        // Send data out face
                        try {
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
                        mRegisteredPrefixIds.remove(mMouseActivity.getString(R.string.ndn_prefix_mouse_click));
                        mPrefixRegisterError = true;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
        mRegisteredPrefixIds.put(mMouseActivity.getString(R.string.ndn_prefix_mouse_click), prefixId);
    }
}
