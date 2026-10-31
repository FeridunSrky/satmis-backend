package com.satmis.listing;

import com.satmis.enums.ImageType;

import java.util.Arrays;
import java.util.Optional;

// Recognises the format from the file's first bytes; the declared content type is only the client's claim
final class ImageTypeDetector {

    static final int HEADER_LENGTH = 12;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    // WebP is a RIFF container: "RIFF", 4 bytes of length, then "WEBP"
    private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};
    private static final int WEBP_SIGNATURE_OFFSET = 8;

    private ImageTypeDetector() {
    }

    static Optional<ImageType> detect(byte[] header) {
        if (startsWith(header, 0, JPEG_SIGNATURE)) {
            return Optional.of(ImageType.JPEG);
        }
        if (startsWith(header, 0, PNG_SIGNATURE)) {
            return Optional.of(ImageType.PNG);
        }
        if (startsWith(header, 0, RIFF_SIGNATURE) && startsWith(header, WEBP_SIGNATURE_OFFSET, WEBP_SIGNATURE)) {
            return Optional.of(ImageType.WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] signature) {
        return data.length >= offset + signature.length
                && Arrays.equals(data, offset, offset + signature.length, signature, 0, signature.length);
    }
}
