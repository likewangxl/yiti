package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadImportLocalStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketingLeadImportLocalStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReadsSourceFileUnderConfiguredLocalRoot() throws Exception {
        MarketingLeadImportLocalStorage storage = new MarketingLeadImportLocalStorage(tempDir.toString());
        byte[] content = "客户名称,统一社会信用代码\n客户甲,91320100ABC1234567\n"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "线索导入.csv", "text/csv", content);

        MarketingLeadImportLocalStorage.StoredFile stored = storage.saveSource(file);

        assertFalse(Path.of(stored.key()).isAbsolute());
        assertTrue(stored.key().startsWith("local:source/"));
        assertTrue(stored.key().endsWith(".csv"));
        assertEquals(32, stored.md5Hash().length());
        assertArrayEquals(content, storage.read(stored.key()));
        assertTrue(Files.isRegularFile(tempDir.resolve(stored.key().substring("local:".length()))));
    }

    @Test
    void savesGeneratedErrorCsvLocally() {
        MarketingLeadImportLocalStorage storage = new MarketingLeadImportLocalStorage(tempDir.toString());
        byte[] content = "行号,错误原因\n2,客户名称为空\n".getBytes(StandardCharsets.UTF_8);

        String key = storage.saveErrorCsv(content);

        assertTrue(key.startsWith("local:error/"));
        assertTrue(key.endsWith(".csv"));
        assertArrayEquals(content, storage.read(key));
    }

    @Test
    void rejectsPathTraversalWhenReadingLocalFile() {
        MarketingLeadImportLocalStorage storage = new MarketingLeadImportLocalStorage(tempDir.toString());

        BizException exception = assertThrows(BizException.class,
                () -> storage.read("local:../outside.csv"));

        assertEquals("MARKETING_LEAD_IMPORT_LOCAL_PATH_INVALID", exception.getCode());
        assertEquals("导入文件本地路径不合法", exception.getMessage());
    }
}
