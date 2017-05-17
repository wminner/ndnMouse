package edu.ucla.cs.ndnmouse.utilities;

import android.os.Parcelable;

import java.io.IOException;

public interface Server {
    /**
     * Starts the server on its own thread
     */
    public void start();

    /**
     * Stops the server thread and cleans up any worker threads it may be running
     */
    public void stop();

    /**
     * Main loop of server after calling thread.start()
     */
    public void run();

    /**
     * Tells server to send a mouse click command
     * @param command type of click (using clicks defined in strings.xml)
     * @throws IOException from sending out socket/face
     */
    public void executeCommand(int command) throws IOException;

    /**
     * Tells server to send a custom type message to all clients
     * @param message string to type on clients
     * @throws IOException from sending out socket/face
     */
    public void executeTypedMessage(String message) throws IOException;

    /**
     * This is called whenever settings are updated, so the server can change its behavior on the fly
     * @param key of the setting being updated
     * @param value of the updated setting (generic type)
     */
    public <T> void UpdateSettings(int key, T value);
}
