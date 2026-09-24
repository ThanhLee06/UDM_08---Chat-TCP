package vn.edu.ut.udm08.client.ui;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Base64;
import javafx.scene.image.Image;
public final class AvatarPayload {
    private AvatarPayload() {}
    public static String encode(Path path) throws java.io.IOException {
        if (java.nio.file.Files.size(path) > 5 * 1024 * 1024) throw new IllegalArgumentException("Ảnh tối đa 5 MB");
        Image image = new Image(path.toUri().toString(), 128, 128, false, true);
        if (image.isError() || image.getPixelReader() == null) throw new IllegalArgumentException("Không đọc được ảnh");
        var png = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(png, "png", bytes);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
