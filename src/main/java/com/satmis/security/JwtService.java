package com.satmis.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpirationMs;

    public JwtService(JwtProperties properties) {
        this.signingKey = createSigningKey(properties.secret());
        this.accessExpirationMs = properties.accessExpirationMs();
    }

    // A missing JWT_SECRET reaches here as the literal "${JWT_SECRET}"; fail with a message that says
    // how to fix it instead of a bare Base64 error. The secret value itself is never included.
    private static SecretKey createSigningKey(String base64Secret) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        } catch (DecodingException | WeakKeyException ex) {
            throw new IllegalStateException("app.jwt.secret is missing or invalid: set the JWT_SECRET "
                    + "environment variable (or the git-ignored .env file in development) to a Base64 key "
                    + "of at least 32 bytes, e.g. generated with: openssl rand -base64 32", ex);
        }
    }

    // Only the e-mail is carried: every request reloads the user, so role and enabled come from the database
    public String generateAccessToken(CustomUserDetails userDetails) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpirationMs)))
                .signWith(signingKey)
                .compact();
    }

    // Verifies signature and expiry; throws JwtException otherwise
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }
}
