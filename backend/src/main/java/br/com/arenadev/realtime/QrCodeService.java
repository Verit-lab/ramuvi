package br.com.arenadev.realtime;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QrCodeService {
    private final Set<String> allowedOrigins;

    /** Only configured exact origins can be embedded in official QR codes. */
    public QrCodeService(
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
            @Value("${app.allowed-origin-patterns:http://localhost:3000,http://127.0.0.1:3000}")
                    String originPatterns
    ) {
        String primary = normalizedOrigin(frontendUrl);
        var configured = Arrays.stream(originPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank() && !value.contains("*"))
                .map(value -> {
                    try {
                        return normalizedOrigin(value);
                    } catch (IllegalArgumentException ignored) {
                        return null; // CORS wildcard patterns are not QR allowlist entries.
                    }
                })
                .filter(value -> value != null)
                .collect(Collectors.toSet());
        configured.add(primary);
        this.allowedOrigins = Set.copyOf(configured);
    }

    public byte[] joinPng(String baseUrl, String code, int size) {
        String origin = normalizedOrigin(baseUrl);
        if (!allowedOrigins.contains(origin)) {
            throw new IllegalArgumentException("Origem não autorizada para QR Code.");
        }
        return png(origin + "/join?code=" + code, size);
    }

    public byte[] png(String content, int size) {
        try {
            int resolvedSize = Math.max(180, Math.min(size, 640));
            BitMatrix matrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    resolvedSize,
                    resolvedSize,
                    java.util.Map.of(EncodeHintType.MARGIN, 1)
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Não foi possível gerar o QR Code.", error);
        }
    }

    private static String normalizedOrigin(String raw) {
        try {
            URI uri = URI.create(raw == null ? "" : raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || host == null || host.isBlank() || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                        && !"/".equals(uri.getRawPath()))
                    || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw new IllegalArgumentException("Origem de QR Code inválida.");
            }
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            return new URI(scheme, null, host.toLowerCase(Locale.ROOT), port,
                    null, null, null).toASCIIString();
        } catch (Exception error) {
            throw new IllegalArgumentException("Origem de QR Code inválida.");
        }
    }
}
