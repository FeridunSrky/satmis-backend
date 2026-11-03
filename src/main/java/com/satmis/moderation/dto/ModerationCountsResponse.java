package com.satmis.moderation.dto;

// Badges for the admin tabs. reportedListings counts listings with open reports, not the reports themselves.
public record ModerationCountsResponse(long pendingListings, long pendingComments, long reportedListings) {
}
