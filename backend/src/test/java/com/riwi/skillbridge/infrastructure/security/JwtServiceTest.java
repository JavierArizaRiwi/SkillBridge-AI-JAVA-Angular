package com.riwi.skillbridge.infrastructure.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String LEGACY = "legacy-secret-with-at-least-32-characters-long";

    private static KeyPair rsa(int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        return g.generateKeyPair();
    }

    private static String pem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    private static JwtService service(String priv, String pub, String legacy, boolean ephemeral) {
        return new JwtService(priv, pub, "kid-1", legacy, ephemeral, 30);
    }

    private static UserDetails user(String email) {
        return new User(email, "x", List.of());
    }

    @Test
    void emite_rs256_con_claims_del_contrato_y_se_valida() throws Exception {
        KeyPair kp = rsa(2048);
        JwtService jwt = service(pem("PRIVATE KEY", kp.getPrivate().getEncoded()), "", "", false);
        UUID uid = UUID.randomUUID();

        String token = jwt.generate(uid, "ana@test.com", "CUSTOMER");

        var jws = Jwts.parser().verifyWith(kp.getPublic()).build().parseSignedClaims(token);
        assertEquals("RS256", jws.getHeader().getAlgorithm());
        assertEquals("kid-1", jws.getHeader().getKeyId());
        assertEquals("skillbridge", jws.getPayload().getIssuer());
        assertEquals("ana@test.com", jws.getPayload().getSubject());
        assertEquals(uid.toString(), jws.getPayload().get("uid", String.class));
        assertEquals("ana@test.com", jws.getPayload().get("email", String.class));
        assertEquals("CUSTOMER", jwt.extractRole(token));
        assertTrue(jwt.isValid(token, user("ana@test.com")));
        assertFalse(jwt.isValid(token, user("otra@test.com")));
    }

    @Test
    void la_clave_publica_puede_darse_explicita_y_debe_coincidir() throws Exception {
        KeyPair kp = rsa(2048);
        KeyPair other = rsa(2048);
        String priv = pem("PRIVATE KEY", kp.getPrivate().getEncoded());

        service(priv, pem("PUBLIC KEY", kp.getPublic().getEncoded()), "", false);
        assertThrows(IllegalArgumentException.class,
            () -> service(priv, pem("PUBLIC KEY", other.getPublic().getEncoded()), "", false));
    }

    @Test
    void acepta_pem_con_secuencias_n_literales_de_variables_de_entorno() throws Exception {
        KeyPair kp = rsa(2048);
        String oneLine = pem("PRIVATE KEY", kp.getPrivate().getEncoded()).replace("\n", "\\n");
        JwtService jwt = service(oneLine, "", "", false);
        assertTrue(jwt.isValid(jwt.generate(UUID.randomUUID(), "a@b.co", "ADMIN"), user("a@b.co")));
    }

    @Test
    void sin_claves_no_arranca_salvo_modo_efimero_explicito() {
        assertThrows(IllegalStateException.class, () -> service("", "", "", false));
        JwtService jwt = service("", "", "", true);
        assertTrue(jwt.isValid(jwt.generate(UUID.randomUUID(), "a@b.co", "ADMIN"), user("a@b.co")));
        assertTrue(jwt.publicKeyPem().startsWith("-----BEGIN PUBLIC KEY-----"));
    }

    @Test
    void rechaza_claves_rsa_menores_de_2048_bits() throws Exception {
        KeyPair weak = rsa(1024);
        assertThrows(IllegalArgumentException.class,
            () -> service(pem("PRIVATE KEY", weak.getPrivate().getEncoded()), "", "", false));
    }

    @Test
    void rechaza_token_firmado_con_otra_clave_rsa() throws Exception {
        JwtService jwt = service("", "", "", true);
        String forged = Jwts.builder().issuer("skillbridge").subject("a@b.co").claim("role", "ADMIN")
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(rsa(2048).getPrivate(), Jwts.SIG.RS256).compact();
        assertThrows(JwtException.class, () -> jwt.extractUsername(forged));
    }

    @Test
    void rechaza_token_rs256_con_emisor_incorrecto() throws Exception {
        KeyPair kp = rsa(2048);
        JwtService jwt = service(pem("PRIVATE KEY", kp.getPrivate().getEncoded()), "", "", false);
        String token = Jwts.builder().issuer("otro").subject("a@b.co")
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(kp.getPrivate(), Jwts.SIG.RS256).compact();
        assertThrows(JwtException.class, () -> jwt.extractUsername(token));
    }

    @Test
    void rechaza_hs256_si_no_hay_legacy_secret_y_tokens_sin_firma() {
        JwtService jwt = service("", "", "", true);
        String hs = Jwts.builder().subject("a@b.co").expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(LEGACY.getBytes(StandardCharsets.UTF_8))).compact();
        String none = Jwts.builder().subject("a@b.co").compact();
        assertThrows(JwtException.class, () -> jwt.extractUsername(hs));
        assertThrows(JwtException.class, () -> jwt.extractUsername(none));
    }

    @Test
    void con_legacy_secret_valida_hs256_heredado_pero_siempre_emite_rs256() {
        JwtService jwt = service("", "", LEGACY, true);
        String hs = Jwts.builder().subject("a@b.co").claim("role", "CUSTOMER")
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(Keys.hmacShaKeyFor(LEGACY.getBytes(StandardCharsets.UTF_8))).compact();
        assertEquals("a@b.co", jwt.extractUsername(hs));
        String issued = jwt.generate(UUID.randomUUID(), "a@b.co", "CUSTOMER");
        assertTrue(Jwts.parser().verifyWith(Keys.hmacShaKeyFor(LEGACY.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(hs).getHeader().getAlgorithm().equals("HS256"));
        assertThrows(JwtException.class, () -> Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(LEGACY.getBytes(StandardCharsets.UTF_8))).build().parseSignedClaims(issued));
    }

    @Test
    void el_secreto_legacy_corto_es_rechazado() {
        assertThrows(IllegalArgumentException.class, () -> service("", "", "corto", true));
    }
}
