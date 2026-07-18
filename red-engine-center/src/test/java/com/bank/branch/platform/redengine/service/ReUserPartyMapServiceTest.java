package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReUserPartyMapService 单元测试 -- 纯 JUnit 5 + Mockito，不连数据库。
 * 覆盖 Task 6 简报要求的 3 个用例：命中返回 orgId / 无映射抛 RE-40001 / bind 已存在走 update。
 */
@ExtendWith(MockitoExtension.class)
class ReUserPartyMapServiceTest {

    @Mock
    private ReUserPartyMapMapper reUserPartyMapMapper;

    @InjectMocks
    private ReUserPartyMapService reUserPartyMapService;

    @Test
    void getRequiredPartyOrgId_hit_returnsOrgId() {
        ReUserPartyMap map = new ReUserPartyMap();
        map.setId(1L);
        map.setUserId("E001");
        map.setPartyOrgId(100L);
        map.setPartyRole("SECRETARY");
        when(reUserPartyMapMapper.selectOne(any())).thenReturn(map);

        Long orgId = reUserPartyMapService.getRequiredPartyOrgId("E001");

        assertThat(orgId).isEqualTo(100L);
    }

    @Test
    void getRequiredPartyOrgId_noMapping_throwsRe40001() {
        when(reUserPartyMapMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> reUserPartyMapService.getRequiredPartyOrgId("E404"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40001");
                    assertThat(bizEx.getMessage()).isEqualTo("当前用户未绑定党组织，请联系管理员");
                });
    }

    @Test
    void bind_existingMapping_updatesRecord() {
        ReUserPartyMap existing = new ReUserPartyMap();
        existing.setId(5L);
        existing.setUserId("E001");
        existing.setPartyOrgId(1L);
        existing.setPartyRole("REPORTER");
        when(reUserPartyMapMapper.selectOne(any())).thenReturn(existing);

        reUserPartyMapService.bind("E001", 2L, "SECRETARY");

        // uk_user 唯一：命中已存在映射时必须走 updateById，绝不重复 insert
        verify(reUserPartyMapMapper, never()).insert(ArgumentMatchers.any(ReUserPartyMap.class));
        verify(reUserPartyMapMapper).updateById(ArgumentMatchers.<ReUserPartyMap>argThat(e ->
                e.getId().equals(5L) && e.getPartyOrgId().equals(2L) && "SECRETARY".equals(e.getPartyRole())));
    }
}
