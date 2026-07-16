package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.config.AuthUserProperties;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock com.bank.branch.platform.auth.mapper.UserOrgMapper userOrgMapper;
    @Mock com.bank.branch.platform.auth.mapper.UserRoleMapper userRoleMapper;
    @Mock BCryptPasswordEncoder passwordEncoder;
    AuthUserProperties props = new AuthUserProperties();
    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, userOrgMapper, userRoleMapper, passwordEncoder, props);
    }

    // ---------- exportAllUsers ----------
    @Test
    void exportAllUsers_returnsAllUsersWithJoinedRoleNames_andForwardSemanticStatus() {
        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setUsername("10086");
        u.setUserchnname("张三");
        u.setEmail("z3@bank.com");
        u.setUserType("1");
        u.setIsEnabled(0);   // 0=启用
        u.setIsLocked(1);    // 1=锁定
        u.setRemark("备注X");

        when(userMapper.countByQuery(any())).thenReturn(1L);
        when(userMapper.selectByQuery(any(), eq(0), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.List.of(u));

        // 批量取角色（避免 N+1）：selectRolesByUserIds 返回 UserRoleItemDTO 列表
        com.bank.branch.platform.auth.api.dto.UserRoleItemDTO i1 = new com.bank.branch.platform.auth.api.dto.UserRoleItemDTO();
        i1.setUserId("E001"); i1.setRoleChName("系统管理员");
        com.bank.branch.platform.auth.api.dto.UserRoleItemDTO i2 = new com.bank.branch.platform.auth.api.dto.UserRoleItemDTO();
        i2.setUserId("E001"); i2.setRoleChName("资财部负责人");
        when(userRoleMapper.selectRolesByUserIds(java.util.List.of("E001")))
                .thenReturn(java.util.List.of(i1, i2));

        java.util.List<com.bank.branch.platform.auth.controller.dto.UserExportRow> rows =
                userService.exportAllUsers();

        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.getUsername()).isEqualTo("10086");
        assertThat(row.getUserchnname()).isEqualTo("张三");
        assertThat(row.getStatus()).isEqualTo("启用");
        assertThat(row.getLocked()).isEqualTo("锁定");
        assertThat(row.getRoles()).isEqualTo("系统管理员、资财部负责人");
        assertThat(row.getEmail()).isEqualTo("z3@bank.com");
        assertThat(row.getRemark()).isEqualTo("备注X");
    }

    // ---------- create ----------
    @Test
    void create_shouldInsertAndEncryptPassword() {
        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUserId("E001");
        req.setUsername("alice");
        req.setUserchnname("张三");
        req.setInitialPassword("Init@123");

        when(userMapper.selectByUserId("E001")).thenReturn(null);
        when(userMapper.countByUsername("alice")).thenReturn(0);
        when(passwordEncoder.encode("Init@123")).thenReturn("$2a$bcrypt$xxx");

        userService.create(req, "OPERATOR1");

        verify(passwordEncoder).encode("Init@123");
        verify(userMapper).insert(any(PtUser.class));
    }

    @Test
    void create_shouldThrowWhenUserIdDuplicate() {
        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUserId("E001");
        req.setUsername("alice");
        req.setUserchnname("张三");
        req.setInitialPassword("Init@123");

        PtUser existing = new PtUser();
        existing.setUserId("E001");
        when(userMapper.selectByUserId("E001")).thenReturn(existing);

        assertThatThrownBy(() -> userService.create(req, "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40904"));
    }

    @Test
    void create_shouldThrowWhenUsernameDuplicate() {
        UserCreateReqDTO req = new UserCreateReqDTO();
        req.setUserId("E002");
        req.setUsername("alice");
        req.setUserchnname("张三");
        req.setInitialPassword("Init@123");

        when(userMapper.selectByUserId("E002")).thenReturn(null);
        when(userMapper.countByUsername("alice")).thenReturn(1);

        assertThatThrownBy(() -> userService.create(req, "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40905"));
    }

    // ---------- existsByUsername ----------
    @Test
    void existsByUsername_shouldReturnTrueWhenCountGreaterThanZero() {
        when(userMapper.countByUsername("alice")).thenReturn(1);
        assertThat(userService.existsByUsername("alice")).isTrue();
    }

    @Test
    void existsByUsername_shouldReturnFalseWhenZero() {
        when(userMapper.countByUsername("bob")).thenReturn(0);
        assertThat(userService.existsByUsername("bob")).isFalse();
    }

    // ---------- getById ----------
    @Test
    void getById_shouldReturnDtoWithoutPwd() {
        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setUsername("alice");
        u.setUserchnname("张三");
        u.setPwd("$2a$bcrypt$xxx");
        when(userMapper.selectByUserId("E001")).thenReturn(u);

        UserDetailRespDTO dto = userService.getById("E001");

        assertThat(dto.getUserId()).isEqualTo("E001");
        assertThat(dto.getUsername()).isEqualTo("alice");
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(userMapper.selectByUserId(anyString())).thenReturn(null);
        assertThatThrownBy(() -> userService.getById("NONE"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }

    @Test
    void pageUsers_shouldReturnPageResult() {
        com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q =
                new com.bank.branch.platform.auth.api.dto.UserQueryReqDTO();
        q.setUsername("ali");
        q.setPageNo(1);
        q.setPageSize(10);

        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setUsername("alice");
        when(userMapper.selectByQuery(eq(q), eq(0), eq(10))).thenReturn(java.util.List.of(u));
        when(userMapper.countByQuery(eq(q))).thenReturn(1L);

        com.bank.branch.platform.common.web.PageResult<com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> page =
                userService.pageUsers(q);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getUserId()).isEqualTo("E001");
    }

    @Test
    void pageUsers_shouldClampPageSize() {
        com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q =
                new com.bank.branch.platform.auth.api.dto.UserQueryReqDTO();
        q.setPageNo(1);
        q.setPageSize(500);
        when(userMapper.selectByQuery(eq(q), eq(0), eq(100))).thenReturn(java.util.List.of());
        when(userMapper.countByQuery(eq(q))).thenReturn(0L);
        userService.pageUsers(q);
        verify(userMapper).selectByQuery(eq(q), eq(0), eq(100));
    }

    @Test
    void pageUsers_shouldFillDeptNameFromUserOrgJoin_andLeaveNullWhenNoMapping() {
        com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q =
                new com.bank.branch.platform.auth.api.dto.UserQueryReqDTO();
        q.setPageNo(1);
        q.setPageSize(20);

        PtUser u1 = new PtUser();
        u1.setUserId("E001");
        PtUser u2 = new PtUser();
        u2.setUserId("E002");
        when(userMapper.selectByQuery(eq(q), eq(0), eq(20))).thenReturn(java.util.List.of(u1, u2));
        when(userMapper.countByQuery(eq(q))).thenReturn(2L);

        // EXT_USER_ORG ⋈ EXT_ORG_INFO 批量查询：仅 E001 有机构归属
        com.bank.branch.platform.auth.api.dto.UserDeptNameDTO row =
                new com.bank.branch.platform.auth.api.dto.UserDeptNameDTO();
        row.setUserId("E001");
        row.setDeptName("公司业务部(绿色金融部)");
        when(userOrgMapper.selectDeptNamesByUserIds(java.util.List.of("E001", "E002")))
                .thenReturn(java.util.List.of(row));

        com.bank.branch.platform.common.web.PageResult<com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> page =
                userService.pageUsers(q);

        assertThat(page.getRecords().get(0).getDeptName()).isEqualTo("公司业务部(绿色金融部)");
        assertThat(page.getRecords().get(1).getDeptName()).isNull();
    }

    @Test
    void update_shouldSetUpdateFieldsAndCallMapper() {
        com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO();
        req.setUsername("alice2");
        req.setUserchnname("李四");

        PtUser existing = new PtUser();
        existing.setUserId("E001");
        existing.setPwd("$2a$old");
        when(userMapper.selectByUserId("E001")).thenReturn(existing);

        userService.update("E001", req, "OPERATOR1");

        org.mockito.ArgumentCaptor<PtUser> captor = org.mockito.ArgumentCaptor.forClass(PtUser.class);
        verify(userMapper).updateById(captor.capture());
        PtUser u = captor.getValue();
        assertThat(u.getUserId()).isEqualTo("E001");
        assertThat(u.getUsername()).isEqualTo("alice2");
        assertThat(u.getUserchnname()).isEqualTo("李四");
        assertThat(u.getUpdateAuthor()).isEqualTo("OPERATOR1");
        assertThat(u.getUpdateTime()).isNotNull();
        assertThat(u.getPwd()).isEqualTo("$2a$old");
    }

    @Test
    void update_shouldThrowWhenUserNotFound() {
        when(userMapper.selectByUserId("NONE")).thenReturn(null);
        com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req =
                new com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO();
        assertThatThrownBy(() -> userService.update("NONE", req, "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }

    @Test
    void deleteByIds_shouldCallMapperWithList() {
        when(userMapper.deleteByUserIds(java.util.List.of("E001","E002"))).thenReturn(2);
        int n = userService.deleteByIds(java.util.List.of("E001","E002"));
        assertThat(n).isEqualTo(2);
        verify(userMapper).deleteByUserIds(java.util.List.of("E001","E002"));
    }

    @Test
    void deleteByIds_shouldThrowWhenIdsEmpty() {
        assertThatThrownBy(() -> userService.deleteByIds(java.util.List.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }

    @Test
    void deleteByIds_shouldThrowWhenIdsExceedMaxBatch() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) ids.add("E" + i);
        assertThatThrownBy(() -> userService.deleteByIds(ids))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }

    @Test
    void resetPassword_shouldEncryptDefaultAndUpdateEach() {
        when(passwordEncoder.encode(props.getDefaultPassword())).thenReturn("$2a$bcrypt$default");
        when(userMapper.updatePassword(anyString(), anyString(), anyString())).thenReturn(1);

        int n = userService.resetPassword(java.util.List.of("E001", "E002"), "OPERATOR1");

        assertThat(n).isEqualTo(2);
        verify(userMapper).updatePassword("E001", "$2a$bcrypt$default", "OPERATOR1");
        verify(userMapper).updatePassword("E002", "$2a$bcrypt$default", "OPERATOR1");
    }

    @Test
    void resetPassword_shouldThrowWhenIdsInvalid() {
        assertThatThrownBy(() -> userService.resetPassword(java.util.List.of(), "OPERATOR1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }

    // ---------- changeMyPassword ----------
    @Test
    void changeMyPassword_shouldUpdateWhenOldPasswordMatches() {
        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setPwd("$2a$old");
        when(userMapper.selectByUserId("E001")).thenReturn(u);
        when(passwordEncoder.matches("oldPwd123", "$2a$old")).thenReturn(true);
        when(passwordEncoder.encode("newPwd456")).thenReturn("$2a$new");

        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("oldPwd123");
        req.setNewPassword("newPwd456");

        userService.changeMyPassword("E001", req);
        verify(userMapper).updatePassword("E001", "$2a$new", "E001");
    }

    @Test
    void changeMyPassword_shouldThrowWhenOldPasswordMismatch() {
        PtUser u = new PtUser();
        u.setUserId("E001");
        u.setPwd("$2a$old");
        when(userMapper.selectByUserId("E001")).thenReturn(u);
        when(passwordEncoder.matches("wrong", "$2a$old")).thenReturn(false);

        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("wrong");
        req.setNewPassword("newPwd456");

        assertThatThrownBy(() -> userService.changeMyPassword("E001", req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40001"));
    }

    @Test
    void changeMyPassword_shouldThrowWhenUserNotFound() {
        when(userMapper.selectByUserId("NONE")).thenReturn(null);
        com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req =
                new com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO();
        req.setOldPassword("o"); req.setNewPassword("n");
        assertThatThrownBy(() -> userService.changeMyPassword("NONE", req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40403"));
    }

    // ---------- batchActivate / batchInactivate / batchLock / batchUnlock ----------
    @Test
    void batchActivate_shouldSetIsEnabledZero() {
        when(userMapper.updateActiveStatus(anyString(), eq(0), anyString())).thenReturn(1);
        int n = userService.batchActivate(java.util.List.of("E001","E002"), "OPERATOR1");
        assertThat(n).isEqualTo(2);
        verify(userMapper).updateActiveStatus("E001", 0, "OPERATOR1");
        verify(userMapper).updateActiveStatus("E002", 0, "OPERATOR1");
    }

    @Test
    void batchInactivate_shouldSetIsEnabledOne() {
        when(userMapper.updateActiveStatus(anyString(), eq(1), anyString())).thenReturn(1);
        int n = userService.batchInactivate(java.util.List.of("E001"), "OPERATOR1");
        assertThat(n).isEqualTo(1);
        verify(userMapper).updateActiveStatus("E001", 1, "OPERATOR1");
    }

    @Test
    void batchLock_shouldCallUpdateLockedStatusWithOne() {
        when(userMapper.updateLockedStatus(anyString(), eq(1))).thenReturn(1);
        int n = userService.batchLock(java.util.List.of("E001","E002"), "OPERATOR1");
        assertThat(n).isEqualTo(2);
        verify(userMapper).updateLockedStatus("E001", 1);
        verify(userMapper).updateLockedStatus("E002", 1);
    }

    @Test
    void batchUnlock_shouldCallUpdateLockedStatusWithZero() {
        when(userMapper.updateLockedStatus(anyString(), eq(0))).thenReturn(1);
        int n = userService.batchUnlock(java.util.List.of("E001"), "OPERATOR1");
        assertThat(n).isEqualTo(1);
        verify(userMapper).updateLockedStatus("E001", 0);
    }

    @Test
    void batchActivate_shouldThrowWhenIdsInvalid() {
        assertThatThrownBy(() -> userService.batchActivate(java.util.List.of(), "OP"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40002"));
    }
}
