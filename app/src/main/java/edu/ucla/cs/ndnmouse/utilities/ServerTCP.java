package edu.ucla.cs.ndnmouse.utilities;

import android.graphics.Point;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import edu.ucla.cs.ndnmouse.MouseActivity;

/**
 * Utilities to provide TCP and NDN communication with the PC client
 *
 * Based off of example code at:
 * https://developer.android.com/samples/PermissionRequest/src/com.example.android.permissionrequest/SimpleWebServer.html
 */
public class ServerTCP implements Runnable {

    private static final String TAG = ServerTCP.class.getSimpleName();

    private MouseActivity mActivity;

    private ServerSocket mServerSocket;
    private final int mPort;
    private boolean mIsRunning;

    /**
     * Constructor for server
     *
     * @param activity of the caller (so we can get position points)
     * @param port number for server to listen on
     */
    public ServerTCP(MouseActivity activity, int port) {
        mActivity = activity;
        mPort = port;
    }

    public void start() {
        mIsRunning = true;
        new Thread(this).start();
        Log.d(TAG, "Started server... " + getIPAddress(true));
    }

    public void stop() {
        try {
            mIsRunning = false;
            if (null != mServerSocket) {
                mServerSocket.close();
                mServerSocket = null;
            }
            Log.d(TAG, "Stopped server...");
        } catch (IOException e) {
            Log.e(TAG, "Error closing the server socket.", e);
        }
    }

    @Override
    public void run() {
        try {
            mServerSocket = new ServerSocket(mPort);
            while (mIsRunning) {
                Socket socket = mServerSocket.accept();
                Log.d(TAG, "Accepted client connection...");
                handle(socket);
                socket.close();
            }
        } catch (SocketException e) {
            Log.d(TAG, "Socket got disconnected!");
        } catch (IOException e) {
            Log.e(TAG, "Web server error.", e);
        }
    }

    private void handle(Socket socket) throws IOException {
        BufferedReader reader = null;
        PrintStream output = null;
        try {
            String route = null;

            // Read HTTP headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while (!TextUtils.isEmpty(line = reader.readLine())) {
                if (line.startsWith("GET /")) {
                    int start = line.indexOf('/') + 1;
                    int end = line.indexOf(' ', start);
                    route = line.substring(start, end);
                    break;
                }
            }

            // Output stream that we send the response to
            output = new PrintStream(socket.getOutputStream());

            // TODO look for specific message, otherwise throw error
            if (null == route) {
                writeServerError(output);
                return;
            }

            // Send out the content
            // TODO bad form
            while (mIsRunning && socket.isConnected()) {
                Point lastPos = mActivity.getLastPosition();
                output.write((lastPos.x + "," + lastPos.y + "\n").getBytes());
                output.flush();
                Thread.sleep(100);
            }
        } catch (InterruptedException | SocketException e) {
            e.printStackTrace();
        } finally {
            if (null != output) {
                output.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
    }

    private void writeServerError(PrintStream output) {
        output.println("HTTP/1.0 500 Internal Server Error");
        output.flush();;
    }

    /**
     * Get IP address from first non-localhost interface
     * Based on code from:
     * http://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    private static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return (delim < 0) ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
