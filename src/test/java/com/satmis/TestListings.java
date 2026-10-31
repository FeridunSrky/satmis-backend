package com.satmis;

import org.springframework.jdbc.core.JdbcTemplate;

// New listings wait for admin approval; tests about visibility publish them directly instead of going through an admin
public final class TestListings {

    private TestListings() {
    }

    public static void approve(JdbcTemplate jdbcTemplate, long listingId) {
        jdbcTemplate.update("update listings set status = 'ACTIVE', rejection_reason = null where id = ?", listingId);
    }

    public static String statusOf(JdbcTemplate jdbcTemplate, long listingId) {
        return jdbcTemplate.queryForObject("select status from listings where id = ?", String.class, listingId);
    }
}
