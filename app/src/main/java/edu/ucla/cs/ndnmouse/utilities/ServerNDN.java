package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

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
import java.util.HashMap;
import java.util.LinkedList;

import edu.ucla.cs.ndnmouse.MouseActivity;
import edu.ucla.cs.ndnmouse.R;

public class ServerNDN implements Runnable, Server {

    private static final String TAG = ServerNDN.class.getSimpleName();
    MouseActivity mMouseActivity;                   // Reference to calling activity

    Face mFace;                                             // Reference to the NDN face we will use to serve interests
    // private final int mPort = 6363;                      // Default NFD port
    private boolean mServerIsRunning = false;               // Controls if server thread is spinning or not
    float mSensitivity;                                     // Sensitivity multiplier for relative movement
    private final static int mUpdateIntervalMillis = 50;    // Number of milliseconds to wait before sending next update. May require tuning.
    final static double mFreshnessPeriod = 0;               // Number of milliseconds data is considered fresh. May require tuning.

    private Handler mPrefixErrorHandler;                            // Handles work for the UI thread (toast) when there is an error setting up prefixes
    HashMap<String, Long> mRegisteredPrefixIds = new HashMap<String, Long>();  // Keeps track of all registered prefix IDs
    boolean mPrefixRegisterError = false;                           // Tracks error during prefix registration
    LinkedList<Integer> mClickQueue = new LinkedList<>();           // Holds a queue of all incoming clicks that need to be sent out to client
    private KeyChain mKeyChain;                                     // Keychain reference (server identity)

    public ServerNDN(MouseActivity activity, float sensitivity) {
        mMouseActivity = activity;
        mSensitivity = sensitivity;

        // Makes a toast to alert user to restart NFD
        mPrefixErrorHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Toast.makeText(mMouseActivity.getApplicationContext(), "NFD doesn't appear to be running correctly. Please restart NFD and try again.", Toast.LENGTH_LONG).show();
            }
        };
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

            if (!mPrefixRegisterError) {
                while (mServerIsRunning) {
                    // TODO does this require thread synchronization? should not send interests at the same time
                    mFace.processEvents();
                    Thread.sleep(mUpdateIntervalMillis);
                }
            } else {
                Log.e(TAG, "One or more prefixes failed to register!");
                mMouseActivity.finish();
                // Make a toast notifying user to restart NFD
                Message message = mPrefixErrorHandler.obtainMessage();
                message.sendToTarget();
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
            mKeyChain.createIdentityAndCertificate(new Name(mMouseActivity.getString(R.string.ndn_prefix_identity)));
            mKeyChain.getIdentityManager().setDefaultIdentity(new Name(mMouseActivity.getString(R.string.ndn_prefix_identity)));
        }

        // Set KeyChain and certificate
        mFace.setCommandSigningInfo(mKeyChain, mKeyChain.getDefaultCertificateName());
    }

    /**
     * Setup prefixes that this server will respond to
     *
     * @throws IOException for putting data at Face
     * @throws SecurityException for Face registration
     */
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

    /**
     * Send a click command to an existing client
     *
     * @param click identifier for the type of click
     * @throws IOException for socket IO error
     */
    public void executeClick(int click) throws IOException {
        mClickQueue.add(click);
    }

    /**
     * This is called whenever settings are updated, so the server can change its behavior on the fly
     *
     * @param key of the setting being updated
     * @param value of the updated setting (generic type)
     */
    public <T> void UpdateSettings(int key, T value) {
        switch (key) {
            case R.string.pref_sensitivity_key:
                mSensitivity = (Float) value;
                break;
            default:
                Log.e(TAG, "Error: setting to update not recognized!");
        }
        Log.d(TAG, "Updated " + mMouseActivity.getString(key) + " with new value " + value);
    }
}
