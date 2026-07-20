package com.bank.branch.platform.governance.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.PersonTagImportResultDTO;
import com.bank.branch.platform.governance.api.dto.PersonTagImportRow;
import com.bank.branch.platform.governance.api.dto.PersonTagMemberImportRow;
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
 * 人员标签 Excel 导入服务（同步原子：任一行错误则整体不入库，返回行级错误明细）。
 * <p>工号有效性按 PT_USER 单次/分片批量校验（{@link UserApi#filterExistingUsernames}），不逐行查库；
 * 姓名列仅供人工对照，不校验一致性、不入库（展示时实时解析）。</p>
 * <ul>
 *   <li>全局导入（标签名称/工号/姓名）：库中无该标签时自动新建；已存在的关联跳过（追加语义）。</li>
 *   <li>成员导入（工号/姓名）：对指定标签<b>全量覆盖</b>——先清空原关联再写入本次文件内容。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonTagImportService {

    /** 批量 INSERT 分片大小（规避超长 multi-values 语句）. */
    private static final int INSERT_CHUNK = 500;

    /** (标签,工号) 复合键分隔符：工号/标签名中不可能出现的控制字符，避免拼接歧义. */
    private static final String KEY_SEP = "\u0001";

    private final PersonTagMapper tagMapper;
    private final PersonTagRelMapper relMapper;
    private final PersonTagService personTagService;
    private final UserApi userApi;

    /**
     * 全局导入：标签+人员关联关系，缺标签自动新建，已存在关联跳过。
     *
     * @param file     上传的 .xlsx 文件
     * @param operator 操作人
     * @return 导入结果（成功计数或行级错误明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public PersonTagImportResultDTO importGlobal(MultipartFile file, String operator) {
        List<PersonTagImportRow> rows = parse(file, PersonTagImportRow.class);

        // 1. 逐行格式校验 + 文件内 (标签,工号) 去重
        List<PersonTagImportResultDTO.RowError> errors = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        List<RowRef> valid = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            PersonTagImportRow r = rows.get(i);
            String tagName = trim(r.getTagName());
            String username = trim(r.getUsername());
            if (tagName.isEmpty()) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, username, "标签名称不能为空"));
                continue;
            }
            if (username.isEmpty()) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, username, "工号不能为空"));
                continue;
            }
            if (!seenPairs.add(tagName + KEY_SEP + username)) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, username, "标签+工号在文件内重复"));
                continue;
            }
            valid.add(new RowRef(rowNo, tagName, username));
        }

        // 2. 工号有效性：按 PT_USER 单次/分片批量校验，不逐行查库
        Set<String> existingUsernames = batchExistingUsernames(
                valid.stream().map(RowRef::username).toList());
        List<RowRef> passed = new ArrayList<>();
        for (RowRef r : valid) {
            if (!existingUsernames.contains(r.username())) {
                errors.add(new PersonTagImportResultDTO.RowError(r.rowNo(), r.username(), "工号在系统中不存在"));
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

        // 5. 已存在关联跳过（追加语义），其余批量写入
        Set<String> existingRels = new HashSet<>();
        List<Long> tagIds = new ArrayList<>(new LinkedHashSet<>(tagIdByName.values()));
        if (!tagIds.isEmpty()) {
            for (PersonTagRel rel : relMapper.selectByTagIds(tagIds)) {
                existingRels.add(rel.getTagId() + KEY_SEP + rel.getUsername());
            }
        }
        List<PersonTagRel> toInsert = new ArrayList<>();
        int skipped = 0;
        for (RowRef r : passed) {
            Long tagId = tagIdByName.get(r.tagName());
            if (existingRels.contains(tagId + KEY_SEP + r.username())) {
                skipped++;
                continue;
            }
            PersonTagRel rel = new PersonTagRel();
            rel.setTagId(tagId);
            rel.setUsername(r.username());
            rel.setCreateBy(operator);
            toInsert.add(rel);
        }
        insertChunked(toInsert);

        result.setSuccess(true);
        result.setImportedCount(toInsert.size());
        result.setCreatedTagCount(createdTagCount);
        result.setSkippedCount(skipped);
        log.info("[PersonTagImportService.importGlobal] operator={}, imported={}, createdTags={}, skipped={}",
                operator, toInsert.size(), createdTagCount, skipped);
        return result;
    }

    /**
     * 成员导入：对指定标签全量覆盖（先清空原关联，再写入本次文件内容）。
     *
     * @param tagId    标签 ID
     * @param file     上传的 .xlsx 文件
     * @param operator 操作人
     * @return 导入结果（成功计数或行级错误明细）
     * @throws BizException GOV-40008 标签不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public PersonTagImportResultDTO importMembers(Long tagId, MultipartFile file, String operator) {
        personTagService.requireTag(tagId);
        List<PersonTagMemberImportRow> rows = parse(file, PersonTagMemberImportRow.class);

        // 1. 逐行格式校验 + 文件内工号去重
        List<PersonTagImportResultDTO.RowError> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<RowRef> valid = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            String username = trim(rows.get(i).getUsername());
            if (username.isEmpty()) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, username, "工号不能为空"));
                continue;
            }
            if (!seen.add(username)) {
                errors.add(new PersonTagImportResultDTO.RowError(rowNo, username, "工号在文件内重复"));
                continue;
            }
            valid.add(new RowRef(rowNo, null, username));
        }

        // 2. 工号有效性：按 PT_USER 单次/分片批量校验
        Set<String> existing = batchExistingUsernames(valid.stream().map(RowRef::username).toList());
        List<String> usernames = new ArrayList<>();
        for (RowRef r : valid) {
            if (!existing.contains(r.username())) {
                errors.add(new PersonTagImportResultDTO.RowError(r.rowNo(), r.username(), "工号在系统中不存在"));
                continue;
            }
            usernames.add(r.username());
        }

        // 3. 任一行错误 → 整体不动原数据
        PersonTagImportResultDTO result = new PersonTagImportResultDTO();
        if (!errors.isEmpty()) {
            result.setSuccess(false);
            result.setErrors(errors);
            return result;
        }

        // 4. 全量覆盖：清空原关联 + 写入本次内容（同一事务，失败整体回滚）
        int removed = relMapper.deleteByTagId(tagId);
        List<PersonTagRel> toInsert = new ArrayList<>(usernames.size());
        for (String username : usernames) {
            PersonTagRel rel = new PersonTagRel();
            rel.setTagId(tagId);
            rel.setUsername(username);
            rel.setCreateBy(operator);
            toInsert.add(rel);
        }
        insertChunked(toInsert);

        result.setSuccess(true);
        result.setImportedCount(toInsert.size());
        log.info("[PersonTagImportService.importMembers] operator={}, tagId={}, removedOld={}, imported={}",
                operator, tagId, removed, toInsert.size());
        return result;
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

    /** 按 PT_USER 批量取存在的工号集合（空列表直接返回空集）。 */
    private Set<String> batchExistingUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(userApi.filterExistingUsernames(usernames));
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

    /** 校验通过行的引用（Excel 数据行号从 1 起）。 */
    private record RowRef(int rowNo, String tagName, String username) {
    }
}
