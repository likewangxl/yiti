package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.DocumentDTO;
import com.bank.branch.platform.portal.entity.DocInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentConverter 单元测试
 * <p>纯 POJO 转换，不需要 Spring 上下文。</p>
 */
class DocumentConverterTest {

    @Test
    @DisplayName("toDTO: null 输入 -> 返回 null")
    void toDTOShouldReturnNullOnNullInput() {
        assertNull(DocumentConverter.toDTO(null));
    }

    @Test
    @DisplayName("toDTO: 全量字段映射验证（8 个字段）")
    void toDTOShouldMapAllFields() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 4, 13, 10, 0, 0);
        DocInfo entity = new DocInfo();
        entity.setId("doc-001");
        entity.setDocTitle("产品操作手册");
        entity.setDocCategory("OPERATION");
        entity.setFileObjectId("file-obj-001");
        entity.setStatus("ACTIVE");
        entity.setUpdatedTime(now);

        // when
        DocumentDTO dto = DocumentConverter.toDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("doc-001", dto.getId());
        assertEquals("产品操作手册", dto.getDocTitle());
        assertEquals("OPERATION", dto.getDocCategory());
        assertNull(dto.getDocCategoryDesc(), "docCategoryDesc 应由 Service 层填充");
        assertEquals("file-obj-001", dto.getFileObjectId());
        assertNull(dto.getFileName(), "fileName 应由 Service 层填充");
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(now, dto.getUpdatedTime());
    }

    @Test
    @DisplayName("toDTO: Entity 额外字段（createdBy 等）不影响转换")
    void toDTOShouldIgnoreExtraEntityFields() {
        // given - 设置 DocumentDTO 不包含的字段
        DocInfo entity = new DocInfo();
        entity.setId("doc-002");
        entity.setDocTitle("风控指引");
        entity.setDocCategory("RISK");
        entity.setStatus("DISABLED");
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("admin");

        // when
        DocumentDTO dto = DocumentConverter.toDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("doc-002", dto.getId());
        assertEquals("风控指引", dto.getDocTitle());
        assertEquals("DISABLED", dto.getStatus());
    }
}
