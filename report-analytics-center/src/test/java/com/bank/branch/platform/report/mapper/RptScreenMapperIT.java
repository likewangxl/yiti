package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.ReportTestApplication;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大屏 4 张配置表 Mapper 集成测试（真库 onepl_test_bootstrap，事务回滚）.
 */
@SpringBootTest(classes = ReportTestApplication.class)
@ActiveProfiles("test")
@Transactional
class RptScreenMapperIT {

    @Autowired private RptScreenDatasourceMapper dsMapper;
    @Autowired private RptScreenMapper screenMapper;
    @Autowired private RptScreenBlockMapper blockMapper;
    @Autowired private RptScreenMapPointMapper pointMapper;

    @Test
    void datasource_insertAndSelect_roundTrip() {
        RptScreenDatasource ds = new RptScreenDatasource();
        ds.setDsCode("TEST_SCR_DS_1");
        ds.setDsName("测试数据源");
        ds.setDsType("TIMESERIES");
        ds.setSourceKind("KPI_RESULT");
        ds.setConfigJson("{\"cycleType\":\"MONTHLY\"}");
        ds.setStatus("ACTIVE");
        dsMapper.insert(ds);

        assertThat(ds.getId()).isNotNull(); // AUTO 回填
        RptScreenDatasource got = dsMapper.selectById(ds.getId());
        assertThat(got.getDsCode()).isEqualTo("TEST_SCR_DS_1");
        assertThat(got.getDeleted()).isZero();
    }

    @Test
    void screenAndBlock_insert_thenCountByDsId() {
        RptScreen s = new RptScreen();
        s.setScreenCode("TEST_SCR_S_1");
        s.setScreenName("测试屏");
        s.setViewLevel("BRANCH");
        s.setStatus("ACTIVE");
        screenMapper.insert(s);

        RptScreenBlock b = new RptScreenBlock();
        b.setScreenId(s.getId());
        b.setRegion("LEFT");
        b.setRowNo(1);
        b.setColNo(1);
        b.setWidthPct(100);
        b.setHeightPct(50);
        b.setComponentType("METRIC_CARD");
        b.setBindJson("{\"dsId\":987654321}");
        blockMapper.insert(b);

        assertThat(blockMapper.countByDsId(987654321L)).isEqualTo(1);
        assertThat(blockMapper.countByDsId(111L)).isZero();
    }

    @Test
    void mapPoint_insertAndSelect_roundTrip() {
        RptScreenMapPoint p = new RptScreenMapPoint();
        p.setOrgCode("TEST_SCR_ORG1");
        p.setOrgName("测试支行");
        p.setLng(new BigDecimal("108.948024"));
        p.setLat(new BigDecimal("34.263161"));
        p.setTargetScreenCode("SCR_BRANCH");
        p.setStatus("ACTIVE");
        pointMapper.insert(p);

        RptScreenMapPoint got = pointMapper.selectById(p.getId());
        assertThat(got.getLng()).isEqualByComparingTo("108.948024");
    }
}
