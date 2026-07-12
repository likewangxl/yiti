package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** RPT_SCREEN_PUBLISH_LOG Mapper IT(MyBatis-Plus BaseMapper 落真库 onepl_test_bootstrap). */
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
class RptScreenPublishLogMapperIT {

    private static final Long SCREEN_ID = -998877L; // 负值测试屏,避开真实数据

    @Autowired
    private RptScreenPublishLogMapper mapper;

    @AfterEach
    void cleanup() {
        mapper.delete(new LambdaQueryWrapper<RptScreenPublishLog>()
                .eq(RptScreenPublishLog::getScreenId, SCREEN_ID));
    }

    @Test
    void insertThenSelectByScreen_roundTrips() {
        RptScreenPublishLog log = new RptScreenPublishLog();
        log.setScreenId(SCREEN_ID);
        log.setSnapshotJson("{\"schemaVersion\":1,\"components\":[]}");
        log.setPublishedBy("TEST_SCR_E9");
        log.setPublishedAt(LocalDateTime.now());
        mapper.insert(log);

        assertThat(log.getId()).isNotNull();
        var rows = mapper.selectList(new LambdaQueryWrapper<RptScreenPublishLog>()
                .eq(RptScreenPublishLog::getScreenId, SCREEN_ID));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSnapshotJson()).contains("schemaVersion");
    }
}
