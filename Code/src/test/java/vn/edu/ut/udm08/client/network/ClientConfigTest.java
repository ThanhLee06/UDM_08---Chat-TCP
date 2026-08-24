package vn.edu.ut.udm08.client.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ST-10: ClientConfig and Host/Port Validation Tests")
class ClientConfigTest {

    @Test
    @DisplayName("TC_01: Cấu hình Host và Port hợp lệ")
    void testValidHostAndPort() {
        ClientConfig config = new ClientConfig("127.0.0.1", 8080);
        assertEquals("127.0.0.1", config.getHost());
        assertEquals(8080, config.getPort());
        assertEquals("127.0.0.1:8080", config.toString());

        ClientConfig configOf = ClientConfig.of("localhost", 9000);
        assertEquals("localhost", configOf.getHost());
        assertEquals(9000, configOf.getPort());
    }

    @Test
    @DisplayName("TC_02: Host có khoảng trắng thừa ở đầu/cuối phải được trim")
    void testHostWithWhitespaceTrimmed() {
        ClientConfig config = new ClientConfig("  192.168.1.100  ", 5000);
        assertEquals("192.168.1.100", config.getHost());
    }

    @Test
    @DisplayName("TC_03: Ném ngoại lệ khi Host là null")
    void testNullHostThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            new ClientConfig(null, 8080);
        });
        assertTrue(ex.getMessage().contains("Host"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("TC_04: Ném ngoại lệ khi Host là chuỗi rỗng hoặc chỉ toàn khoảng trắng")
    void testBlankHostThrowsException(String blankHost) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            new ClientConfig(blankHost, 8080);
        });
        assertTrue(ex.getMessage().contains("Host"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -8080, Integer.MIN_VALUE})
    @DisplayName("TC_05: Ném ngoại lệ khi Port <= 0")
    void testNonPositivePortThrowsException(int invalidPort) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            new ClientConfig("localhost", invalidPort);
        });
        assertTrue(ex.getMessage().contains("Port"));
    }

    @ParameterizedTest
    @ValueSource(ints = {65536, 70000, 100000, Integer.MAX_VALUE})
    @DisplayName("TC_06: Ném ngoại lệ khi Port > 65535")
    void testPortExceedsMaxThrowsException(int invalidPort) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            new ClientConfig("localhost", invalidPort);
        });
        assertTrue(ex.getMessage().contains("Port"));
    }

    @Test
    @DisplayName("TC_07: Kiểm tra giá trị biên của Port (1 và 65535)")
    void testBoundaryPorts() {
        assertDoesNotThrow(() -> new ClientConfig("localhost", 1));
        assertDoesNotThrow(() -> new ClientConfig("localhost", 65535));
    }
}
