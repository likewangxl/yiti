package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.redengine.api.dto.ReSubmitCreateReqDTO;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.entity.ReSubmitFile;
import com.bank.branch.platform.redengine.mapper.ReSubmitFileMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReSubmitService 单元测试 -- 纯 JUnit 5 + Mockito，不连数据库。
 * 覆盖 Task 8 简报要求的 3 个核心用例（create 落库 status=1 且逐附件 bindFile / my 列表按党组织过滤分页 /
 * 未绑定党组织抛 RE-40001），并补充无附件、getDetail 基础用例。
 */
@ExtendWith(MockitoExtension.class)
class ReSubmitServiceTest {

    @Mock
    private ReSubmitMapper reSubmitMapper;

    @Mock
    private ReSubmitFileMapper reSubmitFileMapper;

    @Mock
    private ReUserPartyMapService reUserPartyMapService;

    @Mock
    private FileApi fileApi;

    @InjectMocks
    private ReSubmitService reSubmitService;

    /** 预热 ReSubmit 的 lambda 缓存，使 LambdaQueryWrapper.getTargetSql() 可在纯单测中渲染. */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReSubmit.class);
    }

    private static ReSubmitCreateReqDTO req(List<String> fileObjectIds) {
        ReSubmitCreateReqDTO dto = new ReSubmitCreateReqDTO();
        dto.setDimension("dim1");
        dto.setItemCode("1.1");
        dto.setItemName("联建规范度");
        dto.setMaxScore(new BigDecimal("35.0"));
        dto.setProjectName("与XX企业联合党建");
        dto.setSubmitType(1);
        dto.setSubmitDate(LocalDate.of(2026, 7, 18));
        dto.setFormData("{\"a\":1}");
        dto.setFileObjectIds(fileObjectIds);
        return dto;
    }

    @Test
    void createSubmit_savesWithStatusSubmitted_andBindsEachAttachment() {
        when(reUserPartyMapService.getRequiredPartyOrgId("E001")).thenReturn(100L);
        when(reSubmitMapper.insert(ArgumentMatchers.any(ReSubmit.class))).thenAnswer(invocation -> {
            ReSubmit arg = invocation.getArgument(0);
            arg.setId(500L);
            return 1;
        });
        when(fileApi.getFileName("FILE1")).thenReturn("a.pdf");
        when(fileApi.getFileName("FILE2")).thenReturn("b.pdf");

        Long id = reSubmitService.createSubmit(req(List.of("FILE1", "FILE2")), "E001");

        assertThat(id).isEqualTo(500L);
        // 落库断言：orgId 取自映射党组织，submitterId 为当前 empId，status 直接置 1=已提交（草稿语义废弃）
        verify(reSubmitMapper).insert(ArgumentMatchers.<ReSubmit>argThat(s ->
                s.getOrgId().equals(100L)
                        && "E001".equals(s.getSubmitterId())
                        && s.getStatus().equals(1)
                        && "dim1".equals(s.getDimension())
                        && "1.1".equals(s.getItemCode())));

        // 逐附件落 RE_SUBMIT_FILE，文件名经 FileApi.getFileName 回填
        verify(reSubmitFileMapper).insert(ArgumentMatchers.<ReSubmitFile>argThat(f ->
                f.getSubmitId().equals(500L) && "FILE1".equals(f.getFileObjectId()) && "a.pdf".equals(f.getFileName())));
        verify(reSubmitFileMapper).insert(ArgumentMatchers.<ReSubmitFile>argThat(f ->
                f.getSubmitId().equals(500L) && "FILE2".equals(f.getFileObjectId()) && "b.pdf".equals(f.getFileName())));

        // 逐附件调用 governance FileApi.bindFile，bizId 传字符串化的 submitId
        verify(fileApi).bindFile("RE_SUBMIT", "500", "FILE1", "ATTACHMENT");
        verify(fileApi).bindFile("RE_SUBMIT", "500", "FILE2", "ATTACHMENT");
        verify(fileApi, times(2)).bindFile(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void createSubmit_noAttachments_doesNotTouchFileApiOrFileMapper() {
        when(reUserPartyMapService.getRequiredPartyOrgId("E001")).thenReturn(100L);
        when(reSubmitMapper.insert(ArgumentMatchers.any(ReSubmit.class))).thenAnswer(invocation -> {
            ReSubmit arg = invocation.getArgument(0);
            arg.setId(501L);
            return 1;
        });

        Long id = reSubmitService.createSubmit(req(null), "E001");

        assertThat(id).isEqualTo(501L);
        verify(reSubmitFileMapper, never()).insert(any(ReSubmitFile.class));
        verify(fileApi, never()).bindFile(anyString(), anyString(), anyString(), anyString());
        verify(fileApi, never()).getFileName(anyString());
    }

    @Test
    void createSubmit_unboundOrg_throwsRe40001_andDoesNotInsert() {
        when(reUserPartyMapService.getRequiredPartyOrgId("E404"))
                .thenThrow(new BizException("RE-40001", "当前用户未绑定党组织，请联系管理员"));

        assertThatThrownBy(() -> reSubmitService.createSubmit(req(List.of("FILE1")), "E404"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizEx = (BizException) ex;
                    assertThat(bizEx.getCode()).isEqualTo("RE-40001");
                    assertThat(bizEx.getMessage()).isEqualTo("当前用户未绑定党组织，请联系管理员");
                });

        // 未绑定党组织必须直接短路，绝不落库、绝不触碰附件/FileApi
        verify(reSubmitMapper, never()).insert(any(ReSubmit.class));
        verify(reSubmitFileMapper, never()).insert(any(ReSubmitFile.class));
        verify(fileApi, never()).bindFile(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void getMySubmits_filtersByMappedOrgId_returnsPaged() {
        when(reUserPartyMapService.getRequiredPartyOrgId("E002")).thenReturn(200L);

        ReSubmit submit = new ReSubmit();
        submit.setId(1L);
        submit.setOrgId(200L);
        Page<ReSubmit> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of(submit));
        mpPage.setTotal(1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ReSubmit>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        when(reSubmitMapper.selectPage(ArgumentMatchers.<IPage<ReSubmit>>any(), wrapperCaptor.capture()))
                .thenReturn(mpPage);

        PageResult<ReSubmit> result = reSubmitService.getMySubmits("E002", 1, 10);

        assertThat(result.getPageNo()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(1L);

        // 分页过滤条件必须按映射党组织(200L)限定，而非全表扫描
        LambdaQueryWrapper<ReSubmit> wrapper = wrapperCaptor.getValue();
        wrapper.getTargetSql();
        assertThat(wrapper.getParamNameValuePairs().values()).contains(200L);
    }

    @Test
    void getDetail_delegatesToMapperSelectById() {
        ReSubmit submit = new ReSubmit();
        submit.setId(9L);
        when(reSubmitMapper.selectById(9L)).thenReturn(submit);

        ReSubmit result = reSubmitService.getDetail(9L);

        assertThat(result).isSameAs(submit);
    }
}
