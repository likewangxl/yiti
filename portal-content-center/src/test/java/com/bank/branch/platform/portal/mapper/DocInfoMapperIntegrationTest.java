package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.DocInfo;
import com.bank.branch.platform.portal.support.AbstractMapperIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocInfoMapper 集成测试。
 * <p>
 * 直连本地 MySQL（127.0.0.1:3306/onepl），每个测试方法前清理 TEST_ 前缀数据。
 * 验证文档 CRUD、分页查询、分类过滤等核心 SQL 的正确性。
 * </p>
 */
@Sql(scripts = "/sql/clean-doc-info.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DocInfoMapperIntegrationTest extends AbstractMapperIntegrationTest {

    @Autowired
    private DocInfoMapper mapper;

    /**
     * 验证插入和按 ID 查询的完整往返。
     */
    @Test
    void insertAndSelectById() {
        // Arrange
        DocInfo doc = newDoc("TEST_产品说明书", "PRODUCT_DOC");

        // Act
        int rows = mapper.insert(doc);
        DocInfo found = mapper.selectById(doc.getId());

        // Assert
        assertThat(rows).isEqualTo(1);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(doc.getId());
        assertThat(found.getDocTitle()).isEqualTo("TEST_产品说明书");
        assertThat(found.getDocCategory()).isEqualTo("PRODUCT_DOC");
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
    }

    /**
     * 验证 softDeleteById 将状态设为 DISABLED。
     */
    @Test
    void softDelete() {
        // Arrange
        DocInfo doc = newDoc("TEST_待停用文档", "GENERAL");
        mapper.insert(doc);

        // Act
        int rows = mapper.softDeleteById(doc.getId(), "admin");

        // Assert
        assertThat(rows).isEqualTo(1);
        DocInfo found = mapper.selectById(doc.getId());
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("DISABLED");

        // 再次 softDelete 应返回 0（已经是 DISABLED）
        int rows2 = mapper.softDeleteById(doc.getId(), "admin");
        assertThat(rows2).isEqualTo(0);
    }

    /**
     * 验证 selectPage 的多条件过滤功能。
     */
    @Test
    void selectPage_withFilters() {
        // Arrange - 插入多条不同分类和状态的文档
        mapper.insert(newDoc("TEST_产品手册A", "PRODUCT_DOC"));
        mapper.insert(newDoc("TEST_产品手册B", "PRODUCT_DOC"));
        mapper.insert(newDocWithStatus("TEST_运营指南", "OPERATION_DOC", "ACTIVE"));
        mapper.insert(newDocWithStatus("TEST_已停用文档", "PRODUCT_DOC", "DISABLED"));

        // Act - 按分类 PRODUCT_DOC + 状态 ACTIVE 查询
        List<DocInfo> result = mapper.selectPage(null, "PRODUCT_DOC", "ACTIVE", 0, 10);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(d -> "PRODUCT_DOC".equals(d.getDocCategory()));
        assertThat(result).allMatch(d -> "ACTIVE".equals(d.getStatus()));

        // 按关键词查询
        List<DocInfo> keywordResult = mapper.selectPage("手册", null, null, 0, 10);
        assertThat(keywordResult).hasSize(2);
        assertThat(keywordResult).allMatch(d -> d.getDocTitle().contains("手册"));

        // 验证 countPage
        long count = mapper.countPage(null, "PRODUCT_DOC", "ACTIVE");
        assertThat(count).isEqualTo(2);
    }

    /**
     * 验证 listActiveByCategory 只返回指定分类且 ACTIVE 的文档。
     */
    @Test
    void listActiveByCategory() {
        // Arrange
        mapper.insert(newDoc("TEST_产品A", "PRODUCT_DOC"));
        mapper.insert(newDoc("TEST_产品B", "PRODUCT_DOC"));
        mapper.insert(newDocWithStatus("TEST_产品C停用", "PRODUCT_DOC", "DISABLED"));
        mapper.insert(newDoc("TEST_运营D", "OPERATION_DOC"));

        // Act
        List<DocInfo> result = mapper.listActiveByCategory("PRODUCT_DOC");

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(d -> "PRODUCT_DOC".equals(d.getDocCategory()));
        assertThat(result).allMatch(d -> "ACTIVE".equals(d.getStatus()));
    }

    /**
     * 创建测试文档辅助方法。
     *
     * @param title    文档标题
     * @param category 文档分类
     * @return 预填充的文档实体
     */
    private DocInfo newDoc(String title, String category) {
        return newDocWithStatus(title, category, "ACTIVE");
    }

    /**
     * 创建指定状态的测试文档辅助方法。
     *
     * @param title    文档标题
     * @param category 文档分类
     * @param status   状态
     * @return 预填充的文档实体
     */
    private DocInfo newDocWithStatus(String title, String category, String status) {
        DocInfo doc = new DocInfo();
        doc.setId(UUID.randomUUID().toString().replace("-", ""));
        doc.setDocTitle(title);
        doc.setDocCategory(category);
        doc.setFileObjectId("FILE_" + UUID.randomUUID().toString().substring(0, 8));
        doc.setStatus(status);
        doc.setCreatedBy("tester");
        doc.setCreatedTime(LocalDateTime.now());
        return doc;
    }
}
