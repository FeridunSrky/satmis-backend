package com.satmis.auth;

import com.satmis.auth.dto.AuthResponse;

// The raw refresh token only travels to the controller, which puts it in the httpOnly cookie, never in the body
record AuthSession(AuthResponse response, String refreshToken) {
}
