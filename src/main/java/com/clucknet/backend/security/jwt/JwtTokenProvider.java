package com.clucknet.backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationInMs;

    // Decode base64 secret and generate HMAC-SHA Cryptographic Secret Key
    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(this.jwtSecret);
        } catch (IllegalArgumentException e) {
            log.warn("JWT secret is not Base64 encoded. Falling back to plain bytes.");
            keyBytes = this.jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        // Ensure the key bytes are at least 64 bytes (512 bits) to avoid WeakKeyException with HS512
        if (keyBytes.length < 64) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-512");
                keyBytes = md.digest(keyBytes);
            } catch (java.security.NoSuchAlgorithmException ex) {
                log.error("SHA-512 digest algorithm not found", ex);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Generate JWT Token from Authentication principal
    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    // Extract Username claim from validated token
    public String getUsernameFromJwt(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    // Cryptographically validate JWT token structure and signature
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("JWT validation failure: {}", ex.getMessage());
        }
        return false;
    }
}
