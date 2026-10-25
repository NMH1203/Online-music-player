package vn.edu.usth.online_music_player.backend.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private final byte[] secret;
    public TokenService(@Value("${auth.jwt-secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 bytes.");
    }
    public String issue(UUID user, UUID family) {
        try {
            Instant now = Instant.now();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().issuer("online-music-player").audience("music-android")
                    .subject(user.toString()).claim("family", family.toString()).issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(900))).jwtID(UUID.randomUUID().toString()).build());
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) { throw new IllegalStateException("Cannot sign token", e); }
    }
    public record Identity(UUID userId, UUID familyId) {}
    public Identity verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))
                || !"online-music-player".equals(claims.getIssuer())
                || !claims.getAudience().contains("music-android")
                || claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(Instant.now()))
                throw new IllegalArgumentException("Invalid token");
            return new Identity(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getStringClaim("family")));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid or expired access token"); }
    }
}
