package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalUserRoleRowDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagBriefDTO;
import com.bank.branch.platform.performance.eval.dto.EvalUserTagRow;
import com.bank.branch.platform.performance.eval.entity.EvalTag;
import com.bank.branch.platform.performance.eval.entity.EvalUserTag;
import com.bank.branch.platform.performance.eval.mapper.EvalTagMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserSettingMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalUserTagMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 人员标签关联服务.
 */
@Slf4j
@Service
public class EvalUserTagService {

    private final EvalUserTagMapper evalUserTagMapper;
    private final EvalTagMapper evalTagMapper;
    private final UserApi userApi;
    private final AddressBookApi addressBookApi;
    private final EvalUserSettingMapper evalUserSettingMapper;

    @Autowired
    public EvalUserTagService(EvalUserTagMapper evalUserTagMapper,
                              EvalTagMapper evalTagMapper,
                              UserApi userApi,
                              AddressBookApi addressBookApi,
                              EvalUserSettingMapper evalUserSettingMapper) {
        this.evalUserTagMapper = evalUserTagMapper;
        this.evalTagMapper = evalTagMapper;
        this.userApi = userApi;
        this.addressBookApi = addressBookApi;
        this.evalUserSettingMapper = evalUserSettingMapper;
    }

    /** 查询人员的标签关联. */
    public List<EvalUserTag> getByUserId(String userId) {
        return evalUserTagMapper.selectByUserId(userId);
    }

    /** 查询标签关联的所有用户ID（返回工号 String 列表）. */
    public List<String> getUserIdsByTagId(Long tagId) {
        return evalUserTagMapper.selectUserIdsByTagId(tagId);
    }

    /** 批量绑定人员标签. */
    @Transactional(rollbackFor = Exception.class)
    public void batchBind(String userId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        List<EvalUserTag> list = tagIds.stream().map(tagId -> {
            EvalUserTag ut = new EvalUserTag();
            ut.setUserId(userId);
            ut.setTagId(tagId);
            return ut;
        }).collect(Collectors.toList());
        evalUserTagMapper.batchInsert(list);
    }

    /** 批量解绑人员标签. */
    @Transactional(rollbackFor = Exception.class)
    public void batchUnbind(String userId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        evalUserTagMapper.batchDelete(userId, tagIds);
    }

    /**
     * 覆盖式保存人员的评价角色：删除该用户全部旧关联，再写入新的被评价人标签（至多一个）+ 评价人标签（多个）。
     * <p>被评价人单选由参数结构（单个 beEvalTagId）天然保证；类型校验防止前端传错类型。</p>
     *
     * @param userId      人员ID
     * @param beEvalTagId 被评价人标签ID（必须 tagType=1；null 表示清空被评价人角色）
     * @param evalTagIds  评价人标签ID列表（必须都是 tagType=2；null/空 表示清空评价人角色）
     * @throws PerfException EVAL_RULE_NOT_FOUND（标签不存在）/ EVAL_TAG_TYPE_MISMATCH（类型不符）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(String userId, Long beEvalTagId, List<Long> evalTagIds) {
        // 1. 校验被评价人标签必须 tagType=1
        if (beEvalTagId != null) {
            EvalTag t = evalTagMapper.selectById(beEvalTagId);
            if (t == null) throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, beEvalTagId);
            if (!Integer.valueOf(1).equals(t.getTagType())) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH, beEvalTagId);
            }
        }
        // 2. 校验评价人标签必须都是 tagType=2
        List<Long> evalIds = (evalTagIds == null) ? List.of()
                : evalTagIds.stream().distinct().collect(Collectors.toList());
        for (Long tid : evalIds) {
            EvalTag t = evalTagMapper.selectById(tid);
            if (t == null) throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tid);
            if (!Integer.valueOf(2).equals(t.getTagType())) {
                throw new PerfException(PerfErrorCode.EVAL_TAG_TYPE_MISMATCH, tid);
            }
        }
        // 3. 覆盖：删除该用户全部旧标签关联
        List<EvalUserTag> existing = evalUserTagMapper.selectByUserId(userId);
        if (!existing.isEmpty()) {
            List<Long> oldTagIds = existing.stream().map(EvalUserTag::getTagId).collect(Collectors.toList());
            evalUserTagMapper.batchDelete(userId, oldTagIds);
        }
        // 4. 写入新组合（被评价 1 个 + 评价人 N 个）
        List<EvalUserTag> toInsert = new ArrayList<>();
        if (beEvalTagId != null) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(beEvalTagId);
            toInsert.add(u);
        }
        for (Long tid : evalIds) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(tid);
            toInsert.add(u);
        }
        if (!toInsert.isEmpty()) {
            evalUserTagMapper.batchInsert(toInsert);
        }
        log.info("[EvalUserTagService.saveUserRoles] userId={} beEvalTagId={} evalTagIds={}", userId, beEvalTagId, evalIds);
    }

    /**
     * 覆盖式保存人员评价角色 + 写"是否启用评价"位（同一事务，原子）。
     *
     * @param userId      人员工号
     * @param beEvalTagId 被评价人标签ID（null 表示清空）
     * @param evalTagIds  评价人标签ID列表（null/空 表示清空）
     * @param evalEnabled 是否启用评价：1=是 0=否；null 兜底为 0
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRolesWithSetting(String userId, Long beEvalTagId, List<Long> evalTagIds, Integer evalEnabled) {
        saveUserRoles(userId, beEvalTagId, evalTagIds);
        int enabled = (evalEnabled != null && evalEnabled == 1) ? 1 : 0;
        evalUserSettingMapper.upsert(userId, enabled);
        log.info("[EvalUserTagService.saveUserRolesWithSetting] userId={} evalEnabled={}", userId, enabled);
    }

    /** "是"过滤驱动模式下最大全量启用工号上限（防止超大结果集撑爆内存）。 */
    private static final int ENABLED_DRIVEN_CAP = 5000;

    /**
     * 分页聚合查询人员标签列表，按"是否启用评价"三态分派。
     *
     * @param keyword     关键词（工号/姓名，可空）
     * @param evalEnabled 过滤态：null/"1"=只看启用(默认)，"0"=否，其它("all"/"")=全部
     * @param page        页码（从 1 开始）
     * @param pageSize    每页条数
     */
    public PageResult<EvalUserRoleRowDTO> pageUserRoles(String keyword, String evalEnabled, int page, int pageSize) {
        String mode = normalizeEnabledMode(evalEnabled);
        if ("1".equals(mode)) {
            return pageEnabledDriven(keyword, page, pageSize);
        }
        PageResult<UserDTO> users = userApi.pageUsers(keyword, page, pageSize);
        List<EvalUserRoleRowDTO> rows = assembleRows(users.getRecords());
        if ("0".equals(mode)) {
            rows = rows.stream()
                    .filter(r -> r.getEvalEnabled() == null || r.getEvalEnabled() == 0)
                    .collect(Collectors.toList());
        }
        return PageResult.of(page, pageSize, users.getTotal(), rows);
    }

    /** 归一过滤态：null/"1"->"1"；"0"->"0"；其它->"all"。 */
    private String normalizeEnabledMode(String evalEnabled) {
        if (evalEnabled == null || "1".equals(evalEnabled.trim())) {
            return "1";
        }
        if ("0".equals(evalEnabled.trim())) {
            return "0";
        }
        return "all";
    }

    /** "是"过滤：eval 侧驱动——取全部启用工号，批量解析，内存关键词过滤+内存分页。 */
    private PageResult<EvalUserRoleRowDTO> pageEnabledDriven(String keyword, int page, int pageSize) {
        List<String> enabledIds = evalUserSettingMapper.selectEnabledUserIds();
        if (enabledIds.size() > ENABLED_DRIVEN_CAP) {
            log.warn("[EvalUserTagService.pageEnabledDriven] 启用工号数 {} 超上限 {}，截断", enabledIds.size(), ENABLED_DRIVEN_CAP);
            enabledIds = new ArrayList<>(enabledIds.subList(0, ENABLED_DRIVEN_CAP));
        }
        if (enabledIds.isEmpty()) {
            return PageResult.of(page, pageSize, 0L, new ArrayList<>());
        }
        List<UserDTO> users = userApi.getUserByEmpIds(enabledIds);
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty()) {
            users = users.stream().filter(u -> matchesKeyword(u, kw)).collect(Collectors.toList());
        }
        long total = users.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(users.size(), from + pageSize);
        List<UserDTO> pageUsers = from >= users.size() ? new ArrayList<>() : users.subList(from, to);
        List<EvalUserRoleRowDTO> rows = assembleRows(pageUsers);
        return PageResult.of(page, pageSize, total, rows);
    }

    /** 关键词匹配：工号 / 中文名 / 登录名 任一 contains。 */
    private boolean matchesKeyword(UserDTO u, String kw) {
        return (u.getEmpId() != null && u.getEmpId().contains(kw))
                || (u.getDisplayName() != null && u.getDisplayName().contains(kw))
                || (u.getUsername() != null && u.getUsername().contains(kw));
    }

    /**
     * 导出用：取关键词匹配的全部人员（翻页累积，每页 100），上限 cap 行。
     *
     * @param keyword 关键词（工号/姓名，可空）
     * @param cap     最大导出行数（保护，超出截断）
     * @return 装配好的列表行
     */
    public List<EvalUserRoleRowDTO> listForExport(String keyword, int cap) {
        List<EvalUserRoleRowDTO> all = new ArrayList<>();
        int pageSize = 100;
        int pageNo = 1;
        while (all.size() < cap) {
            PageResult<UserDTO> users = userApi.pageUsers(keyword, pageNo, pageSize);
            List<UserDTO> records = users.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            all.addAll(assembleRows(records));
            if (all.size() >= users.getTotal()) {
                break;
            }
            pageNo++;
        }
        if (all.size() > cap) {
            return new ArrayList<>(all.subList(0, cap));
        }
        return all;
    }

    /**
     * 将 UserDTO 列表装配成 EvalUserRoleRowDTO 列表（批量查通讯录、角色、标签）。
     * records 为空时返回空列表。
     */
    private List<EvalUserRoleRowDTO> assembleRows(List<UserDTO> records) {
        if (records == null || records.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> empIds = records.stream().map(UserDTO::getEmpId).collect(Collectors.toList());

        // 部门/岗位：通讯录批量
        Map<String, EmployeeDTO> empMap = addressBookApi.getEmployees(empIds).stream()
                .collect(Collectors.toMap(EmployeeDTO::getEmpId, Function.identity(), (a, b) -> a));
        // RBAC 角色：批量
        Map<String, List<RoleSimpleDTO>> roleMap = userApi.getRolesByUserIds(empIds);

        // EVAL 标签：按工号（String）批量匹配
        Map<String, List<EvalUserTagRow>> tagMap = new HashMap<>();
        if (!empIds.isEmpty()) {
            for (EvalUserTagRow r : evalUserTagMapper.selectUserTagsByUserIds(empIds)) {
                tagMap.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
            }
        }

        // 批量查询启用位，生成 enabledSet 用于 overlay
        Set<String> enabledSet = empIds.isEmpty()
                ? Set.of()
                : new HashSet<>(evalUserSettingMapper.selectEnabledUserIdsIn(empIds));

        List<EvalUserRoleRowDTO> rows = new ArrayList<>(records.size());
        for (UserDTO u : records) {
            EvalUserRoleRowDTO row = new EvalUserRoleRowDTO();
            row.setUserId(u.getEmpId());
            row.setUserName(u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
            EmployeeDTO emp = empMap.get(u.getEmpId());
            if (emp != null) {
                row.setOrgName(emp.getOrgName());
                row.setPosition(emp.getPosition());
            }
            List<RoleSimpleDTO> roles = roleMap.getOrDefault(u.getEmpId(), List.of());
            row.setRoleNames(roles.stream().map(RoleSimpleDTO::getRoleChName).collect(Collectors.toList()));

            List<EvalUserTagRow> tagRows = tagMap.getOrDefault(u.getEmpId(), List.of());
            EvalUserTagBriefDTO beEval = tagRows.stream()
                    .filter(t -> Integer.valueOf(1).equals(t.getTagType()))
                    .findFirst()
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .orElse(null);
            List<EvalUserTagBriefDTO> evalTags = tagRows.stream()
                    .filter(t -> Integer.valueOf(2).equals(t.getTagType()))
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .collect(Collectors.toList());
            row.setBeEvalTag(beEval);
            row.setEvalTags(evalTags);
            // overlay：从 EVAL_USER_SETTING 读取启用位
            row.setEvalEnabled(enabledSet.contains(u.getEmpId()) ? 1 : 0);
            rows.add(row);
        }
        return rows;
    }

}
