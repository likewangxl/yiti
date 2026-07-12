package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 画布双态 Controller 端到端 IT(鉴权 Mock 放行,业务落真库). */
class ScreenCanvasControllerIT extends BaseControllerIT {

    @Autowired
    private RptScreenMapper screenMapper;
    @Autowired
    private RptScreenPublishLogMapper publishLogMapper;
    @Autowired
    private RptScreenMapPointMapper pointMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long screenId;
    private String screenCode;
    /** 单测过程中额外建的屏(如 PROVINCE 点位测试屏),清理时一并处理归档/自身 */
    private final List<Long> extraScreenIds = new ArrayList<>();

    @BeforeEach
    void seed() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("TEST_SCR_E9");
        RptScreen s = new RptScreen();
        screenCode = "TEST_SCR_CV_" + System.currentTimeMillis() % 100000;
        s.setScreenCode(screenCode);
        s.setScreenName("TEST_SCR_画布屏");
        s.setViewLevel("BRANCH");
        s.setStatus("ACTIVE");
        s.setCanvasVersion(0);
        s.setPublishStatus(0);
        screenMapper.insert(s);
        screenId = s.getId();
    }

    @AfterEach
    void cleanup() {
        // 发布归档先按 screenId 清理(无 name 字段可 like,需显式收集 id)
        publishLogMapper.delete(new LambdaQueryWrapper<RptScreenPublishLog>()
                .eq(RptScreenPublishLog::getScreenId, screenId));
        for (Long id : extraScreenIds) {
            publishLogMapper.delete(new LambdaQueryWrapper<RptScreenPublishLog>()
                    .eq(RptScreenPublishLog::getScreenId, id));
        }
        pointMapper.delete(new LambdaQueryWrapper<RptScreenMapPoint>()
                .likeRight(RptScreenMapPoint::getOrgCode, "TEST_SCR_"));
        screenMapper.delete(new LambdaQueryWrapper<RptScreen>()
                .likeRight(RptScreen::getScreenName, "TEST_SCR_"));
    }

    @Test
    void saveThenLoad_roundTrips_andBumpsVersion() throws Exception {
        String body = """
                {"screenId":%d,"expectedVersion":0,
                 "canvasStyle":{"schemaVersion":1,"adaptor":"keepProportion"},
                 "components":[{"id":"w-1","component":"TextLabel",
                    "style":{"top":10,"left":10,"width":200,"height":48},
                    "propValue":{"text":"TEST_SCR"}}]}
                """.formatted(screenId);
        mvc.perform(post("/api/screen/admin/canvas/save")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.canvasVersion").value(1));

        mvc.perform(get("/api/screen/admin/canvas/" + screenId))
                .andExpect(jsonPath("$.data.canvasVersion").value(1))
                .andExpect(jsonPath("$.data.canvasDraftJson").value(
                        org.hamcrest.Matchers.containsString("TextLabel")));
    }

    @Test
    void save_staleVersion_returns43012() throws Exception {
        // 先保存一次到 version=1,再用 expectedVersion=0 重复保存 → 冲突
        String body0 = """
                {"screenId":%d,"expectedVersion":0,"canvasStyle":{"schemaVersion":1},
                 "components":[]}""".formatted(screenId);
        mvc.perform(post("/api/screen/admin/canvas/save")
                .contentType(MediaType.APPLICATION_JSON).content(body0));
        mvc.perform(post("/api/screen/admin/canvas/save")
                        .contentType(MediaType.APPLICATION_JSON).content(body0))
                .andExpect(jsonPath("$.code").value("RPT-43012"));
    }

    @Test
    void load_unknownScreen_returns43004() throws Exception {
        mvc.perform(get("/api/screen/admin/canvas/999999999"))
                .andExpect(jsonPath("$.code").value("RPT-43004"));
    }

    @Test
    void publish_bumpsStatusAndArchivesLog() throws Exception {
        String saveBody = """
                {"screenId":%d,"expectedVersion":0,"canvasStyle":{"schemaVersion":1},
                 "components":[{"id":"w-1","component":"TextLabel",
                    "style":{"top":10,"left":10,"width":200,"height":48},
                    "propValue":{"text":"PUBLISH_MARK_V1"}}]}
                """.formatted(screenId);
        mvc.perform(post("/api/screen/admin/canvas/save")
                .contentType(MediaType.APPLICATION_JSON).content(saveBody));

        String publishBody = """
                {"screenId":%d,"expectedVersion":1}""".formatted(screenId);
        mvc.perform(post("/api/screen/admin/canvas/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(publishBody))
                .andExpect(jsonPath("$.code").value("0"));

        // 状态流转:publishStatus=1(已发布)
        mvc.perform(get("/api/screen/admin/canvas/" + screenId))
                .andExpect(jsonPath("$.data.publishStatus").value(1));

        // 归档有行 + 发布态渲染包非空且含预期组件
        mvc.perform(get("/api/screen/admin/canvas/" + screenId + "/publish-logs"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].publishedBy").value("TEST_SCR_E9"));
        mvc.perform(get("/api/screen/view/" + screenCode))
                .andExpect(jsonPath("$.data.state").value("published"))
                .andExpect(jsonPath("$.data.renderPackageJson").value(
                        org.hamcrest.Matchers.containsString("PUBLISH_MARK_V1")));
    }

    @Test
    void rollback_toEarlierLog_restoresOldPublishedContent() throws Exception {
        // 第一次发布:ROLLBACK_MARK_V1
        saveDraft("ROLLBACK_MARK_V1", 0);
        publish(1);
        // 第二次发布:ROLLBACK_MARK_V2(覆盖发布态)
        saveDraft("ROLLBACK_MARK_V2", 1);
        publish(2);

        // 归档倒序:index0=V2 的日志,index1=V1 的日志
        MvcResult logsResult = mvc.perform(get("/api/screen/admin/canvas/" + screenId + "/publish-logs"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).path("data");
        long earlierLogId = logs.get(1).path("id").asLong();

        String rollbackBody = """
                {"screenId":%d,"publishLogId":%d}""".formatted(screenId, earlierLogId);
        mvc.perform(post("/api/screen/admin/canvas/rollback")
                        .contentType(MediaType.APPLICATION_JSON).content(rollbackBody))
                .andExpect(jsonPath("$.code").value("0"));

        // 发布态已回滚为 V1 内容,不再是 V2
        mvc.perform(get("/api/screen/view/" + screenCode))
                .andExpect(jsonPath("$.data.renderPackageJson").value(
                        org.hamcrest.Matchers.containsString("ROLLBACK_MARK_V1")))
                .andExpect(jsonPath("$.data.renderPackageJson").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ROLLBACK_MARK_V2"))));
    }

    @Test
    void discard_afterDraftDiverges_revertsDraftToPublishedContent() throws Exception {
        // 发布态:DISCARD_PUBLISHED_MARK
        saveDraft("DISCARD_PUBLISHED_MARK", 0);
        publish(1);
        // 草稿态继续改动,与发布态分叉
        saveDraft("DISCARD_DRAFT_MARK", 1);

        String discardBody = """
                {"screenId":%d}""".formatted(screenId);
        mvc.perform(post("/api/screen/admin/canvas/discard")
                        .contentType(MediaType.APPLICATION_JSON).content(discardBody))
                .andExpect(jsonPath("$.code").value("0"));

        // 草稿被发布态组件树覆盖:含发布内容,不再含分叉的草稿内容
        mvc.perform(get("/api/screen/admin/canvas/" + screenId))
                .andExpect(jsonPath("$.data.canvasDraftJson").value(
                        org.hamcrest.Matchers.containsString("DISCARD_PUBLISHED_MARK")))
                .andExpect(jsonPath("$.data.canvasDraftJson").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DISCARD_DRAFT_MARK"))));
    }

    @Test
    void viewDraft_forProvinceScreen_returnsDraftStateWithComponentsAndMapPoints() throws Exception {
        // PROVINCE 屏 + 一个 ACTIVE 点位,验证 getRenderByCode 回填 mapPoints(Important-2 修复点)
        RptScreen province = new RptScreen();
        String provinceCode = "TEST_SCR_PV_" + System.currentTimeMillis() % 100000;
        province.setScreenCode(provinceCode);
        province.setScreenName("TEST_SCR_省屏");
        province.setViewLevel("PROVINCE");
        province.setStatus("ACTIVE");
        province.setCanvasVersion(0);
        province.setPublishStatus(0);
        screenMapper.insert(province);
        extraScreenIds.add(province.getId());

        String orgCode = "TEST_SCR_ORG_" + System.currentTimeMillis() % 100000;
        RptScreenMapPoint point = new RptScreenMapPoint();
        point.setOrgCode(orgCode);
        point.setOrgName("TEST_SCR_测试支行");
        point.setLng(new BigDecimal("108.9"));
        point.setLat(new BigDecimal("34.3"));
        point.setStatus("ACTIVE");
        pointMapper.insert(point);

        String saveBody = """
                {"screenId":%d,"expectedVersion":0,"canvasStyle":{"schemaVersion":1},
                 "components":[{"id":"w-1","component":"TextLabel",
                    "style":{"top":10,"left":10,"width":200,"height":48},
                    "propValue":{"text":"DRAFT_PREVIEW_MARK"}}]}
                """.formatted(province.getId());
        mvc.perform(post("/api/screen/admin/canvas/save")
                .contentType(MediaType.APPLICATION_JSON).content(saveBody));

        mvc.perform(get("/api/screen/view/" + provinceCode).param("preview", "draft"))
                .andExpect(jsonPath("$.data.state").value("draft"))
                .andExpect(jsonPath("$.data.renderPackageJson").value(
                        org.hamcrest.Matchers.containsString("DRAFT_PREVIEW_MARK")))
                .andExpect(jsonPath("$.data.mapPoints.length()").value(1))
                .andExpect(jsonPath("$.data.mapPoints[0].orgCode").value(orgCode));
    }

    // ===== 内部辅助:发布/回滚/放弃草稿测试共用 =====

    private void saveDraft(String markText, int expectedVersion) throws Exception {
        String body = """
                {"screenId":%d,"expectedVersion":%d,"canvasStyle":{"schemaVersion":1},
                 "components":[{"id":"w-1","component":"TextLabel",
                    "style":{"top":10,"left":10,"width":200,"height":48},
                    "propValue":{"text":"%s"}}]}
                """.formatted(screenId, expectedVersion, markText);
        mvc.perform(post("/api/screen/admin/canvas/save")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void publish(int expectedVersion) throws Exception {
        String body = """
                {"screenId":%d,"expectedVersion":%d}""".formatted(screenId, expectedVersion);
        mvc.perform(post("/api/screen/admin/canvas/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value("0"));
    }
}
