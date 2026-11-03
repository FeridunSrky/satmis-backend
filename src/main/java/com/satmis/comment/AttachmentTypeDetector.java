package com.satmis.comment;

import com.satmis.enums.AttachmentType;
import com.satmis.listing.ImageTypeDetector;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

// Recognises the format from the file's first bytes; the declared content type is only the client's claim
final class AttachmentTypeDetector {

    static final int HEADER_LENGTH = ImageTypeDetector.HEADER_LENGTH;

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private AttachmentTypeDetector() {
    }

    static Optional<AttachmentType> detect(byte[] header) {
        if (header.length >= PDF_SIGNATURE.length
                && Arrays.equals(header, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
            return Optional.of(AttachmentType.PDF);
        }
        return ImageTypeDetector.detect(header).map(imageType -> AttachmentType.valueOf(imageType.name()));
    }
}
