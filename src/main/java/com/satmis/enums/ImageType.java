package com.satmis.enums;

import java.util.Arrays;
import java.util.Optional;

// Only raster formats browsers display safely; SVG is excluded because it can carry scripts
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    ImageType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> fromMimeType(String mimeType) {
        return Arrays.stream(values()).filter(type -> type.mimeType.equals(mimeType)).findFirst();
    }
}
