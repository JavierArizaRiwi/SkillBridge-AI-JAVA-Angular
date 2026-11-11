package com.riwi.skillbridge.infrastructure.security;

import com.riwi.skillbridge.application.port.out.TokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

/**
 * Emite y valida JWT con RS256 (ver docs/contracts/mentorships/security.md).
 *
 * <p>Solo este servicio posee la clave privada; los servicios satélite validan con la pública.
 * No existe valor por defecto de clave: si falta configuración la aplicación no arranca,
 * salvo que se active explícitamente {@code app.jwt.generate-ephemeral-keys} (solo desarrollo/tests).
 *
 * <p>Durante la migración desde HS256 puede configurarse {@code app.jwt.legacy-secret} para seguir
 * <em>validando</em> (nunca emitiendo) tokens antiguos. Retirarlo al cumplirse la vida máxima del token.
 */
@Service
public class JwtService implements TokenPort {
    static final String ISSUER = "skillbridge";
    private static final String RS256 = "RS256";
    private static final String HS256 = "HS256";
    private static final int MIN_RSA_BITS = 2048;
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final SecretKey legacyKey;
    private final String keyId;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.private-key:}") String privatePem,
                      @Value("${app.jwt.public-key:}") String publicPem,
                      @Value("${app.jwt.key-id:skillbridge-1}") String keyId,
                      @Value("${app.jwt.legacy-secret:}") String legacySecret,
                      @Value("${app.jwt.generate-ephemeral-keys:false}") boolean generateEphemeralKeys,
                      @Value("${app.jwt.expiration-minutes:120}") long expirationMinutes) {
        KeyPair pair = resolveKeys(privatePem, publicPem, generateEphemeralKeys);
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
        this.keyId = keyId;
        this.expirationMinutes = expirationMinutes;
        if (legacySecret == null || legacySecret.isBlank()) {
            this.legacyKey = null;
        } else {
            if (legacySecret.length() < 32) {
                throw new IllegalArgumentException("JWT legacy secret must have at least 32 characters");
            }
            this.legacyKey = Keys.hmacShaKeyFor(legacySecret.getBytes(StandardCharsets.UTF_8));
            log.warn("JWT legacy HS256 validation is ENABLED; remove app.jwt.legacy-secret once old tokens expire");
        }
    }

    @Override
    public String generate(UUID userId, String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .header().keyId(keyId).and()
            .issuer(ISSUER)
            .subject(email)
            .claim("uid", userId.toString())
            .claim("email", email)
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public String extractUsername(String token) {
        return claims(token).getSubject();
    }

    public boolean isValid(String token, UserDetails userDetails) {
        Claims claims = claims(token);
        return claims.getSubject().equalsIgnoreCase(userDetails.getUsername())
            && claims.getExpiration().after(new Date());
    }

    public String extractRole(String token) {
        return claims(token).get("role", String.class);
    }

    /** Clave pública en formato PEM X.509, útil para publicarla a los servicios satélite. */
    public String publicKeyPem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    private Claims claims(String token) {
        Jws<Claims> jws = Jwts.parser()
            .keyLocator(new LocatorAdapter<Key>() {
                @Override
                protected Key locate(JwsHeader header) {
                    String alg = header.getAlgorithm();
                    if (RS256.equals(alg)) return publicKey;
                    if (HS256.equals(alg) && legacyKey != null) return legacyKey;
                    throw new JwtException("Unsupported JWT algorithm");
                }
            })
            .build()
            .parseSignedClaims(token);
        // Los tokens RS256 siempre llevan emisor; los HS256 heredados no lo tenían.
        if (RS256.equals(jws.getHeader().getAlgorithm()) && !ISSUER.equals(jws.getPayload().getIssuer())) {
            throw new JwtException("Invalid JWT issuer");
        }
        return jws.getPayload();
    }

    private static KeyPair resolveKeys(String privatePem, String publicPem, boolean ephemeral) {
        if (privatePem == null || privatePem.isBlank()) {
            if (!ephemeral) {
                throw new IllegalStateException(
                    "app.jwt.private-key (JWT_PRIVATE_KEY) is required. For local development only, "
                        + "set app.jwt.generate-ephemeral-keys=true");
            }
            log.warn("Using EPHEMERAL in-memory JWT keys; tokens become invalid on restart. Never use in production");
            return generateKeyPair();
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey priv = factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privatePem)));
            if (!(priv instanceof RSAPrivateCrtKey crt)) {
                throw new IllegalArgumentException("JWT private key must be an RSA CRT key");
            }
            if (crt.getModulus().bitLength() < MIN_RSA_BITS) {
                throw new IllegalArgumentException("JWT RSA key must be at least " + MIN_RSA_BITS + " bits");
            }
            PublicKey pub = (publicPem == null || publicPem.isBlank())
                ? factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()))
                : factory.generatePublic(new X509EncodedKeySpec(decodePem(publicPem)));
            if (!(pub instanceof RSAPublicKey rsaPub) || !rsaPub.getModulus().equals(crt.getModulus())) {
                throw new IllegalArgumentException("JWT public key does not match the private key");
            }
            return new KeyPair(pub, priv);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid JWT key material (expected PKCS#8 private / X.509 public PEM)", e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_RSA_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Acepta PEM con saltos reales o con secuencias literales {@code \n} típicas de variables de entorno. */
    private static byte[] decodePem(String pem) {
        String body = pem.replace("\\n", "\n")
            .replaceAll("-----BEGIN [A-Z ]+-----", "")
            .replaceAll("-----END [A-Z ]+-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
