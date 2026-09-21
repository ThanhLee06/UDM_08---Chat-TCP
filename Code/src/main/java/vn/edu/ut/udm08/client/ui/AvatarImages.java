package vn.edu.ut.udm08.client.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

public final class AvatarImages {
    private AvatarImages() {}
    public static ImageView view(String id, double size) {
        String preset = id != null && id.matches("avatar[123]") ? id : "avatar1";
        ImageView view = new ImageView(new Image(AvatarImages.class.getResource("/images/" + preset + ".jpg").toExternalForm()));
        view.setFitWidth(size); view.setFitHeight(size);
        view.setClip(new Circle(size / 2, size / 2, size / 2));
        return view;
    }
}
