package vn.edu.ut.udm08.client.ui;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import vn.edu.ut.udm08.client.network.ChatClient;
import vn.edu.ut.udm08.shared.model.MessageType;
public final class AvatarImages {
    private static final Map<String, CompletableFuture<Image>> images = new ConcurrentHashMap<>();
    private static ChatClient client;
    private AvatarImages() {}
    public static void configure(ChatClient value) {
        if (client != value) images.clear();
        client = value;
    }
    public static ImageView view(String id, double size) {
        ImageView view = new ImageView();
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setClip(new Circle(size / 2, size / 2, size / 2));
        apply(view, id);
        return view;
    }
    public static void apply(ImageView view, String id) {
        view.getProperties().put("avatar", id == null ? "" : id);
        String preset = id != null && id.matches("avatar[123]") ? id : "avatar1";
        view.setImage(new Image(AvatarImages.class.getResource("/images/" + preset + ".jpg").toExternalForm()));
        if (id == null || !id.startsWith("avatar:") || client == null) return;
        var request = images.computeIfAbsent(id, key -> client.requestFeature(MessageType.AVATAR_GET, key)
                .thenApply(response -> new Image(new ByteArrayInputStream(Base64.getDecoder().decode(response.content)))));
        request.whenComplete((image, error) -> Platform.runLater(() -> {
            if (error != null) images.remove(id, request);
            else if (id.equals(view.getProperties().get("avatar")) && !image.isError()) view.setImage(image);
        }));
    }
}
