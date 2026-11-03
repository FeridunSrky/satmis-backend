package com.satmis.comment;

import com.satmis.enums.AttachmentType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentTypeDetectorTest {

    @Test
    void detect_pdfSignature_returnsPdf() {
        byte[] header = "%PDF-1.7\n%âãÏÓ".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(AttachmentTypeDetector.detect(header)).contains(AttachmentType.PDF);
    }

    @Test
    void detect_imageSignatures_returnMatchingImageTypes() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThat(AttachmentTypeDetector.detect(jpeg)).contains(AttachmentType.JPEG);
        assertThat(AttachmentTypeDetector.detect(png)).contains(AttachmentType.PNG);
        assertThat(AttachmentTypeDetector.detect(webp)).contains(AttachmentType.WEBP);
    }

    @Test
    void detect_htmlOrTruncatedPdf_returnsEmpty() {
        assertThat(AttachmentTypeDetector.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(AttachmentTypeDetector.detect("%PDF".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(AttachmentTypeDetector.detect(new byte[0])).isEmpty();
    }
}
