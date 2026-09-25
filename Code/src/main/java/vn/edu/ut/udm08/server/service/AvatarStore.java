package vn.edu.ut.udm08.server.service;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
public final class AvatarStore {
    private final Path directory;
    public AvatarStore(Path directory) {
        this.directory = directory;
    }
    public String save(String value) throws IOException {
        if (value == null) throw new IllegalArgumentException("Vui lòng chọn ảnh đại diện");
        if (value.matches("avatar[123]")) return value;
        if (!value.startsWith("data:image/png;base64,") || value.length() > 90000) {
            throw new IllegalArgumentException("Ảnh đại diện không hợp lệ");
        }
        byte[] bytes = Base64.getDecoder().decode(value.substring(22));
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Không đọc được ảnh");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) > 256 || reader.getHeight(0) > 256) {
                    throw new IllegalArgumentException("Ảnh đại diện tối đa 256 × 256");
                }
                if (reader.read(0) == null) throw new IllegalArgumentException("Ảnh bị lỗi");
            } finally {
                reader.dispose();
            }
        }
        Files.createDirectories(directory);
        String id = UUID.randomUUID().toString();
        Files.write(directory.resolve(id + ".png"), bytes);
        return "avatar:" + id;
    }
    public String read(String id) throws IOException {
        if (id == null || !id.matches("avatar:[a-f0-9-]{36}")) throw new IllegalArgumentException("Mã ảnh không hợp lệ");
        return Base64.getEncoder().encodeToString(Files.readAllBytes(directory.resolve(id.substring(7) + ".png")));
    }
}
