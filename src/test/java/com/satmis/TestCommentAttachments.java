package com.satmis;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

// Inserts attachments as if the user had uploaded and completed them, so comment tests need no storage container
public final class TestCommentAttachments {

    private TestCommentAttachments() {
    }

    public static long uploaded(JdbcTemplate jdbcTemplate, long ownerId, String fileType) {
        return jdbcTemplate.queryForObject("""
                        insert into comment_attachments (owner_id, status, object_key, file_type, size_bytes, created_at, version)
                        values (?, 'UPLOADED', ?, ?, 1000, now(), 0)
                        returning id""",
                Long.class, ownerId, "comments/test/" + UUID.randomUUID(), fileType);
    }

    public static String statusOf(JdbcTemplate jdbcTemplate, long attachmentId) {
        return jdbcTemplate.queryForObject("select status from comment_attachments where id = ?", String.class,
                attachmentId);
    }
}
