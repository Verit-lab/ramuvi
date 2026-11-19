package br.com.arenadev.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only OpenID Provider on loopback: JWKS + token endpoint signing RS256 ID Tokens.
 * Lets HTTP ITs drive the REAL Spring Security OIDC pipeline (state, PKCE, nonce,
 * signature, issuer, audience, expiry) and StudentOidcUserService without network
 * access or extra dependencies. Never used outside src/test.
 */
public final class FakeOidcProvider implements AutoCloseable {
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private final HttpServer server;
    private final KeyPair keys;
    private final Map<String, Map<String, Object>> codes = new ConcurrentHashMap<>();
    private final String clientId;

    public FakeOidcProvider(String clientId) {
        this.clientId = clientId;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            keys = generator.generateKeyPair();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        server.createContext("/jwks", this::jwks);
        server.createContext("/token", this::token);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Authorization code bound to the nonce Spring put in the authorization redirect. */
    public String prepareCode(String issuer, String subject, String nonce, String email,
                              Boolean emailVerified, String name) {
        Map<String, Object> claims = new LinkedHashMap<>();
        Instant now = Instant.now();
        claims.put("iss", issuer);
        claims.put("sub", subject);
        claims.put("aud", clientId);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(300).getEpochSecond());
        claims.put("nonce", nonce);
        if (email != null) claims.put("email", email);
        if (emailVerified != null) claims.put("email_verified", emailVerified);
        if (name != null) claims.put("name", name);
        String code = "code-" + UUID.randomUUID();
        codes.put(code, claims);
        return code;
    }

    private void jwks(HttpExchange exchange) throws IOException {
        RSAPublicKey key = (RSAPublicKey) keys.getPublic();
        String body = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test-key\",\"use\":\"sig\",\"alg\":\"RS256\","
                + "\"n\":\"" + URL.encodeToString(unsigned(key.getModulus().toByteArray())) + "\","
                + "\"e\":\"" + URL.encodeToString(unsigned(key.getPublicExponent().toByteArray())) + "\"}]}";
        respond(exchange, 200, body);
    }

    private void token(HttpExchange exchange) throws IOException {
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String code = null;
        for (String pair : form.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && parts[0].equals("code")) {
                code = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        Map<String, Object> claims = code == null ? null : codes.remove(code); // single use
        if (claims == null) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        respond(exchange, 200, "{\"access_token\":\"at-" + UUID.randomUUID() + "\",\"token_type\":\"Bearer\","
                + "\"expires_in\":300,\"scope\":\"openid profile email\",\"id_token\":\"" + sign(claims) + "\"}");
    }

    private String sign(Map<String, Object> claims) {
        try {
            String header = URL.encodeToString("{\"alg\":\"RS256\",\"kid\":\"test-key\",\"typ\":\"JWT\"}"
                    .getBytes(StandardCharsets.UTF_8));
            String payload = URL.encodeToString(json(claims).getBytes(StandardCharsets.UTF_8));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keys.getPrivate());
            signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + URL.encodeToString(signature.sign());
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static String json(Map<String, Object> claims) {
        StringBuilder out = new StringBuilder("{");
        claims.forEach((key, value) -> {
            if (out.length() > 1) out.append(',');
            out.append('"').append(key).append("\":");
            if (value instanceof Number || value instanceof Boolean) out.append(value);
            else out.append('"').append(value.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        });
        return out.append('}').toString();
    }

    private static byte[] unsigned(byte[] value) {
        if (value.length > 1 && value[0] == 0) {
            byte[] trimmed = new byte[value.length - 1];
            System.arraycopy(value, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return value;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
