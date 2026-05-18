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
    @Mock BCryptPasswordEncoder passwordEncoder;
    AuthUserProperties props = new AuthUserProperties();
    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, passwordEncoder, props);
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
}
