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
    public void ExecuteClick(int click) throws IOException;
}
