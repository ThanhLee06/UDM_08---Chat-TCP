package vn.edu.ut.udm08.server.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.logging.*;

public final class ServerLog {
    private static boolean configured;
    private ServerLog() {}
    public static synchronized void configure() throws IOException {
        if (configured) return;
        Path directory = Path.of(System.getProperty("udm08.log.directory", "logs"));
        Files.createDirectories(directory);
        FileHandler file = new FileHandler(directory.resolve("server-%g.log").toString(), 2_000_000, 3, true);
        file.setEncoding("UTF-8"); file.setFormatter(new SimpleFormatter());
        Logger.getLogger("vn.edu.ut.udm08.server").addHandler(file);
        configured = true;
    }
}
