package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 人员评价角色 Excel 导入服务。
 * <p>同步、原子：全部行校验通过才逐行覆盖式入库；任一行错误则一条都不写，返回行级错误明细。</p>
 */
@Slf4j
@Service
public class EvalUserTagImportService {

    /** 单次导入最大行数保护。 */
    private static final int MAX_IMPORT_ROWS = 5000;

    private final EvalTagMapper evalTagMapper;
    private final UserApi userApi;
    private final EvalUserTagService evalUserTagService;

    public EvalUserTagImportService(EvalTagMapper evalTagMapper,
                                    UserApi userApi,
                                    EvalUserTagService evalUserTagService) {
        this.evalTagMapper = evalTagMapper;
        this.userApi = userApi;
        this.evalUserTagService = evalUserTagService;
    }

    /**
     * 解析并导入 Excel。
     *
     * @param file 上传的 .xlsx 文件
     * @return 导入结果（成功条数或行级错误明细）
     */
    public EvalUserTagImportResultDTO importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        List<EvalUserTagImportRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(EvalUserTagImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (Exception e) {
            log.warn("[EvalUserTagImportService.importExcel] 解析失败: {}", e.getMessage());
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, e.getMessage());
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_ROWS_EXCEEDED, rows.size(), MAX_IMPORT_ROWS);
        }
        return importRows(rows);
    }

    /**
     * 校验全部行并原子入库。
     *
     * @param rows 解析后的行
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public EvalUserTagImportResultDTO importRows(List<EvalUserTagImportRow> rows) {
        EvalUserTagImportResultDTO result = new EvalUserTagImportResultDTO();
        if (rows == null || rows.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }

        // 1. 标签名称 → tagId（仅启用），按类型拆分
        Map<String, Long> beEvalNameToId = new HashMap<>();
        Map<String, Long> evalNameToId = new HashMap<>();
        for (EvalTag t : evalTagMapper.selectAll(null, 1)) {
            if (Integer.valueOf(1).equals(t.getTagType())) {
                beEvalNameToId.put(t.getTagName(), t.getTagId());
            } else if (Integer.valueOf(2).equals(t.getTagType())) {
                evalNameToId.put(t.getTagName(), t.getTagId());
            }
        }

        // 2. 工号有效性：批量查存在的工号
        List<String> empIds = rows.stream()
                .map(r -> r.getEmpId() == null ? "" : r.getEmpId().trim())
                .collect(Collectors.toList());
        Set<String> existingEmpIds = userApi.getUserByEmpIds(empIds).stream()
                .map(UserDTO::getEmpId)
                .collect(Collectors.toSet());

        // 3. 逐行校验
        List<EvalUserTagImportResultDTO.RowError> errors = new ArrayList<>();
        List<ParsedRow> parsed = new ArrayList<>();
        Set<String> seenEmpIds = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            EvalUserTagImportRow r = rows.get(i);
            String empId = r.getEmpId() == null ? "" : r.getEmpId().trim();

            if (empId.isEmpty()) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "工号不能为空"));
                continue;
            }
            if (!seenEmpIds.add(empId)) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "工号在文件内重复"));
                continue;
            }
            if (!existingEmpIds.contains(empId)) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "工号不存在"));
                continue;
            }

            // 被评价角色：可空；填了则只能一个且类型=1
            Long beEvalTagId = null;
            String beEvalRaw = r.getBeEvalRoleName() == null ? "" : r.getBeEvalRoleName().trim();
            boolean rowFailed = false;
            if (!beEvalRaw.isEmpty()) {
                List<String> beNames = splitNames(beEvalRaw);
                if (beNames.size() > 1) {
                    errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "被评价角色只能填一个"));
                    rowFailed = true;
                } else {
                    Long id = beEvalNameToId.get(beNames.get(0));
                    if (id == null) {
                        errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId,
                                "被评价角色无效或非被评价人类型：" + beNames.get(0)));
                        rowFailed = true;
                    } else {
                        beEvalTagId = id;
                    }
                }
            }
            if (rowFailed) {
                continue;
            }

            // 评价角色：可空；逗号分隔去重；每个类型=2
            List<Long> evalTagIds = new ArrayList<>();
            String evalRaw = r.getEvalRoleNames() == null ? "" : r.getEvalRoleNames().trim();
            if (!evalRaw.isEmpty()) {
                for (String name : splitNames(evalRaw)) {
                    Long id = evalNameToId.get(name);
                    if (id == null) {
                        errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId,
                                "评价角色无效或非评价人类型：" + name));
                        rowFailed = true;
                        break;
                    }
                    if (!evalTagIds.contains(id)) {
                        evalTagIds.add(id);
                    }
                }
            }
            if (rowFailed) {
                continue;
            }

            if (beEvalTagId == null && evalTagIds.isEmpty()) {
                errors.add(new EvalUserTagImportResultDTO.RowError(rowNo, empId, "被评价角色与评价角色不能同时为空"));
                continue;
            }

            parsed.add(new ParsedRow(empId, beEvalTagId, evalTagIds));
        }

        // 4. 任一行错误 → 整体不入库
        if (!errors.isEmpty()) {
            result.setSuccess(false);
            result.setImportedCount(0);
            result.setErrors(errors);
            return result;
        }

        // 5. 全部通过 → 逐行覆盖式入库
        for (ParsedRow p : parsed) {
            evalUserTagService.saveUserRoles(p.empId, p.beEvalTagId, p.evalTagIds);
        }
        result.setSuccess(true);
        result.setImportedCount(parsed.size());
        log.info("[EvalUserTagImportService.importRows] 导入成功 {} 条", parsed.size());
        return result;
    }

    /** 拆分逗号分隔名称（兼容中英文逗号），去空白与空项。 */
    private static List<String> splitNames(String raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw.split("[,，]")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 校验通过的一行解析结果。 */
    private static class ParsedRow {
        final String empId;
        final Long beEvalTagId;
        final List<Long> evalTagIds;

        ParsedRow(String empId, Long beEvalTagId, List<Long> evalTagIds) {
            this.empId = empId;
            this.beEvalTagId = beEvalTagId;
            this.evalTagIds = evalTagIds;
        }
    }
}
