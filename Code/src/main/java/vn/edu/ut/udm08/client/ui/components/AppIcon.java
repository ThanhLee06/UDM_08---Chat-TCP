package vn.edu.ut.udm08.client.ui.components;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.SVGPath;

public final class AppIcon {

    public static final String PATH_USERS = "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z";
    public static final String PATH_USER_PLUS = "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    public static final String PATH_REFRESH = "M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    public static final String PATH_SEARCH = "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
    public static final String PATH_INFO = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z";
    public static final String PATH_SMILE = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5S7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z";
    public static final String PATH_IMAGE = "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z";
    public static final String PATH_PAPERCLIP = "M16.5 6v11.5c0 2.21-1.79 4-4 4s-4-1.79-4-4V5c0-1.38 1.12-2.5 2.5-2.5s2.5 1.12 2.5 2.5v10.5c0 .83-.67 1.5-1.5 1.5s-1.5-.67-1.5-1.5V6H10v9.5c0 1.93 1.57 3.5 3.5 3.5s3.5-1.57 3.5-3.5V5c0-3.04-2.46-5.5-5.5-5.5S6 1.96 6 5v12.5c0 3.58 2.92 6.5 6.5 6.5s6.5-2.92 6.5-6.5V6h-2.5z";
    public static final String PATH_SEND = "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z";
    public static final String PATH_PIN = "M16 9V4l1 0V2H7v2l1 0v5l-2 2v2h5v7l1 1 1-1v-7h5v-2l-2-2z";
    public static final String PATH_DOWNLOAD = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";

    private AppIcon() {}

    public static SVGPath create(String pathData, double size, Paint fill) {
        SVGPath svg = new SVGPath();
        svg.setContent(pathData);
        svg.setFill(fill != null ? fill : Color.web("#475569"));
        
        // Scale to match requested size based on default 24x24 viewBox
        double scale = size / 24.0;
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        return svg;
    }

    public static SVGPath create(String pathData, double size) {
        return create(pathData, size, Color.web("#475569"));
    }

    public static SVGPath create(String pathData) {
        return create(pathData, 18.0, Color.web("#475569"));
    }
}
