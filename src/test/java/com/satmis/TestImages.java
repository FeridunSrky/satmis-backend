package com.satmis;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

// Inserts images as if the user had uploaded and completed them, so listing tests need no storage container
public final class TestImages {

    private TestImages() {
    }

    public static List<Long> uploaded(JdbcTemplate jdbcTemplate, long ownerId, int count) {
        return IntStream.range(0, count).mapToObj(index -> uploaded(jdbcTemplate, ownerId)).toList();
    }

    public static long uploaded(JdbcTemplate jdbcTemplate, long ownerId) {
        return jdbcTemplate.queryForObject("""
                        insert into listing_images (owner_id, status, object_key, image_type, size_bytes, cover, created_at)
                        values (?, 'UPLOADED', ?, 'JPEG', 1000, false, now())
                        returning id""",
                Long.class, ownerId, "listings/test/" + UUID.randomUUID() + ".jpg");
    }

    public static String objectKeyOf(JdbcTemplate jdbcTemplate, long imageId) {
        return jdbcTemplate.queryForObject("select object_key from listing_images where id = ?", String.class, imageId);
    }

    public static String statusOf(JdbcTemplate jdbcTemplate, long imageId) {
        return jdbcTemplate.queryForObject("select status from listing_images where id = ?", String.class, imageId);
    }
}
