package edu.ucla.cs.ndnmouse.utilities;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import edu.ucla.cs.ndnmouse.MainActivity;
import edu.ucla.cs.ndnmouse.MouseActivity;

/**
 * Utilities to provide TCP and NDN communication with the PC client
 */
public final class ServerTCP {

    private static final String TAG = ServerTCP.class.getSimpleName();

    private MouseActivity activity;
    private ServerSocket serverSocket;
    private static final int socketServerPort = 10888;

    /**
     * Constructor for server
     *
     * @param activity of the calling class (for running the threads)
     */
    public ServerTCP(MouseActivity activity) {
        this.activity = activity;
        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();

        String ip = getIPAddress();
        Log.d(TAG, "Constructed server... " + ip);
    }

    /**
     * Destructor for server
     */
    public void onDestroy() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "Destroyed server...");
    }

    /**
     * Socket listening thread for the server
     */
    private class SocketServerThread extends Thread {

        int count = 0;

        @Override
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(socketServerPort);

                while (true) {
                    Socket socket = serverSocket.accept();
                    count++;
                    final String message = "#" + count + " from "
                            + socket.getInetAddress() + ":"
                            + socket.getPort() + "\n";

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, message);
                        }
                    });

                    // Reply to message
                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(socket, count);
                    socketServerReplyThread.run();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Socket reply thread for the server
     */
    private class SocketServerReplyThread extends Thread {

        private Socket hostThreadSocket;
        int count;

        SocketServerReplyThread(Socket socket, int count) {
            hostThreadSocket = socket;
            this.count = count;
        }

        @Override
        public void run() {
            OutputStream outputStream;
            String replyMessage = "Hello from Server, you are #" + count;

            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(replyMessage);
                printStream.close();

                final String message = "replyed: " + replyMessage + "\n";

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, message);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get socket server port
     *
     * @return The port of the server socket
     */
    public int getPort() {
        return socketServerPort;
    }

    /**
     * Display man-readable IP address of server
     *
     * @return Man-readable string of IP address (for debugging)
     */
    public String getIPAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();

                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "Server running at: " + inetAddress.getHostAddress() + ". ";
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return ip;
    }
}
