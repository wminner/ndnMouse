package edu.ucla.cs.ndnmouse.utilities;

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
     * Tells server to send a mouse click command
     *
     * @param click type of click (using clicks defined in strings.xml)
     * @throws IOException
     */
    public void executeClick(int click) throws IOException;

    /**
     * This is called whenever settings are updated, so the server can change its behavior on the fly
     *
     * @param key of the setting being updated
     * @param value of the updated setting (generic type)
     */
    public <T> void UpdateSettings(int key, T value);
}
