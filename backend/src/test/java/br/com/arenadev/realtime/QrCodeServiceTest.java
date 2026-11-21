package br.com.arenadev.realtime;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeServiceTest {
    private final QrCodeService service = new QrCodeService(
            "https://ramuvi.io/", "https://ramuvi.io,https://*.ramuvi.io,http://192.168.1.44:3000");

    @Test
    void exactAuthorizedOriginsGenerateImages() {
        assertThat(service.joinPng("https://RAMUVI.io:443", "ABCD12", 320)).isNotEmpty();
        assertThat(service.joinPng("http://192.168.1.44:3000/", "ABCD12", 320)).isNotEmpty();
    }

    @Test
    void ignoresWildcardCorsPatternsAndRejectsCrossOriginQrUrls() {
        for (String origin : new String[] {
                "https://student.ramuvi.io", "https://ramuvi.io.evil.example",
                "https://ramuvi.io@phishing.example", "https://phishing.example",
                "https://ramuvi.io:8443", "http://ramuvi.io", "ftp://ramuvi.io",
                "javascript:alert(1)", "https://ramuvi.io/join",
                "https://ramuvi.io?source=external", "https://ramuvi.io/#fragment",
                "https://user@ramuvi.io"
        }) {
            assertThatThrownBy(() -> service.joinPng(origin, "ABCD12", 320))
                    .as(origin).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
