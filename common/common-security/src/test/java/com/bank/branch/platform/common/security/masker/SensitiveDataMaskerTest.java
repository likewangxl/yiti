package com.bank.branch.platform.common.security.masker;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataMaskerTest {
    @Test void maskPhoneShouldWork() { assertEquals("138****5678", SensitiveDataMasker.maskPhone("13812345678")); }
    @Test void maskPhoneShortInput() { assertEquals("***", SensitiveDataMasker.maskPhone("123")); }
    @Test void maskPhoneNull() { assertEquals("***", SensitiveDataMasker.maskPhone(null)); }
    @Test void maskIdCardShouldWork() { assertEquals("110***********1234", SensitiveDataMasker.maskIdCard("110101199001011234")); }
    @Test void maskBankAccountShouldWork() { assertEquals("****7890", SensitiveDataMasker.maskBankAccount("6222021234567890")); }
    @Test void maskAmountShouldReturnStars() { assertEquals("***.**", SensitiveDataMasker.maskAmount(new BigDecimal("12345.67"))); }
    @Test void maskAmountNull() { assertEquals("***.**", SensitiveDataMasker.maskAmount(null)); }
}
