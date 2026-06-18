package com.bank.branch.platform.portal.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.addrbook.AddrbookImportRow;
import com.bank.branch.platform.portal.entity.AddrbookEmployee;
import com.bank.branch.platform.portal.mapper.AddrbookEmployeeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AddrbookImportService 单元测试（纯 Mockito，不启 Spring 上下文）。
 *
 * <p>用 EasyExcel.write 在内存造 xlsx 字节流作为 MockMultipartFile 输入，验证：
 * 全行合法 → upsert；任一行非法（工号不存在/已禁用/机构歧义）→ 整批不入库并抛错。</p>
 */
class AddrbookImportServiceTest {

    private AddrbookEmployeeMapper mapper;
    private UserApi userApi;
    private OrgApi orgApi;
    private CurrentUserApi currentUserApi;
    private AddrbookImportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AddrbookEmployeeMapper.class);
        userApi = mock(UserApi.class);
        orgApi = mock(OrgApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.getCurrentEmpId()).thenReturn("admin");
        service = new AddrbookImportService(mapper, userApi, orgApi, currentUserApi);
    }

    private UserDTO user(String empId, String name, boolean enabled) {
        UserDTO u = new UserDTO();
        u.setUsername(empId);
        u.setDisplayName(name);
        u.setEnabled(enabled);
        return u;
    }

    private OrgDTO org(String code, String name) {
        OrgDTO o = new OrgDTO();
        o.setOrgCode(code);
        o.setOrgName(name);
        return o;
    }

    private MultipartFile excel(List<AddrbookImportRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, AddrbookImportRow.class).sheet("通讯录").doWrite(rows);
        return new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private AddrbookImportRow row(String empId, String orgName) {
        AddrbookImportRow r = new AddrbookImportRow();
        r.setEmpId(empId);
        r.setOrgName(orgName);
        r.setPosition("客户经理");
        r.setMobile("13800000000");
        return r;
    }

    @Test
    @DisplayName("全部合法 → upsert：新建走 insert、已存在走 updateById，姓名以 PT_USER 为准")
    void allValid_upserts() {
        List<AddrbookImportRow> rows = List.of(row("E1", "南山支行"), row("E2", "南山支行"));
        when(userApi.getUsersByUsernames(any())).thenReturn(List.of(user("E1", "张三", true), user("E2", "李四", true)));
        when(orgApi.getOrgsByNames(any())).thenReturn(List.of(org("ORG_NS", "南山支行")));
        when(mapper.selectByEmpId("E1")).thenReturn(null);                 // 新建
        AddrbookEmployee exist = new AddrbookEmployee(); exist.setEmpId("E2");
        when(mapper.selectByEmpId("E2")).thenReturn(exist);               // 更新

        int n = service.importExcel(excel(rows));

        assertThat(n).isEqualTo(2);
        verify(mapper).insert(any(AddrbookEmployee.class));
        verify(mapper).updateById(any(AddrbookEmployee.class));
    }

    @Test
    @DisplayName("某行工号已禁用 → 整批不入库并抛错（全或无）")
    void disabledUser_atomicFail() {
        List<AddrbookImportRow> rows = List.of(row("E1", "南山支行"), row("E2", "南山支行"));
        when(userApi.getUsersByUsernames(any())).thenReturn(List.of(user("E1", "张三", true), user("E2", "李四", false)));
        when(orgApi.getOrgsByNames(any())).thenReturn(List.of(org("ORG_NS", "南山支行")));

        assertThatThrownBy(() -> service.importExcel(excel(rows)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已禁用");
        verify(mapper, never()).insert(any(AddrbookEmployee.class));
        verify(mapper, never()).updateById(any(AddrbookEmployee.class));
    }

    @Test
    @DisplayName("某行工号不存在 → 整批不入库并抛错")
    void unknownUser_atomicFail() {
        List<AddrbookImportRow> rows = List.of(row("E1", "南山支行"), row("E9", "南山支行"));
        when(userApi.getUsersByUsernames(any())).thenReturn(List.of(user("E1", "张三", true)));
        when(orgApi.getOrgsByNames(any())).thenReturn(List.of(org("ORG_NS", "南山支行")));

        assertThatThrownBy(() -> service.importExcel(excel(rows)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
        verify(mapper, never()).insert(any(AddrbookEmployee.class));
    }

    @Test
    @DisplayName("机构名称重名 → 歧义报错、不入库")
    void ambiguousOrg_atomicFail() {
        List<AddrbookImportRow> rows = List.of(row("E1", "营业部"));
        when(userApi.getUsersByUsernames(any())).thenReturn(List.of(user("E1", "张三", true)));
        when(orgApi.getOrgsByNames(any())).thenReturn(List.of(org("ORG_A", "营业部"), org("ORG_B", "营业部")));

        assertThatThrownBy(() -> service.importExcel(excel(rows)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重复");
        verify(mapper, never()).insert(any(AddrbookEmployee.class));
    }

    @Test
    @DisplayName("空文件 → 抛错")
    void emptyFile_fails() {
        assertThatThrownBy(() -> service.importExcel(excel(new ArrayList<>())))
                .isInstanceOf(BizException.class);
        verify(mapper, never()).insert(any(AddrbookEmployee.class));
    }
}
