package vn.edu.ut.udm08.shared.protocol;

import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.*;

/** Closing the socket interrupts a blocked TCP write without blocking the UI forever. */
public final class SocketWrites {
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SocketWriteDeadline"); t.setDaemon(true); return t;
    });
    private SocketWrites() {}
    public static void line(Socket socket, PrintWriter writer, String frame, int timeoutMs) {
        ScheduledFuture<?> deadline = DEADLINES.schedule(() -> {
            try { socket.close(); } catch (IOException ignored) {}
        }, timeoutMs, TimeUnit.MILLISECONDS);
        try { writer.println(frame); } finally { deadline.cancel(false); }
    }
}
