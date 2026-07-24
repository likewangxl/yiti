package com.bank.branch.platform.governance.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.PersonTagImportResultDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagOrgImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagOrgMemberImportRow;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.PersonTagMapper;
import com.bank.branch.platform.governance.mapper.PersonTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务标签 Excel 导入服务（同步原子：任一行错误则整体不入库，返回行级错误明细）。
 * <p>按维度分流：EMP 维度按工号（PT_USER 校验），ORG 维度按机构编号（EXT_ORG_INFO.DEPT_NO 校验）。</p>
 * <ul>
 *   <li>全局导入（标签名称 + 成员标识）：库中无该标签时自动新建；已存在的同维度关联跳过（追加语义）。</li>
 *   <li>成员导入（成员标识）：对指定标签<b>按维度全量覆盖</b>——先清空该标签该维度原关联，再写入本次文件内容，
 *       不影响另一维度成员。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonTagImportService {

    /** 批量 INSERT 分片大小（规避超长 multi-values 语句）. */
    private static final int INSERT_CHUNK = 500;

    /** (标签,标识) 复合键分隔符：标识/标签名中不可能出现的控制字符，避免拼接歧义. */
    private static final String KEY_SEP = "";

    private final PersonTagMapper tagMapper;
    private final PersonTagRelMapper relMapper;
    private final PersonTagService personTagService;
    private final UserApi userApi;
    /** 机构维度：dept_no 存在性批量校验（EXT_ORG_INFO 口径，经 auth OrgApi）. */
    private final OrgApi orgApi;

    /**
     * 全局导入：标签 + 成员关联关系，缺标签自动新建，已存在同维度关联跳过。
     *
     * @param file     上传的 .xlsx 文件
     * @param dimType  成员维度（EMP/ORG，空按 EMP）
     * @param operator 操作人
     * @return 导入结果（成功计数或行级错误明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public PersonTagImportResultDTO importGlobal(MultipartFile file, String dimType, String operator) {
        boolean org = PersonTagRel.DIM_ORG.equalsIgnoreCase(dimType);
        // 1. 解析 + 逐行格式校验 + 文件内 (标签,标识) 去重
        List<RowRef> valid = new ArrayList<>();
        List<PersonTagImportResultDTO.RowError> errors = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        List<TagIdentRow> rows = org ? parseOrgGlobalRows(file) : parseEmpGlobalRows(file);
        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            String tagName = trim(rows.get(i).tagName());
            String ident = trim(rows.get(i).ident());
            if (tagName.isEmpty()) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, ident, "标签名称不能为空"));
                continue;
            }
            if (ident.isEmpty()) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, ident, org ? "机构号不能为空" : "工号不能为空"));
                continue;
            }
            if (!seenPairs.add(tagName + KEY_SEP + ident)) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, ident,
                        org ? "标签+机构号在文件内重复" : "标签+工号在文件内重复"));
                continue;
            }
            valid.add(new RowRef(rowNo, tagName, ident));
        }

        // 2. 标识有效性：单次/分片批量校验，不逐行查库
        Set<String> existingIdents = org
                ? batchExistingDeptNos(valid.stream().map(RowRef::ident).toList())
                : batchExistingUsernames(valid.stream().map(RowRef::ident).toList());
        List<RowRef> passed = new ArrayList<>();
        for (RowRef r : valid) {
            if (!existingIdents.contains(r.ident())) {
                errors.add(new PersonTagImportResultDTO.RowError(r.rowNo(), r.ident(),
                        org ? "机构号在系统中不存在" : "工号在系统中不存在"));
                continue;
            }
            passed.add(r);
        }

        // 3. 任一行错误 → 整体不入库
        PersonTagImportResultDTO result = new PersonTagImportResultDTO();
        if (!errors.isEmpty()) {
            result.setSuccess(false);
            result.setErrors(errors);
            return result;
        }

        // 4. 标签解析：库中已有的复用，缺的自动新建
        List<String> tagNames = new ArrayList<>(new LinkedHashSet<>(
                passed.stream().map(RowRef::tagName).toList()));
        Map<String, Long> tagIdByName = new HashMap<>();
        if (!tagNames.isEmpty()) {
            for (PersonTag t : tagMapper.selectByTagNames(tagNames)) {
                tagIdByName.put(t.getTagName(), t.getTagId());
            }
        }
        int createdTagCount = 0;
        for (String name : tagNames) {
            if (!tagIdByName.containsKey(name)) {
                PersonTag tag = new PersonTag();
                tag.setTagName(name);
                tag.setCreateBy(operator);
                tagMapper.insert(tag);
                tagIdByName.put(name, tag.getTagId());
                createdTagCount++;
            }
        }

        // 5. 已存在同维度关联跳过（追加语义），其余批量写入
        Set<String> existingRels = new HashSet<>();
        List<Long> tagIds = new ArrayList<>(new LinkedHashSet<>(tagIdByName.values()));
        if (!tagIds.isEmpty()) {
            for (PersonTagRel rel : relMapper.selectByTagIds(tagIds)) {
                String ident = org ? rel.getOrgDeptNo() : rel.getUsername();
                if (isDim(rel, org) && ident != null) {
                    existingRels.add(rel.getTagId() + KEY_SEP + ident);
                }
            }
        }
        List<PersonTagRel> toInsert = new ArrayList<>();
        int skipped = 0;
        for (RowRef r : passed) {
            Long tagId = tagIdByName.get(r.tagName());
            if (existingRels.contains(tagId + KEY_SEP + r.ident())) {
                skipped++;
                continue;
            }
            toInsert.add(newRel(tagId, org, r.ident(), operator));
        }
        insertChunked(toInsert);

        result.setSuccess(true);
        result.setImportedCount(toInsert.size());
        result.setCreatedTagCount(createdTagCount);
        result.setSkippedCount(skipped);
        log.info("[PersonTagImportService.importGlobal] operator={}, dim={}, imported={}, createdTags={}, skipped={}",
                operator, org ? "ORG" : "EMP", toInsert.size(), createdTagCount, skipped);
        return result;
    }

    /**
     * 成员导入：对指定标签<b>按维度全量覆盖</b>（先清空该标签该维度原关联，再写入本次文件内容）。
     *
     * @param tagId    标签 ID
     * @param file     上传的 .xlsx 文件
     * @param dimType  成员维度（EMP/ORG，空按 EMP）
     * @param operator 操作人
     * @return 导入结果（成功计数或行级错误明细）
     * @throws BizException GOV-40008 标签不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public PersonTagImportResultDTO importMembers(Long tagId, MultipartFile file, String dimType, String operator) {
        personTagService.requireTag(tagId);
        boolean org = PersonTagRel.DIM_ORG.equalsIgnoreCase(dimType);
        List<String> idents = org ? parseOrgMemberRows(file) : parseEmpMemberRows(file);

        // 1. 逐行格式校验 + 文件内标识去重（保留原文件行号用于错误定位）
        List<PersonTagImportResultDTO.RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<RowRef> valid = new ArrayList<>();
        for (int i = 0; i < idents.size(); i++) {
            int rowNo = i + 1;
            String ident = trim(idents.get(i));
            if (ident.isEmpty()) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, ident, org ? "机构号不能为空" : "工号不能为空"));
                continue;
            }
            if (!seen.add(ident)) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, ident,
                        org ? "机构号在文件内重复" : "工号在文件内重复"));
                continue;
            }
            valid.add(new RowRef(rowNo, null, ident));
        }

        // 2. 标识有效性：单次/分片批量校验
        Set<String> existing = org
                ? batchExistingDeptNos(valid.stream().map(RowRef::ident).toList())
                : batchExistingUsernames(valid.stream().map(RowRef::ident).toList());
        List<String> passed = new ArrayList<>();
        for (RowRef r : valid) {
            if (!existing.contains(r.ident())) {
                errors.add(new PersonTagImportResultDTO.RowError(r.rowNo(), r.ident(),
                        org ? "机构号在系统中不存在" : "工号在系统中不存在"));
                continue;
            }
            passed.add(r.ident());
        }

        // 3. 任一行错误 → 整体不动原数据
        PersonTagImportResultDTO result = new PersonTagImportResultDTO();
        if (!errors.isEmpty()) {
            result.setSuccess(false);
            result.setErrors(errors);
            return result;
        }

        // 4. 按维度全量覆盖：清空该标签该维度原关联 + 写入本次内容（同一事务，失败整体回滚）
        String dim = org ? PersonTagRel.DIM_ORG : PersonTagRel.DIM_EMP;
        int removed = relMapper.deleteByTagIdAndDim(tagId, dim);
        List<PersonTagRel> toInsert = new ArrayList<>(passed.size());
        for (String ident : passed) {
            toInsert.add(newRel(tagId, org, ident, operator));
        }
        insertChunked(toInsert);

        result.setSuccess(true);
        result.setImportedCount(toInsert.size());
        log.info("[PersonTagImportService.importMembers] operator={}, tagId={}, dim={}, removedOld={}, imported={}",
                operator, tagId, dim, removed, toInsert.size());
        return result;
    }

    // ===== 解析 =====

    private List<TagIdentRow> parseEmpGlobalRows(MultipartFile file) {
        return parse(file, PersonTagImportRow.class).stream()
                .map(r -> new TagIdentRow(r.getTagName(), r.getUsername())).toList();
    }

    private List<TagIdentRow> parseOrgGlobalRows(MultipartFile file) {
        return parse(file, PersonTagOrgImportRow.class).stream()
                .map(r -> new TagIdentRow(r.getTagName(), r.getOrgDeptNo())).toList();
    }

    private List<String> parseEmpMemberRows(MultipartFile file) {
        return parse(file, PersonTagMemberImportRow.class).stream()
                .map(PersonTagMemberImportRow::getUsername).toList();
    }

    private List<String> parseOrgMemberRows(MultipartFile file) {
        return parse(file, PersonTagOrgMemberImportRow.class).stream()
                .map(PersonTagOrgMemberImportRow::getOrgDeptNo).toList();
    }

    /**
     * 解析 Excel（首个 sheet，按 @ExcelProperty 表头映射）。
     *
     * @throws BizException GOV-42206 文件为空；GOV-42207 解析失败
     */
    private <T> List<T> parse(MultipartFile file, Class<T> head) {
        if (file == null || file.isEmpty()) {
            throw new BizException(GovErrorCode.PERSON_TAG_IMPORT_FILE_EMPTY.getCode(),
                    GovErrorCode.PERSON_TAG_IMPORT_FILE_EMPTY.getMessage());
        }
        List<T> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).head(head).sheet().doReadSync();
        } catch (Exception e) {
            log.warn("[PersonTagImportService.parse] 解析失败: {}", e.getMessage());
            throw new BizException(GovErrorCode.PERSON_TAG_IMPORT_FILE_INVALID.getCode(),
                    GovErrorCode.PERSON_TAG_IMPORT_FILE_INVALID.getMessage() + ": " + e.getMessage());
        }
        if (rows == null || rows.isEmpty()) {
            throw new BizException(GovErrorCode.PERSON_TAG_IMPORT_FILE_EMPTY.getCode(),
                    GovErrorCode.PERSON_TAG_IMPORT_FILE_EMPTY.getMessage());
        }
        return rows;
    }

    // ===== 校验 / 落库辅助 =====

    /** 按 PT_USER 批量取存在的工号集合（空列表直接返回空集）。 */
    private Set<String> batchExistingUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(userApi.filterExistingUsernames(usernames));
    }

    /** 按 EXT_ORG_INFO.DEPT_NO 批量取存在的机构编号集合（空列表直接返回空集）。 */
    private Set<String> batchExistingDeptNos(List<String> deptNos) {
        if (deptNos == null || deptNos.isEmpty()) {
            return Set.of();
        }
        Set<String> existing = new HashSet<>();
        List<OrgDTO> orgs = orgApi.getOrgsByDeptNos(deptNos);
        if (orgs != null) {
            for (OrgDTO o : orgs) {
                if (o != null && o.getDeptNo() != null) {
                    existing.add(o.getDeptNo());
                }
            }
        }
        return existing;
    }

    /** 新建成员关联行（按维度落对应标识列）。 */
    private static PersonTagRel newRel(Long tagId, boolean org, String ident, String operator) {
        PersonTagRel rel = new PersonTagRel();
        rel.setTagId(tagId);
        if (org) {
            rel.setDimType(PersonTagRel.DIM_ORG);
            rel.setOrgDeptNo(ident);
        } else {
            rel.setDimType(PersonTagRel.DIM_EMP);
            rel.setUsername(ident);
        }
        rel.setCreateBy(operator);
        return rel;
    }

    /** 成员行是否属于目标维度（存量行 dim_type 缺省按 EMP）。 */
    private static boolean isDim(PersonTagRel rel, boolean org) {
        String dim = rel.getDimType() == null ? PersonTagRel.DIM_EMP : rel.getDimType();
        return org ? PersonTagRel.DIM_ORG.equals(dim) : PersonTagRel.DIM_EMP.equals(dim);
    }

    /** 分片批量 INSERT（每片 {@value INSERT_CHUNK} 行）。 */
    private void insertChunked(List<PersonTagRel> rows) {
        for (int from = 0; from < rows.size(); from += INSERT_CHUNK) {
            relMapper.insertBatch(rows.subList(from, Math.min(from + INSERT_CHUNK, rows.size())));
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** 全局导入解析后的中立行（标签名 + 成员标识）。 */
    private record TagIdentRow(String tagName, String ident) {
    }

    /** 校验通过行的引用（Excel 数据行号从 1 起）。 */
    private record RowRef(int rowNo, String tagName, String ident) {
    }
}
