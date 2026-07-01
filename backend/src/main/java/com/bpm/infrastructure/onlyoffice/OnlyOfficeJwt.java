package com.bpm.infrastructure.onlyoffice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Ký/kiểm JWT HS256 cho OnlyOffice (Story 3.10) — tự cài bằng javax.crypto, không thêm thư viện ngoài.
 * OnlyOffice yêu cầu config editor + callback đều ký bằng cùng secret (khớp docker-compose).
 */
@Component
public class OnlyOfficeJwt {

    private final byte[] secret;
    private final ObjectMapper om;

    public OnlyOfficeJwt(@Value("${bpm.onlyoffice.jwt-secret}") String secret, ObjectMapper om) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.om = om;
    }

    /** Ký payload → chuỗi JWT compact. */
    public String sign(Map<String, Object> payload) {
        try {
            String header = b64(om.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String body = b64(om.writeValueAsBytes(payload));
            String sig = b64(hmac((header + "." + body).getBytes(StandardCharsets.UTF_8)));
            return header + "." + body + "." + sig;
        } catch (Exception e) {
            throw new IllegalStateException("Không ký được JWT OnlyOffice", e);
        }
    }

    /** Kiểm chữ ký + trả payload (JsonNode). Ném lỗi nếu chữ ký sai. */
    public JsonNode verify(String token) {
        try {
            String[] p = token.split("\\.");
            if (p.length != 3) {
                throw new IllegalArgumentException("JWT không hợp lệ");
            }
            String expected = b64(hmac((p[0] + "." + p[1]).getBytes(StandardCharsets.UTF_8)));
            if (!constantEquals(expected, p[2])) {
                throw new SecurityException("Chữ ký JWT OnlyOffice sai");
            }
            return om.readTree(Base64.getUrlDecoder().decode(p[1]));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Không kiểm được JWT OnlyOffice", e);
        }
    }

    private byte[] hmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static boolean constantEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
