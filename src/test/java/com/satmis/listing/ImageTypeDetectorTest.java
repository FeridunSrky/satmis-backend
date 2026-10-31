package com.satmis.listing;

import com.satmis.enums.ImageType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeDetectorTest {

    @Test
    void detect_jpegHeader_returnsJpeg() {
        assertThat(ImageTypeDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10))).contains(ImageType.JPEG);
    }

    @Test
    void detect_pngHeader_returnsPng() {
        assertThat(ImageTypeDetector.detect(bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D)))
                .contains(ImageType.PNG);
    }

    @Test
    void detect_webpHeader_returnsWebp() {
        assertThat(ImageTypeDetector.detect("RIFF$\0\0\0WEBP".getBytes(StandardCharsets.ISO_8859_1)))
                .contains(ImageType.WEBP);
    }

    @Test
    void detect_otherRiffFile_returnsEmpty() {
        assertThat(ImageTypeDetector.detect("RIFF$\0\0\0WAVE".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }

    @Test
    void detect_svgPretendingToBeAnImage_returnsEmpty() {
        assertThat(ImageTypeDetector.detect("<svg xmlns=".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }

    @Test
    void detect_headerShorterThanSignature_returnsEmpty() {
        assertThat(ImageTypeDetector.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ImageTypeDetector.detect(new byte[0])).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
