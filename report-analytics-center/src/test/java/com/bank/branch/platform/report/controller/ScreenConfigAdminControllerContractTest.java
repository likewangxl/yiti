package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 管理端创建、元数据与画布区块写入必须是三条独立契约。 */
class ScreenConfigAdminControllerContractTest {

    @Test
    void createAndMetadataUpdate_areSeparateBlockFreeEndpoints() throws Exception {
        Method create = ScreenConfigAdminController.class
                .getMethod("createScreen", ScreenCreateReqDTO.class);
        Method metadata = ScreenConfigAdminController.class
                .getMethod("updateMetadata", Long.class, ScreenMetadataUpdateReqDTO.class);

        assertThat(create.getAnnotation(PostMapping.class).value()).containsExactly("/screens");
        assertThat(metadata.getAnnotation(PutMapping.class).value()).containsExactly("/screens/{id}/metadata");

        assertMetadataOnly(ScreenCreateReqDTO.class);
        assertMetadataOnly(ScreenMetadataUpdateReqDTO.class);
        // 旧通用 DTO 即使仍被 Java 调用方编译引用，也不能再成为区块写入口。
        assertBlockAndRoleFree(ScreenSaveReqDTO.class);
    }

    private void assertMetadataOnly(Class<?> dtoType) {
        Set<String> fieldNames = Arrays.stream(dtoType.getDeclaredFields())
                .map(field -> field.getName()).collect(Collectors.toSet());
        assertThat(fieldNames).doesNotContain("id", "blocks", "allowedRoleCodes", "accessRoleCodes", "accessRoles");
    }

    private void assertBlockAndRoleFree(Class<?> dtoType) {
        Set<String> fieldNames = Arrays.stream(dtoType.getDeclaredFields())
                .map(field -> field.getName()).collect(Collectors.toSet());
        assertThat(fieldNames).doesNotContain("blocks", "allowedRoleCodes", "accessRoleCodes", "accessRoles");
    }
}
