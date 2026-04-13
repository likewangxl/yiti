package com.bank.branch.platform.portal.convert;

import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import com.bank.branch.platform.portal.controller.dto.addrbook.EmployeeSearchDTO;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmployeeConverter 单元测试
 * <p>纯 POJO 转换，不需要 Spring 上下文。</p>
 */
class EmployeeConverterTest {

    @Test
    @DisplayName("toDTO: null 输入 -> 返回 null")
    void toDTOShouldReturnNullOnNullInput() {
        assertNull(EmployeeConverter.toDTO(null));
    }

    @Test
    @DisplayName("toDTO: 全量字段映射验证（12 个字段）")
    void toDTOShouldMapAllFields() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 4, 13, 10, 0, 0);
        AddrbookEmployee entity = new AddrbookEmployee();
        entity.setEmpId("E10001");
        entity.setEmpName("张三");
        entity.setMobile("13812345678");
        entity.setEmail("zhangsan@bank.com");
        entity.setOrgCode("ORG_SZ_001");
        entity.setOrgName("深圳分行");
        entity.setPosition("RM");
        entity.setSelfDesc("资深客户经理");
        entity.setResponsibleProductIds(List.of("prod-001", "prod-002"));
        entity.setStatus("ACTIVE");
        entity.setUpdatedTime(now);

        // when
        EmployeeDTO dto = EmployeeConverter.toDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("E10001", dto.getEmpId());
        assertEquals("张三", dto.getEmpName());
        assertEquals("138****5678", dto.getMobile(), "手机号应脱敏");
        assertEquals("zhangsan@bank.com", dto.getEmail());
        assertEquals("ORG_SZ_001", dto.getOrgCode());
        assertEquals("深圳分行", dto.getOrgName());
        assertEquals("RM", dto.getPosition());
        assertNull(dto.getPositionDesc(), "positionDesc 应由 Service 层填充");
        assertEquals("资深客户经理", dto.getSelfDesc());
        assertEquals(List.of("prod-001", "prod-002"), dto.getResponsibleProductIds());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(now, dto.getUpdatedTime());
    }

    @Test
    @DisplayName("toDTO: 手机号脱敏 - 标准 11 位手机号")
    void toDTOShouldMaskMobile() {
        AddrbookEmployee entity = new AddrbookEmployee();
        entity.setEmpId("E10002");
        entity.setMobile("13899998888");

        EmployeeDTO dto = EmployeeConverter.toDTO(entity);

        assertEquals("138****8888", dto.getMobile());
    }

    @Test
    @DisplayName("toDTO: 手机号脱敏 - null 手机号原样返回")
    void toDTOShouldHandleNullMobile() {
        AddrbookEmployee entity = new AddrbookEmployee();
        entity.setEmpId("E10003");
        entity.setMobile(null);

        EmployeeDTO dto = EmployeeConverter.toDTO(entity);

        assertNull(dto.getMobile());
    }

    @Test
    @DisplayName("toDTO: 手机号脱敏 - 短手机号原样返回")
    void toDTOShouldHandleShortMobile() {
        AddrbookEmployee entity = new AddrbookEmployee();
        entity.setEmpId("E10004");
        entity.setMobile("123456");

        EmployeeDTO dto = EmployeeConverter.toDTO(entity);

        assertEquals("123456", dto.getMobile());
    }

    @Test
    @DisplayName("toSearchDTO: null 输入 -> 返回 null")
    void toSearchDTOShouldReturnNullOnNullInput() {
        assertNull(EmployeeConverter.toSearchDTO(null));
    }

    @Test
    @DisplayName("toSearchDTO: 5 个字段全量映射验证")
    void toSearchDTOShouldMapAllFields() {
        // given
        AddrbookEmployee entity = new AddrbookEmployee();
        entity.setEmpId("E10001");
        entity.setEmpName("张三");
        entity.setOrgCode("ORG_SZ_001");
        entity.setOrgName("深圳分行");
        entity.setPosition("RM");

        // when
        EmployeeSearchDTO dto = EmployeeConverter.toSearchDTO(entity);

        // then
        assertNotNull(dto);
        assertEquals("E10001", dto.getEmpId());
        assertEquals("张三", dto.getEmpName());
        assertEquals("ORG_SZ_001", dto.getOrgCode());
        assertEquals("深圳分行", dto.getOrgName());
        assertEquals("RM", dto.getPosition());
    }

    @Test
    @DisplayName("maskMobile: 边界场景验证")
    void maskMobileBoundary() {
        assertNull(EmployeeConverter.maskMobile(null));
        assertEquals("123456", EmployeeConverter.maskMobile("123456"));
        assertEquals("123****7890", EmployeeConverter.maskMobile("1234567890"));
    }
}
