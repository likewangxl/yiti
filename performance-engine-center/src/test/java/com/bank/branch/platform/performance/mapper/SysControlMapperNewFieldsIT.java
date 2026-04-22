package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sys_control 新增字段 IT（Task B3）.
 *
 * <p>验证 V1_0_3 补充的 5 个字段：remark, updated_by, publish_source, publish_by, publish_time
 * 能正确持久化和读取。
 */
class SysControlMapperNewFieldsIT extends PerformanceMapperTestBase {

    @Autowired
    private SysControlMapper mapper;

    @Test
    void insert_and_read_withNewFields() {
        SysControl sc = new SysControl();
        sc.setId("TEST_SC_B3");
        sc.setScopeDim("GLOBAL");
        sc.setLatestDataDate(LocalDate.of(2026, 4, 1));
        sc.setCurrentVersion("v20260401");
        sc.setIsValid(1);
        sc.setRemark("季度末切版");
        sc.setUpdatedBy("admin");
        sc.setPublishSource("MANUAL");
        sc.setPublishBy("admin");
        sc.setPublishTime(LocalDateTime.now());
        mapper.insert(sc);

        SysControl loaded = mapper.selectById("TEST_SC_B3");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getRemark()).isEqualTo("季度末切版");
        assertThat(loaded.getPublishSource()).isEqualTo("MANUAL");
        assertThat(loaded.getPublishBy()).isEqualTo("admin");
        assertThat(loaded.getPublishTime()).isNotNull();
        assertThat(loaded.getUpdatedBy()).isEqualTo("admin");
    }
}
