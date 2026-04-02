package com.bank.branch.platform.common.security.sign;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SignatureUtilsTest {
    @Test void signAndVerifyShouldMatch() {
        Map<String, String> params = Map.of("userId", "E001", "action", "login");
        String sig = SignatureUtils.sign(params, 1000L, "nonce1", "secret123");
        assertTrue(SignatureUtils.verify(params, 1000L, "nonce1", "secret123", sig));
    }
    @Test void verifyShouldFailForTamperedParams() {
        Map<String, String> params = Map.of("userId", "E001");
        String sig = SignatureUtils.sign(params, 1000L, "nonce1", "secret123");
        assertFalse(SignatureUtils.verify(Map.of("userId", "E002"), 1000L, "nonce1", "secret123", sig));
    }
    @Test void signShouldBeDeterministic() {
        Map<String, String> params = Map.of("a", "1", "b", "2");
        String sig1 = SignatureUtils.sign(params, 100L, "n", "key");
        String sig2 = SignatureUtils.sign(params, 100L, "n", "key");
        assertEquals(sig1, sig2);
    }
}
