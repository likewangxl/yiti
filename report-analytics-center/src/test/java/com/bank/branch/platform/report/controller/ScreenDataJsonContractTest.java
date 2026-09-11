package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 运行时身份字段必须由 HTTP JSON 的整数 token 表达，禁止 Jackson 宽松字符串/浮点转换。 */
class ScreenDataJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runtimeIdentityFields_rejectStringAndFloatingPointJsonValues() {
        for (String body : new String[]{
                "{\"schemaVersion\":\"1\",\"screenCode\":\"SCR\",\"dsId\":12}",
                "{\"schemaVersion\":1.0,\"screenCode\":\"SCR\",\"dsId\":12}",
                "{\"schemaVersion\":2,\"screenCode\":\"SCR\",\"blockId\":\"11\"}",
                "{\"schemaVersion\":2,\"screenCode\":\"SCR\",\"blockId\":11.0}",
                "{\"schemaVersion\":1,\"screenCode\":\"SCR\",\"dsId\":\"12\"}",
                "{\"schemaVersion\":1,\"screenCode\":\"SCR\",\"dsId\":12.0}"
        }) {
            assertThatThrownBy(() -> objectMapper.readValue(body, ScreenDataReqDTO.class))
                    .as("body=%s", body)
                    .isInstanceOf(JsonProcessingException.class);
        }
    }

    @Test
    void batchId_isOptionalAndPassedThroughAsOpaqueString() throws Exception {
        ScreenDataReqDTO request = objectMapper.readValue(
                "{\"schemaVersion\":2,\"screenCode\":\"SCR\",\"blockId\":11,"
                        + "\"batchId\":\"RUN-20260710-01\"}", ScreenDataReqDTO.class);

        assertThat(request.getBatchId()).isEqualTo("RUN-20260710-01");
    }
}
