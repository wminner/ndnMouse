package edu.ucla.cs.ndnmouse.utilities;

import java.io.IOException;

public interface Server {
    /**
     * Starts the server on its own thread
     */
    void start();

    /**
     * Stops the server thread and cleans up any worker threads it may be running
     */
    void stop();

    /**
     * Tells server to send a mouse click command
     * @param command type of click (using clicks defined in strings.xml)
     */
    void executeCommand(int command);

    /**
     * Tells server to send a custom type message to all clients
     * @param message string to type on clients
     */
    void executeTypedMessage(String message);

    /**
     * This is called whenever settings are updated, so the server can change its behavior on the fly
     * @param key of the setting being updated
     * @param value of the updated setting (generic type)
     */
    <T> void UpdateSettings(int key, T value);
}
