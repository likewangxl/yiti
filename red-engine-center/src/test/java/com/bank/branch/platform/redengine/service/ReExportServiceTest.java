package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.mapper.ReScoreMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReExportService 单元测试 -- 纯 JUnit 5 + Mockito，不连数据库。
 * <p>覆盖 Task 11 简报要求的核心用例：①type 非法抛 RE-40006；②行数超限(mock selectCount&gt;10000)
 * 抛 RE-40007 且不再查询数据（先 count 后查详情，避免全量加载后才发现超限）；③正常导出返回非空
 * 字节流且前两字节为 XLSX 魔数 {@code PK}(0x50 0x4B)。并补充边界(=10000 不超限)与两种 type
 * (submit/score)、空列表的覆盖。</p>
 *
 * <p><b>错误码纠偏说明</b>：task-11-brief.md 原文写导出超限抛 {@code RE-40005}，但 RE-40005 已被
 * Task 10 {@code ReCockpitService.executeOverdue}（上报记录不存在）占用，模块内 RE-40001~40005
 * 均已被 Task 6/7/9/10 占用（见下方核对表），本任务改用 {@code RE-40007}（导出超限）+
 * {@code RE-40006}（type 非法，与简报既定一致），避免同码多义。</p>
 * <table>
 *   <caption>模块错误码占用核对表</caption>
 *   <tr><td>RE-40001</td><td>ReUserPartyMapService/ReSubmitService</td><td>当前用户未绑定党组织</td></tr>
 *   <tr><td>RE-40002</td><td>RePartyOrgService</td><td>存在下级党组织不可删除</td></tr>
 *   <tr><td>RE-40003</td><td>ReReviewService</td><td>提交记录不存在</td></tr>
 *   <tr><td>RE-40004</td><td>ReReviewService</td><td>考核项累计得分超上限</td></tr>
 *   <tr><td>RE-40005</td><td>ReCockpitService</td><td>上报记录不存在</td></tr>
 *   <tr><td>RE-40006</td><td>ReExportService(本任务)</td><td>导出类型非法</td></tr>
 *   <tr><td>RE-40007</td><td>ReExportService(本任务)</td><td>导出数据超过上限</td></tr>
 * </table>
 */
@ExtendWith(MockitoExtension.class)
class ReExportServiceTest {

    @Mock
    private ReSubmitMapper reSubmitMapper;

    @Mock
    private ReScoreMapper reScoreMapper;

    @InjectMocks
    private ReExportService reExportService;

    /** 预热各实体的 lambda 缓存，使 LambdaQueryWrapper 在纯单测中可正常构造. */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReSubmit.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReScore.class);
    }

    private static ReSubmit sampleSubmit(Long id) {
        ReSubmit s = new ReSubmit();
        s.setId(id);
        s.setOrgId(1L);
        s.setProjectName("联建示范项目");
        s.setSubmitType(1);
        s.setSubmitDate(LocalDate.of(2026, 1, 15));
        s.setStatus(2);
        return s;
    }

    private static ReScore sampleScore(Long id) {
        ReScore s = new ReScore();
        s.setId(id);
        s.setOrgId(1L);
        s.setScorePeriod("2026-01");
        s.setBaseScore(new BigDecimal("90"));
        s.setDeductionScore(new BigDecimal("5"));
        s.setFinalScore(new BigDecimal("85"));
        return s;
    }

    // ---------- ① type 非法 ----------

    @Test
    void exportData_typeInvalid_throwsRe40006() {
        assertThatThrownBy(() -> reExportService.exportData("unknown"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("RE-40006"));

        verify(reSubmitMapper, never()).selectCount(any());
        verify(reScoreMapper, never()).selectCount(any());
    }

    // ---------- ② 超限：先 count 后查详情，超限直接抛错不查数据 ----------

    @Test
    void exportData_submitCountExceedsLimit_throwsRe40007_andNeverQueriesList() {
        when(reSubmitMapper.selectCount(any())).thenReturn(10001L);

        assertThatThrownBy(() -> reExportService.exportData("submit"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("RE-40007"));

        verify(reSubmitMapper, never()).selectList(any());
    }

    @Test
    void exportData_scoreCountExceedsLimit_throwsRe40007_andNeverQueriesList() {
        when(reScoreMapper.selectCount(any())).thenReturn(10001L);

        assertThatThrownBy(() -> reExportService.exportData("score"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("RE-40007"));

        verify(reScoreMapper, never()).selectList(any());
    }

    @Test
    void exportData_submitCountAtLimit_10000_doesNotThrow() {
        when(reSubmitMapper.selectCount(any())).thenReturn(10000L);
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(sampleSubmit(1L)));

        byte[] bytes = reExportService.exportData("submit");

        assertThat(bytes).isNotEmpty();
        verify(reSubmitMapper).selectList(any());
    }

    // ---------- ③ 正常导出：字节流非空 + XLSX 魔数 PK ----------

    @Test
    void exportData_submitType_returnsNonEmptyXlsxBytes_withPkMagic() {
        when(reSubmitMapper.selectCount(any())).thenReturn(2L);
        when(reSubmitMapper.selectList(any())).thenReturn(List.of(sampleSubmit(1L), sampleSubmit(2L)));

        byte[] bytes = reExportService.exportData("submit");

        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }

    @Test
    void exportData_scoreType_returnsNonEmptyXlsxBytes_withPkMagic() {
        when(reScoreMapper.selectCount(any())).thenReturn(2L);
        when(reScoreMapper.selectList(any())).thenReturn(List.of(sampleScore(1L), sampleScore(2L)));

        byte[] bytes = reExportService.exportData("score");

        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }

    @Test
    void exportData_emptyList_stillReturnsValidXlsxBytes() {
        when(reSubmitMapper.selectCount(any())).thenReturn(0L);
        when(reSubmitMapper.selectList(any())).thenReturn(List.of());

        byte[] bytes = reExportService.exportData("submit");

        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }
}
