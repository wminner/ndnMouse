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
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.KeyChain;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.IdentityManager;
import net.named_data.jndn.security.identity.MemoryIdentityStorage;
import net.named_data.jndn.security.identity.MemoryPrivateKeyStorage;
import net.named_data.jndn.util.Blob;

import java.io.IOException;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;

public class ServerNDN implements Runnable, Server {

    private static final String TAG = ServerNDN.class.getSimpleName();
    private MouseActivity mMouseActivity;

    private Face mFace;
    // private final int mPort = 6363;     // Default NFD port
    private boolean mServerIsRunning = false;
    private boolean mUseRelativeMovement;
    private final int mUpdateIntervalMillis = 50;  // Number of milliseconds to wait before sending next update. May require tuning.

    private int mPhoneWidth;
    private int mPhoneHeight;
    private int mPCWidth = 2560;    // TODO temp test value
    private int mPCHeight = 1335;   // TODO temp test value
    private float mRatioWidth;
    private float mRatioHeight;
    private Point mLastPos = new Point(0, 0);

    private static final long UNREGISTERED = -1;
    private long mRegisteredPrefixId = UNREGISTERED;
    private KeyChain mKeyChain;

    public ServerNDN(MouseActivity activity, int width, int height, boolean useRelativeMovement) {
        mMouseActivity = activity;
        mPhoneWidth = width;
        mPhoneHeight = height;
        mUseRelativeMovement = useRelativeMovement;

        // Calculate ratios between server screen (phone) and client screen (pc)
        mRatioWidth = (float) mPCWidth / mPhoneWidth;
        mRatioHeight = (float) mPCHeight / mPhoneHeight;
    }

    @Override
    public void start() {
        mServerIsRunning = true;
        Thread thread = new Thread(this);
        thread.start();
        Log.d(TAG, "Started NDN server...");
    }

    @Override
    public void stop() {
        mServerIsRunning = false;
        if (null != mFace) {
            mFace.shutdown();
            mFace = null;
        }
        Log.d(TAG, "Stopped NDN server...");
    }

    @Override
    public void run() {
        try {
            setupFace();
            registerPrefixes();

            if (UNREGISTERED != mRegisteredPrefixId) {
                while (mServerIsRunning) {
                    // TODO does this require thread synchronization? should not send interests at the same time
                    mFace.processEvents();
                    Thread.sleep(mUpdateIntervalMillis);
                }
            } else {
                Log.e(TAG, "Failed to register prefix!");
            }
        } catch (IOException|SecurityException|InterruptedException e) {
            e.printStackTrace();
        } catch (EncodingException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to encode/decode.");
        }
    }

    /**
     * Setup Face, its keychain and certificate
     *
     * @throws SecurityException for KeyChain getDefaultCertificate
     */
    private void setupFace() throws SecurityException {
        mFace = new Face();

        MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
        MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
        IdentityManager identityManager = new IdentityManager(identityStorage, privateKeyStorage);
        mKeyChain = new KeyChain(identityManager);

        // Check default identity is defined, and set it if not
        try {
            mKeyChain.getDefaultCertificateName();
        } catch (SecurityException e) {
            mKeyChain.createIdentityAndCertificate(new Name(mMouseActivity.getString(R.string.ndn_uri_identity)));
            mKeyChain.getIdentityManager().setDefaultIdentity(new Name(mMouseActivity.getString(R.string.ndn_uri_identity)));
        }

        // Set KeyChain and certificate
        mFace.setCommandSigningInfo(mKeyChain, mKeyChain.getDefaultCertificateName());
    }

    /**
     * Setup prefixes that this server will respond to
     *
     * @throws IOException
     * @throws SecurityException
     */
    private void registerPrefixes() throws IOException, SecurityException {
        Name prefix = new Name(mMouseActivity.getString(R.string.ndn_uri_mouse_move));
        mRegisteredPrefixId = mFace.registerPrefix(prefix,
                new OnInterestCallback() {
                    @Override
                    public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
                        Log.d(TAG, "Got interest: " + interest.getName());
                        Data data = new Data(interest.getName());
                        Point position;
                        String moveType;

                        // Using relative movement...
                        if (mUseRelativeMovement) {
                            position = mMouseActivity.getRelativePosition();
                            moveType = mMouseActivity.getString(R.string.protocol_move_relative);
                            // Skip update if no relative movement since last update
                            if (position.equals(0, 0))
                                return;
                        } else {    // Using absolute movement...
                            position = mMouseActivity.getAbsolutePosition();
                            moveType = mMouseActivity.getString(R.string.protocol_move_absolute);
                            // Skip update if no movement happened since the last update
                            if (position.equals(mLastPos)) {
                                return;
                            } else
                                mLastPos.set(position.x, position.y);
                        }
                        // Find scaled x and y position according to client's resolution
                        int scaledX = (int) (position.x * mRatioWidth);
                        int scaledY = (int) (position.y * mRatioHeight);
                        // Build reply string and set data contents
                        String replyString = moveType + " " + scaledX + "," + scaledY + "\n";
                        Log.d(TAG, "Sending update: " + replyString);
                        data.setContent(new Blob(replyString));

                        // Send data out face
                        try {
                            face.putData(data);
                            Log.d(TAG, "Sent data: " + data.getContent());
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Failed to put data.");
                        }
                    }
                },
                new OnRegisterFailed() {
                    @Override
                    public void onRegisterFailed(Name name) {
                        mRegisteredPrefixId = UNREGISTERED;
                        Log.e(TAG, "Failed to register prefix: " + name.toUri());
                    }
                });
    }

    @Override
    public void ExecuteClick(int click) throws IOException {

    }
}
