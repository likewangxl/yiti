package com.bank.branch.platform.redengine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentStatus;
import com.bank.branch.platform.redengine.api.dto.ReTaskStatus;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.entity.ReTask;
import com.bank.branch.platform.redengine.entity.ReTaskBranchAssignment;
import com.bank.branch.platform.redengine.entity.ReTaskInstance;
import com.bank.branch.platform.redengine.entity.ReTaskSubmission;
import com.bank.branch.platform.redengine.entity.ReTaskSubmissionFile;
import com.bank.branch.platform.redengine.entity.ReTaskTarget;
import com.bank.branch.platform.redengine.entity.ReTaskTodo;
import com.bank.branch.platform.redengine.entity.ReUserPartyMap;
import com.bank.branch.platform.redengine.mapper.RePartyOrgMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskBranchAssignmentMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskInstanceMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionFileMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskSubmissionMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTargetMapper;
import com.bank.branch.platform.redengine.mapper.ReTaskTodoMapper;
import com.bank.branch.platform.redengine.mapper.ReUserPartyMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务实例分配实现。
 *
 * <p>任务对象是“支部”而不是用户列表：全部/指定支部先解析支部内平台报送员，
 * 指定员工则按员工党组织映射合并为每支部一条 assignment，并且只给指定员工创建待办。
 * 任务和待办均以唯一键作幂等边界，避免调度补偿或多节点发布重复生成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReTaskAssignmentServiceImpl implements ReTaskAssignmentService {

    /** 平台权限角色编码；RE_TASK_TODO 保存的是稳定的业务角色快照 REPORTER。 */
    static final String REPORTER_PLATFORM_ROLE = "R_RE_REPORT";
    static final String REPORTER_TODO_ROLE = "REPORTER";
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReTaskTargetMapper targetMapper;
    private final RePartyOrgMapper partyOrgMapper;
    private final ReUserPartyMapMapper userPartyMapMapper;
    private final ReTaskBranchAssignmentMapper assignmentMapper;
    private final ReTaskTodoMapper todoMapper;
    private final ReTaskInstanceMapper instanceMapper;
    private final ReTaskSubmissionMapper submissionMapper;
    private final ReTaskSubmissionFileMapper submissionFileMapper;
    private final UserApi userApi;

    /**
     * 幂等生成一个任务实例的支部分配和报送员待办。
     *
     * @param task     已发布任务定义
     * @param instance 任务实例
     * @return 已存在及本次生成的支部分配
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReTaskBranchAssignment> ensureAssignments(ReTask task, ReTaskInstance instance) {
        requireTaskAndInstance(task, instance);

        List<ReTaskTarget> targets = targetMapper.selectList(
                new LambdaQueryWrapper<ReTaskTarget>().eq(ReTaskTarget::getTaskId, task.getId()));
        Map<Long, RePartyOrg> orgCache = new HashMap<>();
        Set<Long> branchIds = resolveBranchIds(task, targets, orgCache);
        Map<Long, Set<String>> employeeIdsByBranch = resolveTodoEmployees(task, targets, branchIds, orgCache);

        List<ReTaskBranchAssignment> result = new ArrayList<>();
        for (Long branchId : branchIds.stream().sorted().toList()) {
            ReTaskBranchAssignment assignment = findOrCreateAssignment(instance, branchId);
            result.add(assignment);

            Set<String> employeeIds = employeeIdsByBranch.getOrDefault(branchId, Set.of());
            for (String employeeId : employeeIds.stream().filter(this::hasText).sorted().toList()) {
                ensureReporterTodo(assignment, employeeId, instance);
            }
        }
        return result;
    }

    /**
     * 分页查询任务详情中的支部填报汇总，并批量装配支部、提交人及附件快照。
     * <p>该实现只查询本任务实例下的 assignment，过滤条件先作用于本域数据后再分页；
     * 展示姓名由 UserApi 批量补齐，不直接跨模块访问 PT_USER。</p>
     *
     * @param taskId 任务定义 ID
     * @param query  分页和筛选条件
     * @return 支部填报汇总页
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReTaskAssignmentDTO> pageAssignments(Long taskId, ReTaskAssignmentPageQueryDTO query) {
        if (taskId == null) {
            throw new BizException("RE-40010", "任务不存在");
        }
        ReTaskAssignmentPageQueryDTO normalized = query == null ? new ReTaskAssignmentPageQueryDTO() : query;
        int pageNo = normalizePageNo(normalized.getPageNo());
        int pageSize = normalizePageSize(normalized.getPageSize());

        List<ReTaskInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<ReTaskInstance>()
                        .eq(ReTaskInstance::getTaskId, taskId)
                        .orderByDesc(ReTaskInstance::getWindowStartAt)
                        .orderByDesc(ReTaskInstance::getId));
        if (instances == null || instances.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        List<Long> instanceIds = instances.stream()
                .map(ReTaskInstance::getId)
                .filter(Objects::nonNull)
                .toList();
        if (instanceIds.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }
        List<ReTaskBranchAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<ReTaskBranchAssignment>()
                        .in(ReTaskBranchAssignment::getTaskInstanceId, instanceIds)
                        .eq(normalized.getBranchId() != null,
                                ReTaskBranchAssignment::getBranchId, normalized.getBranchId())
                        .eq(normalized.getStatus() != null,
                                ReTaskBranchAssignment::getStatus, normalized.getStatus().name())
                        .ge(normalized.getSubmittedStartAt() != null,
                                ReTaskBranchAssignment::getLastSubmittedAt, normalized.getSubmittedStartAt())
                        .le(normalized.getSubmittedEndAt() != null,
                                ReTaskBranchAssignment::getLastSubmittedAt, normalized.getSubmittedEndAt())
                        .orderByDesc(ReTaskBranchAssignment::getLastSubmittedAt)
                        .orderByDesc(ReTaskBranchAssignment::getId));
        if (assignments == null || assignments.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, List.of());
        }

        Map<Long, ReTaskInstance> instanceById = instances.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ReTaskInstance::getId, Function.identity(), (first, ignored) -> first));
        List<Long> assignmentIds = assignments.stream()
                .map(ReTaskBranchAssignment::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, ReTaskSubmission> submissionByAssignment = latestSubmissions(assignmentIds);
        Map<Long, List<ReTaskSubmissionFile>> filesBySubmission = filesBySubmission(submissionByAssignment.values());
        Map<Long, String> branchNameById = branchNames(assignments);
        Map<String, String> userNameById = userNames(submissionByAssignment.values());

        String keyword = normalizeText(normalized.getKeyword());
        List<ReTaskAssignmentDTO> rows = assignments.stream()
                .map(assignment -> toAssignmentDTO(taskId, assignment, instanceById,
                        submissionByAssignment, filesBySubmission, branchNameById, userNameById))
                .filter(row -> matchesKeyword(row, keyword))
                .toList();

        long total = rows.size();
        int from = Math.min((pageNo - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        return PageResult.of(pageNo, pageSize, total, rows.subList(from, to));
    }

    /** 校验任务实例主键，避免在异常数据下生成无法关联的孤儿记录。 */
    private void requireTaskAndInstance(ReTask task, ReTaskInstance instance) {
        if (task == null || task.getId() == null) {
            throw new BizException("RE-40010", "任务不存在");
        }
        if (task.getStatus() != ReTaskStatus.PUBLISHED) {
            throw new BizException("RE-40303", "任务未发布，不能生成任务分配");
        }
        if (instance == null || instance.getId() == null || !task.getId().equals(instance.getTaskId())) {
            throw new BizException("RE-40011", "任务实例与任务不匹配");
        }
    }

    /** 根据任务对象配置展开目标支部；全部党支部只读取组织树的二级节点。 */
    private Set<Long> resolveBranchIds(ReTask task, List<ReTaskTarget> targets,
                                       Map<Long, RePartyOrg> orgCache) {
        String audience = normalizeAudience(task.getAudienceType());
        if ("ALL_BRANCHES".equals(audience) || "ALL_BRANCH".equals(audience)) {
            List<RePartyOrg> orgs = partyOrgMapper.selectList(null);
            if (orgs == null) {
                return Set.of();
            }
            return orgs.stream()
                    .filter(this::isBranch)
                    .map(RePartyOrg::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        if ("SPECIFIED_BRANCHES".equals(audience) || "SPECIFIED_BRANCH".equals(audience)) {
            Set<Long> result = new LinkedHashSet<>();
            for (ReTaskTarget target : nullToEmpty(targets)) {
                if (!"BRANCH".equalsIgnoreCase(target.getTargetType())
                        && !"SPECIFIED_BRANCH".equalsIgnoreCase(target.getTargetType())) {
                    continue;
                }
                Long branchId = target.getBranchId();
                if (branchId == null) {
                    throw new BizException("RE-40012", "指定党支部不能为空");
                }
                RePartyOrg branch = loadOrg(branchId, orgCache);
                if (!isBranch(branch)) {
                    throw new BizException("RE-40012", "指定对象不是党支部");
                }
                result.add(branchId);
            }
            return result;
        }

        if ("SPECIFIED_EMPLOYEES".equals(audience) || "SPECIFIED_EMPLOYEE".equals(audience)) {
            Set<Long> result = new LinkedHashSet<>();
            for (ReTaskTarget target : nullToEmpty(targets)) {
                if (target.getEmployeeId() == null || target.getEmployeeId().isBlank()) {
                    throw new BizException("RE-40013", "指定员工不能为空");
                }
                ReUserPartyMap mapping = findUserMapping(target.getEmployeeId());
                Long branchId = resolveBranchId(mapping == null ? null : mapping.getPartyOrgId(), orgCache);
                if (branchId == null) {
                    throw new BizException("RE-40013", "指定员工未绑定党支部");
                }
                result.add(branchId);
            }
            return result;
        }
        throw new BizException("RE-40014", "任务对象范围不受支持");
    }

    /** 为每个目标支部解析应收到待办的员工集合。 */
    private Map<Long, Set<String>> resolveTodoEmployees(ReTask task, List<ReTaskTarget> targets,
                                                         Set<Long> branchIds,
                                                         Map<Long, RePartyOrg> orgCache) {
        String audience = normalizeAudience(task.getAudienceType());
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        for (Long branchId : branchIds) {
            result.put(branchId, new LinkedHashSet<>());
        }

        if ("SPECIFIED_EMPLOYEES".equals(audience) || "SPECIFIED_EMPLOYEE".equals(audience)) {
            for (ReTaskTarget target : nullToEmpty(targets)) {
                if (target.getEmployeeId() == null || target.getEmployeeId().isBlank()) {
                    continue;
                }
                ReUserPartyMap mapping = findUserMapping(target.getEmployeeId());
                Long branchId = resolveBranchId(mapping == null ? null : mapping.getPartyOrgId(), orgCache);
                if (branchId != null && result.containsKey(branchId)) {
                    result.get(branchId).add(target.getEmployeeId().trim());
                }
            }
            return result;
        }

        List<String> reporterIds = userApi.getEmpIdsByRoleCode(REPORTER_PLATFORM_ROLE);
        if (reporterIds == null || reporterIds.isEmpty()) {
            return result;
        }
        for (String reporterId : reporterIds) {
            if (!hasText(reporterId)) {
                continue;
            }
            ReUserPartyMap mapping = findUserMapping(reporterId);
            Long branchId = resolveBranchId(mapping == null ? null : mapping.getPartyOrgId(), orgCache);
            if (branchId != null && result.containsKey(branchId)) {
                result.get(branchId).add(reporterId.trim());
            }
        }
        return result;
    }

    /** 按任务实例和支部唯一键读取或新增 assignment。 */
    private ReTaskBranchAssignment findOrCreateAssignment(ReTaskInstance instance, Long branchId) {
        ReTaskBranchAssignment existing = assignmentMapper.selectOne(
                new LambdaQueryWrapper<ReTaskBranchAssignment>()
                        .eq(ReTaskBranchAssignment::getTaskInstanceId, instance.getId())
                        .eq(ReTaskBranchAssignment::getBranchId, branchId));
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = now();
        ReTaskBranchAssignment assignment = new ReTaskBranchAssignment();
        assignment.setTaskInstanceId(instance.getId());
        assignment.setBranchId(branchId);
        assignment.setStatus(ReTaskAssignmentStatus.UNREPORTED.name());
        assignment.setCurrentVersion(0);
        assignment.setCreateTime(now);
        assignment.setUpdateTime(now);
        try {
            assignmentMapper.insert(assignment);
            if (assignment.getId() == null) {
                throw new BizException("RE-50010", "任务分配保存失败");
            }
            return assignment;
        } catch (DuplicateKeyException duplicate) {
            // 调度补偿与发布可能并发，唯一键冲突只代表另一节点已经完成本步；回读赢家。
            ReTaskBranchAssignment winner = assignmentMapper.selectOne(
                    new LambdaQueryWrapper<ReTaskBranchAssignment>()
                            .eq(ReTaskBranchAssignment::getTaskInstanceId, instance.getId())
                            .eq(ReTaskBranchAssignment::getBranchId, branchId));
            if (winner == null) {
                throw duplicate;
            }
            return winner;
        }
    }

    /** 按 assignment、员工和角色唯一键创建员工待办。 */
    private void ensureReporterTodo(ReTaskBranchAssignment assignment, String employeeId,
                                    ReTaskInstance instance) {
        ReTaskTodo existing = todoMapper.selectOne(
                new LambdaQueryWrapper<ReTaskTodo>()
                        .eq(ReTaskTodo::getAssignmentId, assignment.getId())
                        .eq(ReTaskTodo::getEmployeeId, employeeId)
                        .eq(ReTaskTodo::getRoleCode, REPORTER_TODO_ROLE));
        if (existing != null) {
            return;
        }
        LocalDateTime now = now();
        ReTaskTodo todo = new ReTaskTodo();
        todo.setAssignmentId(assignment.getId());
        todo.setEmployeeId(employeeId);
        todo.setRoleCode(REPORTER_TODO_ROLE);
        todo.setStatus("PENDING");
        todo.setAvailableAt(instance.getWindowStartAt());
        todo.setCreateTime(now);
        todo.setUpdateTime(now);
        try {
            todoMapper.insert(todo);
        } catch (DuplicateKeyException duplicate) {
            // 唯一键是幂等边界；另一个节点已创建时无需覆盖其完成状态。
            log.debug("任务待办已由并发节点创建 assignmentId={} employeeId={}",
                    assignment.getId(), employeeId);
        }
    }

    /** 查找员工的党组织绑定；映射缺失交由调用方按目标类型 fail-close。 */
    private ReUserPartyMap findUserMapping(String employeeId) {
        return userPartyMapMapper.selectOne(new LambdaQueryWrapper<ReUserPartyMap>()
                .eq(ReUserPartyMap::getUserId, employeeId.trim()));
    }

    /** 从任意党组织节点向上寻找 orgLevel=2 的党支部。 */
    private Long resolveBranchId(Long partyOrgId, Map<Long, RePartyOrg> orgCache) {
        if (partyOrgId == null) {
            return null;
        }
        Set<Long> visited = new HashSet<>();
        Long currentId = partyOrgId;
        while (currentId != null && visited.add(currentId)) {
            RePartyOrg org = loadOrg(currentId, orgCache);
            if (org == null) {
                return null;
            }
            if (isBranch(org)) {
                return org.getId();
            }
            currentId = org.getParentId();
        }
        return null;
    }

    private RePartyOrg loadOrg(Long id, Map<Long, RePartyOrg> orgCache) {
        if (id == null) {
            return null;
        }
        if (orgCache.containsKey(id)) {
            return orgCache.get(id);
        }
        RePartyOrg org = partyOrgMapper.selectById(id);
        orgCache.put(id, org);
        return org;
    }

    private boolean isBranch(RePartyOrg org) {
        return org != null && org.getId() != null && Integer.valueOf(2).equals(org.getOrgLevel());
    }

    private Map<Long, ReTaskSubmission> latestSubmissions(Collection<Long> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmission>()
                        .in(ReTaskSubmission::getAssignmentId, assignmentIds));
        if (submissions == null || submissions.isEmpty()) {
            return Map.of();
        }
        return submissions.stream()
                .filter(item -> item.getAssignmentId() != null)
                .collect(Collectors.toMap(ReTaskSubmission::getAssignmentId, Function.identity(),
                        (first, second) -> versionOf(first) >= versionOf(second) ? first : second));
    }

    private Map<Long, List<ReTaskSubmissionFile>> filesBySubmission(Collection<ReTaskSubmission> submissions) {
        List<Long> submissionIds = submissions.stream()
                .map(ReTaskSubmission::getId)
                .filter(Objects::nonNull)
                .toList();
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        List<ReTaskSubmissionFile> files = submissionFileMapper.selectList(
                new LambdaQueryWrapper<ReTaskSubmissionFile>()
                        .in(ReTaskSubmissionFile::getSubmissionId, submissionIds)
                        .orderByAsc(ReTaskSubmissionFile::getSortNo)
                        .orderByAsc(ReTaskSubmissionFile::getId));
        if (files == null || files.isEmpty()) {
            return Map.of();
        }
        return files.stream().filter(file -> file.getSubmissionId() != null)
                .collect(Collectors.groupingBy(ReTaskSubmissionFile::getSubmissionId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, String> branchNames(Collection<ReTaskBranchAssignment> assignments) {
        Map<Long, String> result = new HashMap<>();
        for (ReTaskBranchAssignment assignment : assignments) {
            if (assignment.getBranchId() == null || result.containsKey(assignment.getBranchId())) {
                continue;
            }
            RePartyOrg org = partyOrgMapper.selectById(assignment.getBranchId());
            if (org != null) {
                result.put(assignment.getBranchId(), org.getOrgName());
            }
        }
        return result;
    }

    private Map<String, String> userNames(Collection<ReTaskSubmission> submissions) {
        List<String> ids = submissions.stream()
                .map(ReTaskSubmission::getSubmitterId)
                .filter(this::hasText)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<UserDTO> users = userApi.getUserByEmpIds(ids);
        if (users == null) {
            return Map.of();
        }
        return users.stream().filter(user -> user.getEmpId() != null)
                .collect(Collectors.toMap(UserDTO::getEmpId, this::displayName,
                        (first, ignored) -> first));
    }

    private ReTaskAssignmentDTO toAssignmentDTO(Long taskId, ReTaskBranchAssignment assignment,
                                                 Map<Long, ReTaskInstance> instanceById,
                                                 Map<Long, ReTaskSubmission> submissionByAssignment,
                                                 Map<Long, List<ReTaskSubmissionFile>> filesBySubmission,
                                                 Map<Long, String> branchNameById,
                                                 Map<String, String> userNameById) {
        ReTaskAssignmentDTO dto = new ReTaskAssignmentDTO();
        dto.setTaskId(taskId);
        dto.setTaskInstanceId(assignment.getTaskInstanceId());
        dto.setAssignmentId(assignment.getId());
        dto.setBranchId(assignment.getBranchId());
        dto.setBranchName(branchNameById.get(assignment.getBranchId()));
        ReTaskAssignmentStatus status = parseAssignmentStatus(assignment.getStatus());
        dto.setStatus(status);
        dto.setIsUnreported(status == ReTaskAssignmentStatus.UNREPORTED);

        ReTaskSubmission submission = submissionByAssignment.get(assignment.getId());
        if (submission != null) {
            dto.setSubmitterId(submission.getSubmitterId());
            dto.setSubmitterName(userNameById.get(submission.getSubmitterId()));
            dto.setSubmittedAt(submission.getSubmittedAt());
            dto.setContent(submission.getContentText());
            dto.setFormData(submission.getFormData());
            dto.setFiles(filesBySubmission.getOrDefault(submission.getId(), List.of()).stream()
                    .map(this::toAttachment)
                    .toList());
        } else {
            dto.setFiles(List.of());
        }
        // 当前实例在读取前已被固定，保留局部变量使用以明确窗口与 assignment 的数据边界。
        if (instanceById.get(assignment.getTaskInstanceId()) == null) {
            throw new BizException("RE-40011", "任务实例不存在");
        }
        return dto;
    }

    private com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO toAttachment(ReTaskSubmissionFile file) {
        com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO dto =
                new com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO();
        dto.setId(file.getId());
        dto.setFileId(file.getFileObjectId());
        dto.setFileName(file.getFileName());
        dto.setFileSize(file.getFileSize());
        dto.setFileType(file.getFileType());
        dto.setSortNo(file.getSortNo());
        return dto;
    }

    private boolean matchesKeyword(ReTaskAssignmentDTO row, String keyword) {
        if (!hasText(keyword)) {
            return true;
        }
        String value = keyword.toLowerCase();
        return contains(row.getBranchName(), value)
                || contains(row.getSubmitterName(), value)
                || contains(row.getSubmitterId(), value)
                || contains(row.getContent(), value);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }

    private ReTaskAssignmentStatus parseAssignmentStatus(String status) {
        if (!hasText(status)) {
            return null;
        }
        try {
            return ReTaskAssignmentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int versionOf(ReTaskSubmission submission) {
        return submission.getVersionNo() == null ? 0 : submission.getVersionNo();
    }

    private String displayName(UserDTO user) {
        if (hasText(user.getDisplayName())) {
            return user.getDisplayName();
        }
        return hasText(user.getUsername()) ? user.getUsername() : user.getEmpId();
    }

    private String normalizeAudience(String audience) {
        return audience == null ? "" : audience.trim().toUpperCase();
    }

    private String normalizeText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private int normalizePageNo(long pageNo) {
        return pageNo < 1 ? 1 : (int) Math.min(pageNo, Integer.MAX_VALUE);
    }

    private int normalizePageSize(long pageSize) {
        return pageSize < 1 ? 20 : (int) Math.min(pageSize, 200);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BEIJING_ZONE);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
