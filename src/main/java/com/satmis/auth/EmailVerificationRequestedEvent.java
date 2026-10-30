package com.satmis.auth;

// Published inside the issuing transaction; the mail goes out only after it commits.
record EmailVerificationRequestedEvent(Long userId, String email, String firstName, String rawToken) {
}
