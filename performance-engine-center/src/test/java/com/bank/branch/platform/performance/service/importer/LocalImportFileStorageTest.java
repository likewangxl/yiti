package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocalImportFileStorage 单元测试（2026-06-17，导入源文件改存后端本地目录、不走 OBS）.
 */
class LocalImportFileStorageTest {

    @TempDir
    Path tempDir;

    private MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    @Test
    @DisplayName("save → 落本地目录返回 key；read(key) 取回同样字节")
    void saveThenRead_roundTrip() {
        LocalImportFileStorage store = new LocalImportFileStorage(tempDir.toString());
        byte[] content = {1, 2, 3, 4, 5};

        String key = store.save(file("kpi.xlsx", content));

        assertThat(key).isNotBlank();
        // 文件真的落在了本地目录下
        assertThat(Files.exists(tempDir.resolve(key))).isTrue();
        // 读回字节一致
        assertThat(store.read(key)).containsExactly(content);
    }

    @Test
    @DisplayName("save 保留原文件扩展名")
    void save_keepsExtension() {
        LocalImportFileStorage store = new LocalImportFileStorage(tempDir.toString());
        String key = store.save(file("目标方案.xlsx", new byte[]{9}));
        assertThat(key).endsWith(".xlsx");
    }

    @Test
    @DisplayName("read 不存在的 key → IMPORT_BATCH_NO_SOURCE_FILE")
    void read_missing_throwsNoSourceFile() {
        LocalImportFileStorage store = new LocalImportFileStorage(tempDir.toString());
        assertThatThrownBy(() -> store.read("20260101/nope.xlsx"))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_BATCH_NO_SOURCE_FILE));
    }

    @Test
    @DisplayName("read 路径穿越 key（../）→ 拒绝")
    void read_pathTraversal_rejected() {
        LocalImportFileStorage store = new LocalImportFileStorage(tempDir.toString());
        assertThatThrownBy(() -> store.read("../../etc/passwd"))
                .isInstanceOf(PerfException.class);
    }
}
