package com.bank.branch.platform.redengine.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReScoreExportRow;
import com.bank.branch.platform.redengine.api.dto.ReSubmitExportRow;
import com.bank.branch.platform.redengine.entity.ReScore;
import com.bank.branch.platform.redengine.entity.ReSubmit;
import com.bank.branch.platform.redengine.mapper.ReScoreMapper;
import com.bank.branch.platform.redengine.mapper.ReSubmitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 红色引擎-数据导出服务。
 * <p>移植自源 redengine {@code ExportController.exportData}（{@code business.controller}，
 * 源码直接把导出逻辑写在 Controller 里，本次移植按平台 Controller/Service 分层规范下沉到
 * Service），导出方式由 hutool {@code ExcelWriter} 换为平台既有 EasyExcel（{@code red-engine-center}
 * pom 已含 easyexcel 依赖，无需新增）。</p>
 *
 * <p><b>错误码纠偏（相对 task-11-brief.md 原文）</b>：简报原文写导出超限抛 {@code RE-40005}，但
 * RE-40005 已被 Task 10 {@code ReCockpitService.executeOverdue}（上报记录不存在）占用，模块内
 * RE-40001~40005 分别被 Task 6/7/9/9/10 占用，为避免同码多义，本次改用未占用的
 * {@code RE-40007}（导出数据超过上限）+ {@code RE-40006}（导出类型非法，与简报既定一致）。</p>
 *
 * <p><b>新增防呆（源系统没有的行为）</b>：导出行数上限 10000，且严格按"先 {@code selectCount}
 * 判断、超限即抛错、不再查询明细"的顺序执行，避免像源码那样先把全量数据一次性 {@code selectList}
 * 进内存才发现行数过大导致 OOM 风险。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReExportService {

    /** 导出类型：材料上报 */
    public static final String EXPORT_TYPE_SUBMIT = "submit";
    /** 导出类型：评分 */
    public static final String EXPORT_TYPE_SCORE = "score";
    /** 导出行数上限（新增防呆，防止全量导出把 JVM 堆打爆） */
    private static final long MAX_EXPORT_ROWS = 10000L;

    private final ReSubmitMapper reSubmitMapper;
    private final ReScoreMapper reScoreMapper;

    /**
     * 按类型导出 Excel（xlsx）字节流。
     *
     * @param type 导出类型，仅支持 {@code submit}（材料上报）/ {@code score}（评分）
     * @return xlsx 文件字节数组
     * @throws BizException code=RE-40006，type 非法（既非 submit 也非 score）
     * @throws BizException code=RE-40007，命中行数超过导出上限 10000
     */
    public byte[] exportData(String type) {
        if (EXPORT_TYPE_SUBMIT.equals(type)) {
            return exportSubmits();
        } else if (EXPORT_TYPE_SCORE.equals(type)) {
            return exportScores();
        }
        throw new BizException("RE-40006", "导出类型非法，仅支持 submit/score");
    }

    /**
     * 导出材料上报（RE_SUBMIT）。表头与源码 {@code addHeaderAlias} 逐字对齐：
     * ID/Organization/Project/Type/Date/Status。
     */
    private byte[] exportSubmits() {
        long count = reSubmitMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > MAX_EXPORT_ROWS) {
            throw new BizException("RE-40007", "导出数据超过上限，请缩小范围");
        }

        List<ReSubmit> submits = reSubmitMapper.selectList(new LambdaQueryWrapper<>());
        List<ReSubmitExportRow> rows = submits.stream().map(this::toSubmitRow).collect(Collectors.toList());
        byte[] bytes = writeToBytes(rows, ReSubmitExportRow.class, "材料上报");
        log.info("[ReExportService.exportSubmits] rowCount={}, bytes={}", rows.size(), bytes.length);
        return bytes;
    }

    /**
     * 导出评分（RE_SCORE）。表头与源码 {@code addHeaderAlias} 逐字对齐：
     * ID/Organization/Period/Base Score/Deduction/Final Score。
     */
    private byte[] exportScores() {
        long count = reScoreMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > MAX_EXPORT_ROWS) {
            throw new BizException("RE-40007", "导出数据超过上限，请缩小范围");
        }

        List<ReScore> scores = reScoreMapper.selectList(new LambdaQueryWrapper<>());
        List<ReScoreExportRow> rows = scores.stream().map(this::toScoreRow).collect(Collectors.toList());
        byte[] bytes = writeToBytes(rows, ReScoreExportRow.class, "评分数据");
        log.info("[ReExportService.exportScores] rowCount={}, bytes={}", rows.size(), bytes.length);
        return bytes;
    }

    private ReSubmitExportRow toSubmitRow(ReSubmit s) {
        ReSubmitExportRow row = new ReSubmitExportRow();
        row.setId(s.getId());
        row.setOrgId(s.getOrgId());
        row.setProjectName(s.getProjectName());
        row.setSubmitType(s.getSubmitType());
        row.setSubmitDate(s.getSubmitDate());
        row.setStatus(s.getStatus());
        return row;
    }

    private ReScoreExportRow toScoreRow(ReScore s) {
        ReScoreExportRow row = new ReScoreExportRow();
        row.setId(s.getId());
        row.setOrgId(s.getOrgId());
        row.setScorePeriod(s.getScorePeriod());
        row.setBaseScore(s.getBaseScore());
        row.setDeductionScore(s.getDeductionScore());
        row.setFinalScore(s.getFinalScore());
        return row;
    }

    /**
     * 用 EasyExcel 把行数据写入内存字节流（xlsx 格式，PK 魔数开头）。
     *
     * @param rows      行数据
     * @param rowClass  行模型类型（{@code @ExcelProperty} 表头映射）
     * @param sheetName 工作表名
     * @param <T>       行模型泛型
     * @return xlsx 文件字节数组
     */
    private <T> byte[] writeToBytes(List<T> rows, Class<T> rowClass, String sheetName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, rowClass).sheet(sheetName).doWrite(rows);
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("[ReExportService.writeToBytes] EasyExcel 生成失败 sheetName={}", sheetName, ex);
            throw new RuntimeException("导出 Excel 生成失败：" + ex.getMessage(), ex);
        }
    }
}
