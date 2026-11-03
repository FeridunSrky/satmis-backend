package com.satmis.enums;

import java.util.Arrays;
import java.util.Optional;

// Raster images and PDF reports. PDFs are stored as downloads, so browsers do not render them on the storage domain.
public enum AttachmentType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    private final String mimeType;
    private final String extension;

    AttachmentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extension() {
        return extension;
    }

    public boolean isDownloadOnly() {
        return this == PDF;
    }

    public static Optional<AttachmentType> fromMimeType(String mimeType) {
        return Arrays.stream(values()).filter(type -> type.mimeType.equals(mimeType)).findFirst();
    }
}
