package vn.edu.ut.udm08.shared.protocol;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JsonLineReaderTest {
    @Test void requiresDelimiterAndRejectsOversizedFrame() throws Exception {
        assertEquals("{}", JsonLineReader.read(new BufferedReader(new StringReader("{}\n"))));
        assertThrows(IOException.class, () -> JsonLineReader.read(new BufferedReader(new StringReader("{}"))));
        assertThrows(IOException.class, () -> JsonLineReader.read(new BufferedReader(new StringReader("x".repeat(JsonLineReader.MAX_FRAME_CHARS + 1)))));
    }
}
