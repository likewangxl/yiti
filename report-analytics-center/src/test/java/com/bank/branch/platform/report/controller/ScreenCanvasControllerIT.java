package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.BaseControllerIT;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 画布双态 Controller 端到端 IT(鉴权 Mock 放行,业务落真库). */
class ScreenCanvasControllerIT extends BaseControllerIT {

    @Autowired
    private RptScreenMapper screenMapper;

    private Long screenId;

    @BeforeEach
    void seed() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("TEST_SCR_E9");
        RptScreen s = new RptScreen();
        s.setScreenCode("TEST_SCR_CV_" + System.currentTimeMillis() % 100000);
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
}
