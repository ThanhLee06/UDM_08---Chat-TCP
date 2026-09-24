package vn.edu.ut.udm08.shared.protocol;

import java.io.BufferedReader;
import java.io.IOException;

/** A frame is complete only after LF; EOF must not complete a partial message. */
public final class JsonLineReader {
    public static final int MAX_FRAME_CHARS = 262144;
    private JsonLineReader() {}
    public static String read(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') return line.toString();
            if (line.length() >= MAX_FRAME_CHARS) throw new IOException("FRAME_TOO_LARGE");
            if (ch != '\r') line.append((char) ch);
        }
        if (!line.isEmpty()) throw new IOException("INCOMPLETE_FRAME");
        return null;
    }
}
