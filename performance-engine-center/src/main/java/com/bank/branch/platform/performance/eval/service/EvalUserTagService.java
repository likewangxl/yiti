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

    /**
     * 批量绑定人员标签（增量，指定角色）.
     * <p>局部排斥：若 batch 中任一标签已被该用户绑在对侧角色，抛 EVAL_ROLE_CONFLICT。</p>
     *
     * @param userId   人员工号
     * @param tagIds   标签ID列表
     * @param roleType 角色类型：1=被评价角色, 2=评价角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchBind(String userId, List<Long> tagIds, Integer roleType) {
        if (tagIds == null || tagIds.isEmpty()) return;
        // 局部排斥：对侧角色已持有的标签不能再绑到本角色
        int oppositeRole = (roleType != null && roleType == 1) ? 2 : 1;
        List<Long> oppositeTagIds = evalUserTagMapper.selectTagIdsByUserIdAndType(userId, oppositeRole);
        for (Long tid : tagIds) {
            if (oppositeTagIds.contains(tid)) {
                throw new PerfException(PerfErrorCode.EVAL_ROLE_CONFLICT, tid);
            }
        }
        List<EvalUserTag> list = tagIds.stream().map(tagId -> {
            EvalUserTag ut = new EvalUserTag();
            ut.setUserId(userId);
            ut.setTagId(tagId);
            ut.setRoleType(roleType);
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
     * 覆盖式保存人员的评价角色：删除该用户全部旧关联，再写入新的被评价人标签（至多一个，role=1）+ 评价人标签（多个，role=2）。
     * <p>被评价单选由参数结构（单个 beEvalTagId）天然保证；局部排斥：评价角色与被评价角色不得包含同一标签。</p>
     *
     * @param userId      人员ID
     * @param beEvalTagId 被评价人标签ID（role_type=1；null 表示清空被评价人角色）
     * @param evalTagIds  评价人标签ID列表（role_type=2；null/空 表示清空评价人角色）
     * @throws PerfException EVAL_RULE_NOT_FOUND（标签不存在）/ EVAL_ROLE_CONFLICT（评价角色与被评价角色冲突）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(String userId, Long beEvalTagId, List<Long> evalTagIds) {
        // 1. 评价人标签去重
        List<Long> evalIds = (evalTagIds == null) ? List.of()
                : evalTagIds.stream().distinct().collect(Collectors.toList());
        // 2. 局部排斥：评价角色不能包含被评价角色（同一人）
        if (beEvalTagId != null && evalIds.contains(beEvalTagId)) {
            throw new PerfException(PerfErrorCode.EVAL_ROLE_CONFLICT, beEvalTagId);
        }
        // 3. 标签存在性校验（角色由 role_type 控制，此处仅校验标签存在）
        if (beEvalTagId != null && evalTagMapper.selectById(beEvalTagId) == null) {
            throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, beEvalTagId);
        }
        for (Long tid : evalIds) {
            if (evalTagMapper.selectById(tid) == null) {
                throw new PerfException(PerfErrorCode.EVAL_RULE_NOT_FOUND, tid);
            }
        }
        // 4. 覆盖：删除该用户全部旧关联
        List<EvalUserTag> existing = evalUserTagMapper.selectByUserId(userId);
        if (!existing.isEmpty()) {
            List<Long> oldTagIds = existing.stream().map(EvalUserTag::getTagId).distinct().collect(Collectors.toList());
            evalUserTagMapper.batchDelete(userId, oldTagIds);
        }
        // 5. 写入新组合：被评价 role_type=1，评价人 role_type=2
        List<EvalUserTag> toInsert = new ArrayList<>();
        if (beEvalTagId != null) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(beEvalTagId);
            u.setRoleType(1);
            toInsert.add(u);
        }
        for (Long tid : evalIds) {
            EvalUserTag u = new EvalUserTag();
            u.setUserId(userId);
            u.setTagId(tid);
            u.setRoleType(2);
            toInsert.add(u);
        }
        if (!toInsert.isEmpty()) {
            evalUserTagMapper.batchInsert(toInsert);
        }
        log.info("[EvalUserTagService.saveUserRoles] userId={} beEvalTagId={} evalTagIds={}", userId, beEvalTagId, evalIds);
    }

    /**
     * 覆盖式保存人员评价角色 + 写"是否参与评价"标记（同一事务，原子）。
     * <p>语义：参与评价(是)=不在 EVAL_USER_SETTING 排除名单（默认）；不参与(否)=写入名单。
     * 仅显式 evalEnabled=0 视为"不参与"→入名单；null/1 一律视为"参与"→移出名单。</p>
     *
     * @param userId      人员工号
     * @param beEvalTagId 被评价人标签ID（null 表示清空）
     * @param evalTagIds  评价人标签ID列表（null/空 表示清空）
     * @param evalEnabled 是否参与评价：1=是 0=否；null 兜底为"参与"(是)
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRolesWithSetting(String userId, Long beEvalTagId, List<Long> evalTagIds, Integer evalEnabled) {
        saveUserRoles(userId, beEvalTagId, evalTagIds);
        // 仅显式 0 才"不参与"；null/1 视为参与（默认）。避免拆箱用 Integer.equals。
        boolean excluded = Integer.valueOf(0).equals(evalEnabled);
        if (excluded) {
            evalUserSettingMapper.markExcluded(userId);
        } else {
            evalUserSettingMapper.clearExcluded(userId);
        }
        log.info("[EvalUserTagService.saveUserRolesWithSetting] userId={} excluded={}", userId, excluded);
    }

    /** "否"（不参与）名单驱动模式下最大全量名单工号上限（防止超大结果集撑爆内存）。 */
    private static final int EXCLUDED_DRIVEN_CAP = 5000;

    /**
     * 分页聚合查询人员标签列表，按"是否参与评价"三态分派。
     *
     * @param keyword     关键词（工号/姓名，可空）
     * @param evalEnabled 过滤态：null/"1"=只看参与(默认)，"0"=不参与，其它("all"/"")=全部
     * @param page        页码（从 1 开始）
     * @param pageSize    每页条数
     */
    public PageResult<EvalUserRoleRowDTO> pageUserRoles(String keyword, String evalEnabled, int page, int pageSize) {
        String mode = normalizeEnabledMode(evalEnabled);
        if ("0".equals(mode)) {
            // 不参与：排除名单驱动（小集合，total 精确）
            return pageExcludedDriven(keyword, page, pageSize);
        }
        // 参与(默认)/全部：PT_USER 驱动 + overlay
        PageResult<UserDTO> users = userApi.pageUsers(keyword, page, pageSize);
        List<EvalUserRoleRowDTO> rows = assembleRows(users.getRecords());
        if ("1".equals(mode)) {
            // 参与：剔除名单内（不参与）的人；total 沿用 PT_USER 总数（近似，略高估，名单为极小子集）
            rows = rows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getEvalEnabled()))
                    .collect(Collectors.toList());
        }
        return PageResult.of(page, pageSize, users.getTotal(), rows);
    }

    /** 归一过滤态：null/"1"->"1"（参与）；"0"->"0"（不参与）；其它->"all"。 */
    private String normalizeEnabledMode(String evalEnabled) {
        if (evalEnabled == null || "1".equals(evalEnabled.trim())) {
            return "1";
        }
        if ("0".equals(evalEnabled.trim())) {
            return "0";
        }
        return "all";
    }

    /** "否"过滤：名单驱动——取全部"不参与"工号，批量解析，内存关键词过滤+内存分页。 */
    private PageResult<EvalUserRoleRowDTO> pageExcludedDriven(String keyword, int page, int pageSize) {
        List<String> excludedIds = evalUserSettingMapper.selectExcludedUserIds();
        if (excludedIds.size() > EXCLUDED_DRIVEN_CAP) {
            log.warn("[EvalUserTagService.pageExcludedDriven] 不参与工号数 {} 超上限 {}，截断", excludedIds.size(), EXCLUDED_DRIVEN_CAP);
            excludedIds = new ArrayList<>(excludedIds.subList(0, EXCLUDED_DRIVEN_CAP));
        }
        if (excludedIds.isEmpty()) {
            return PageResult.of(page, pageSize, 0L, new ArrayList<>());
        }
        List<UserDTO> users = userApi.getUserByEmpIds(excludedIds);
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
     * 导出用：取关键词匹配的全部人员，按"是否参与评价"过滤态分派。
     *
     * @param keyword     关键词（工号/姓名，可空）
     * @param evalEnabled 过滤态：null/"1"=只看参与，"0"=不参与，其它=全部
     * @param cap         最大导出行数
     * @implNote "1"/"全部"模式下 cap 为上游扫描行数上限（过滤前），"1"模式剔除不参与者后实际返回可能略少于 cap。
     */
    public List<EvalUserRoleRowDTO> listForExport(String keyword, String evalEnabled, int cap) {
        String mode = normalizeEnabledMode(evalEnabled);
        if ("0".equals(mode)) {
            // 不参与：排除名单驱动
            List<String> excludedIds = evalUserSettingMapper.selectExcludedUserIds();
            if (excludedIds.size() > cap) {
                log.warn("[EvalUserTagService.listForExport] 不参与工号数 {} 超 cap {}，截断", excludedIds.size(), cap);
                excludedIds = new ArrayList<>(excludedIds.subList(0, cap));
            }
            if (excludedIds.isEmpty()) {
                return new ArrayList<>();
            }
            List<UserDTO> users = userApi.getUserByEmpIds(excludedIds);
            String kw = keyword == null ? "" : keyword.trim();
            if (!kw.isEmpty()) {
                users = users.stream().filter(u -> matchesKeyword(u, kw)).collect(Collectors.toList());
            }
            return assembleRows(users);
        }
        // 参与/全部：PT_USER 翻页累积
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
        if ("1".equals(mode)) {
            // 参与：剔除名单内（不参与）的人
            all = all.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getEvalEnabled()))
                    .collect(Collectors.toList());
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

        // 批量查询排除名单，生成 excludedSet 用于 overlay（在名单内=不参与）
        Set<String> excludedSet = empIds.isEmpty()
                ? Set.of()
                : new HashSet<>(evalUserSettingMapper.selectExcludedUserIdsIn(empIds));

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
                    .filter(t -> Integer.valueOf(1).equals(t.getRoleType()))
                    .findFirst()
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .orElse(null);
            List<EvalUserTagBriefDTO> evalTags = tagRows.stream()
                    .filter(t -> Integer.valueOf(2).equals(t.getRoleType()))
                    .map(t -> new EvalUserTagBriefDTO(t.getTagId(), t.getTagName()))
                    .collect(Collectors.toList());
            row.setBeEvalTag(beEval);
            row.setEvalTags(evalTags);
            // overlay：在排除名单内=不参与(0)，否则=参与(1)（默认参与）
            row.setEvalEnabled(excludedSet.contains(u.getEmpId()) ? 0 : 1);
            rows.add(row);
        }
        return rows;
    }

}
