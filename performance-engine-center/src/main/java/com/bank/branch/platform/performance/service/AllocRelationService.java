package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.facade.assembler.AllocAssembler;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户业绩分配关系 Service（V1.0 只读）.
 *
 * <p>职责:
 * <ul>
 *   <li>对应 {@code AllocApi} 10 个方法的纯查询实现, 不包含任何写入 / 调整逻辑</li>
 *   <li>批量查询使用 Redis 缓存合并策略: 先查缓存命中, 未命中集中回查 DB, 回写缓存</li>
 *   <li>数据范围过滤: {@code listCustomersByEmp} 依据 {@link BizScopeApi#resolveScope}
 *       决定是否为 Mapper 追加 {@code "AND emp_id = '<empId>'"} 片段（仅见自己名下）</li>
 *   <li>版本派生: {@code getLatestAllocVersion} / {@code getAllocVersionAt} 取自
 *       {@link SysControlService} 的 CUST 维度版本, 打包为 {@link AllocVersionDTO}</li>
 * </ul>
 *
 * <p>V1.0 关键约束:
 * <ul>
 *   <li>所有方法 {@code @Transactional(readOnly = true)}, 不做写操作</li>
 *   <li>V1.0 不实现缓存 evict, 仅依赖 TTL 5 分钟自然过期 (V1.2 通过工作流调整审批后补)</li>
 *   <li>缓存 key: {@code perf:alloc:cust:{custId}:{bizKind}}; bizKind 为 null 时占位 "ALL"</li>
 *   <li>空列表也写缓存防穿透</li>
 * </ul>
 *
 * <p>SQL 注入防御: {@code resolveScopeFilter} 返回的片段仅使用来自认证 ThreadLocal 的 empId
 * 拼接, 禁止接受任何 Controller 层用户入参拼入 (同 {@link PerfRunTaskService}).
 */
@Slf4j
@Service
public class AllocRelationService {

    private final CustAllocRelationMapper allocMapper;
    private final SysControlService sysControlService;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;
    private final RedisTemplate<String, Object> redisTemplate;
    /**
     * Q7.2 新增：统一数据范围 SQL 片段生成器.
     * 将 7 种 {@link DataScopeType} 转换为 (scopeFragment, scopeParams) 对给 Mapper,
     * 取代原 {@link #resolveScopeFilter} 字符串拼接方式.
     */
    private final PerfScopeHelper perfScopeHelper;

    /**
     * 构造器注入（手写, 因 Q7.2 新增 helper 字段与其他字段非等价语义）.
     */
    public AllocRelationService(CustAllocRelationMapper allocMapper,
                                SysControlService sysControlService,
                                CurrentUserApi currentUserApi,
                                BizScopeApi bizScopeApi,
                                RedisTemplate<String, Object> redisTemplate,
                                PerfScopeHelper perfScopeHelper) {
        this.allocMapper = allocMapper;
        this.sysControlService = sysControlService;
        this.currentUserApi = currentUserApi;
        this.bizScopeApi = bizScopeApi;
        this.redisTemplate = redisTemplate;
        this.perfScopeHelper = perfScopeHelper;
    }

    /** 缓存 key 前缀. */
    private static final String CACHE_KEY_PREFIX = "perf:alloc:cust:";

    /** bizKind 为 null 时 key 占位. */
    private static final String BIZ_KIND_ALL = "ALL";

    /** 缓存 TTL (V1.0 无 evict, 靠 5 min 自然过期). */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /** 派生版本 DTO 时 listVersionHistory 的最大拉取量 (保持充足但有限). */
    private static final int VERSION_HISTORY_LIMIT = 100;

    // ==================== 基础查询 ====================

    /**
     * 查询客户当前有效的分配关系.
     *
     * @param custId  客户 ID
     * @param bizKind 业务种类, null 表示全部
     * @return 实体列表, 不为 null
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelation> getCurrentAllocations(String custId, String bizKind) {
        Assert.hasText(custId, "custId 不能为空");
        return allocMapper.selectCurrentByCustAndBiz(custId, bizKind, LocalDate.now());
    }

    /**
     * 查询客户在指定日期的分配关系（历史快照, 全业务种类）.
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelation> getAllocationHistory(String custId, LocalDate asOfDate) {
        Assert.hasText(custId, "custId 不能为空");
        Assert.notNull(asOfDate, "asOfDate 不能为空");
        return allocMapper.selectHistoryByCustAsOf(custId, asOfDate);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本当前分配查询.
     *
     * <p>内部委托 {@link #getCurrentAllocations}，再通过 {@link AllocAssembler#toDtoList}
     * 装配成 DTO 列表，使 Controller 不再感知 entity。
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelationDTO> getCurrentAllocationsDto(String custId, String bizKind) {
        return AllocAssembler.toDtoList(getCurrentAllocations(custId, bizKind));
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本历史快照查询.
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelationDTO> getAllocationHistoryDto(String custId, LocalDate asOfDate) {
        return AllocAssembler.toDtoList(getAllocationHistory(custId, asOfDate));
    }

    /**
     * 查询某员工名下当前负责的客户列表（含数据范围过滤）.
     *
     * <p>管理员 {@link DataScopeType#ALL} → filter=null, Mapper 不额外加 emp_id 约束;
     * 非 ALL → 加 "AND emp_id = '<当前登录 empId>'" 片段限制仅见自己名下.
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelation> listCustomersByEmp(String empId, String bizKind) {
        Assert.hasText(empId, "empId 不能为空");
        String dataScopeFilter = resolveScopeFilter();
        return allocMapper.selectByEmpAndBiz(empId, bizKind, LocalDate.now(), dataScopeFilter);
    }

    /**
     * Q7.2 新增：基于 {@link PerfScopeHelper} 的数据范围注入查询.
     *
     * <p>和老方法 {@link #listCustomersByEmp} 的差异：
     * <ul>
     *   <li>老方法只支持"ALL 全见 / 非 ALL 收敛自己"的二值逻辑（字符串拼接 empId）</li>
     *   <li>新方法支持全部 7 种 {@link DataScopeType}, 参数走 {@code #{scopeParams.*}} 预编译
     *       彻底杜绝 SQL 注入</li>
     * </ul>
     *
     * <p>ScopeColumns 约定（Alloc 表）：
     * <ul>
     *   <li>ownerEmpCol = "emp_id" (SELF 用)</li>
     *   <li>assigneeCol = "emp_id" (Alloc 无独立 assignee 列, 映射同 emp_id)</li>
     *   <li>createdByCol = "created_by" (SELF_CREATED 用)</li>
     *   <li>ownerOrgCol = "emp_id" (Alloc 表无 org_code, 降级为按 emp_id 过滤; ORG scope 等价于 SELF)</li>
     * </ul>
     *
     * <p><b>注意</b>：Alloc 表物理上无 org_code 列, 因此 ORG / ORG_SUBTREE scope 会注入
     * 类似 "emp_id = #{orgCode}" 的错乱片段. V1.2 的 Alloc 查询不支持 ORG 级过滤;
     * 本方法使用 {@code Set<String>} 降级到 SELF 语义, ORG/ORG_SUBTREE scope
     * 在 Alloc 表场景下应由上层 Facade 控制或等待 V1.3 引入 emp→org 映射.
     *
     * <p>Fail-Close: 若用户无权限或 ctx 为 null, scopeFragment="1=0", Mapper 查无结果.
     *
     * @param empId   员工工号（主查询主体）
     * @param bizKind 业务种类（nullable）
     * @return 分配关系列表（已经数据范围过滤）
     */
    @Transactional(readOnly = true)
    public List<CustAllocRelation> listCustomersByEmpWithScope(String empId, String bizKind) {
        Assert.hasText(empId, "empId 不能为空");
        String currentEmpId = currentUserApi.getCurrentEmpId();
        // Alloc 表 4 列映射：无 org_code 字段, 这里 ownerEmpCol 用 emp_id 做语义基准
        PerfScopeHelper.ScopeColumns columns = new PerfScopeHelper.ScopeColumns(
                "emp_id",       // ownerEmpCol (SELF)
                "emp_id",       // assigneeCol (Alloc 无独立 assignee 列)
                "created_by",   // createdByCol (SELF_CREATED)
                "emp_id"        // ownerOrgCol (Alloc 表无 org_code, 降级为 emp_id; ORG scope 语义等价 SELF)
        );
        PerfScopeHelper.Fragment frag = perfScopeHelper.getFragment(
                currentEmpId, BizType.PERF_CONFIG, BizAction.LIST, columns);
        return allocMapper.selectByEmpAndBizWithScope(
                empId, bizKind, LocalDate.now(), frag.getSql(), frag.getParams());
    }

    // ==================== 批量查询（含缓存合并）====================

    /**
     * 批量查询多个客户的当前分配关系.
     *
     * <p>缓存合并流程:
     * <ol>
     *   <li>遍历 custIds 先查 Redis: 命中直接填入 result</li>
     *   <li>未命中的集中调一次 {@code selectCurrentByCustIds(missingIds, ...)}</li>
     *   <li>按 custId 分组结果, 即便某 custId 无任何分配也写入空列表防穿透</li>
     *   <li>回写 Redis, TTL 5 min</li>
     * </ol>
     *
     * @param custIds 客户 ID 集合; null / 空 → 直接返回空 Map
     * @param bizKind 业务种类, null 表示全部 (此时 key 以 "ALL" 占位)
     * @return Map&lt;custId, List&lt;CustAllocRelation&gt;&gt;; 每个 custId 都有对应 entry (可能空列表)
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, List<CustAllocRelation>> batchGetCurrentAllocations(Set<String> custIds, String bizKind) {
        if (custIds == null || custIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String bizKindKey = bizKind == null ? BIZ_KIND_ALL : bizKind;
        Map<String, List<CustAllocRelation>> result = new HashMap<>(custIds.size());
        Set<String> missingIds = new HashSet<>();

        // 1) 遍历 custIds 查缓存
        for (String custId : custIds) {
            String cacheKey = buildCacheKey(custId, bizKindKey);
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                // 缓存命中 (包括空列表的穿透防护)
                result.put(custId, (List<CustAllocRelation>) cached);
            } else {
                missingIds.add(custId);
            }
        }

        // 2) 缺失的集中查 DB
        if (!missingIds.isEmpty()) {
            List<CustAllocRelation> dbList = allocMapper.selectCurrentByCustIds(
                    missingIds, bizKind, LocalDate.now(), null);
            // 按 custId 分组
            Map<String, List<CustAllocRelation>> grouped = dbList.stream()
                    .collect(Collectors.groupingBy(CustAllocRelation::getCustId));
            // 对每个 missingId 保证一个 entry (即便 DB 返回空, 也写入空列表防穿透)
            for (String custId : missingIds) {
                List<CustAllocRelation> list = grouped.getOrDefault(custId, Collections.emptyList());
                result.put(custId, list);
                // 3) 回写缓存
                redisTemplate.opsForValue().set(buildCacheKey(custId, bizKindKey), list, CACHE_TTL);
            }
        }

        return result;
    }

    /**
     * 批量查询多个员工名下的客户数汇总.
     *
     * @param empIds  员工工号集合; null / 空 → 空 Map
     * @param bizKind 业务种类 (nullable)
     * @return Map&lt;empId, 客户数&gt;
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countCustomersByEmps(Set<String> empIds, String bizKind) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = allocMapper.countCustomersByEmps(empIds, bizKind, LocalDate.now());
        Map<String, Long> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            String empId = (String) row.get("emp_id");
            Object cntObj = row.get("cust_count");
            long cnt = cntObj instanceof Number ? ((Number) cntObj).longValue() : 0L;
            result.put(empId, cnt);
        }
        return result;
    }

    /**
     * 批量查询多个员工的分配关系汇总 (供 report 聚合报表).
     *
     * <p>对每个员工调一次 {@link CustAllocRelationMapper#selectByEmpAndBiz},
     * 聚合出 去重客户数 / 平均分配比例 / 员工工号 / 业务种类 / 数据基准日.
     *
     * @param empIds   员工工号集合; null / 空 → 空列表
     * @param bizKind  业务种类 (nullable)
     * @param asOfDate 截止日期, null → today
     * @return 聚合 DTO 列表, 每个员工一条
     */
    @Transactional(readOnly = true)
    public List<AllocSummaryDTO> batchSummaryByEmps(Set<String> empIds, String bizKind, LocalDate asOfDate) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDate effectiveAsOf = asOfDate == null ? LocalDate.now() : asOfDate;
        List<AllocSummaryDTO> summaries = new ArrayList<>(empIds.size());
        for (String empId : empIds) {
            // report 聚合不做数据范围过滤 (调用方为 report 或管理员场景)
            List<CustAllocRelation> rels = allocMapper.selectByEmpAndBiz(empId, bizKind, effectiveAsOf, null);
            if (rels == null || rels.isEmpty()) {
                continue;
            }
            // 去重客户数
            long custCount = rels.stream().map(CustAllocRelation::getCustId).distinct().count();
            // 平均比例 (sum / count), 半进位保留 2 位
            BigDecimal ratioSum = rels.stream()
                    .map(CustAllocRelation::getRatio)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgRatio = BigDecimal.ZERO;
            if (!rels.isEmpty()) {
                avgRatio = ratioSum.divide(BigDecimal.valueOf(rels.size()), 2, RoundingMode.HALF_UP);
            }
            summaries.add(AllocSummaryDTO.builder()
                    .empId(empId)
                    .bizKind(bizKind)
                    .custCount(custCount)
                    .avgAllocRatio(avgRatio)
                    .asOfDate(effectiveAsOf)
                    .build());
        }
        return summaries;
    }

    // ==================== 快速判定 ====================

    /**
     * 判断某员工对某客户是否存在当前有效的分配关系.
     *
     * <p>实现: 走 {@code selectByEmpAndBiz} 拉取员工名下列表, 再 filter custId 匹配;
     * 不用 countCustomersByEmps 以保留 custId 粒度判定能力.
     */
    @Transactional(readOnly = true)
    public boolean hasAllocation(String empId, String custId, String bizKind) {
        Assert.hasText(empId, "empId 不能为空");
        Assert.hasText(custId, "custId 不能为空");
        List<CustAllocRelation> rels = allocMapper.selectByEmpAndBiz(empId, bizKind, LocalDate.now(), null);
        if (rels == null || rels.isEmpty()) {
            return false;
        }
        return rels.stream().anyMatch(r -> custId.equals(r.getCustId()));
    }

    /**
     * 统计某员工当前负责的去重客户数.
     */
    @Transactional(readOnly = true)
    public long countCustomersOfEmp(String empId, String bizKind) {
        Assert.hasText(empId, "empId 不能为空");
        return allocMapper.countDistinctCustomers(empId, bizKind, LocalDate.now());
    }

    // ==================== 版本查询 ====================

    /**
     * 查询当前最新的分配关系版本号（派生自 sys_control CUST 维度）.
     *
     * @param bizKind 业务种类, 透传到 DTO 的 bizKind 字段; V1.0 CUST 维度版本不按业务种类分开
     * @return 版本信息
     */
    @Transactional(readOnly = true)
    public AllocVersionDTO getLatestAllocVersion(String bizKind) {
        SysControl sc = sysControlService.getCurrentVersion("CUST");
        return toAllocVersionDTO(sc, bizKind);
    }

    /**
     * 查询某时间点生效的分配关系版本号.
     *
     * <p>实现:
     * <ol>
     *   <li>调 {@link SysControlService#listVersionHistory}("CUST", 100) 获取倒序历史</li>
     *   <li>过滤出 {@code latestDataDate <= asOfDate} 的最近一条</li>
     *   <li>若无任何 &lt;= asOfDate 的版本 → 返回 null</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public AllocVersionDTO getAllocVersionAt(String bizKind, LocalDate asOfDate) {
        Assert.notNull(asOfDate, "asOfDate 不能为空");
        List<SysControl> history = sysControlService.listVersionHistory("CUST", VERSION_HISTORY_LIMIT);
        if (history == null || history.isEmpty()) {
            return null;
        }
        // listVersionHistory 按 latest_data_date 倒序, 遍历取第一个 <= asOfDate 的即可
        for (SysControl sc : history) {
            if (sc.getLatestDataDate() != null && !sc.getLatestDataDate().isAfter(asOfDate)) {
                return toAllocVersionDTO(sc, bizKind);
            }
        }
        return null;
    }

    // ==================== 私有工具 ====================

    /**
     * 生成缓存 key: {@code perf:alloc:cust:{custId}:{bizKindKey}}.
     */
    private String buildCacheKey(String custId, String bizKindKey) {
        return CACHE_KEY_PREFIX + custId + ":" + bizKindKey;
    }

    /**
     * 组装 AllocVersionDTO. publishedAt 取 createdTime (sys_control 无 published_at 字段),
     * publishedBy 取 null (sys_control 无 created_by 字段).
     */
    private AllocVersionDTO toAllocVersionDTO(SysControl sc, String bizKind) {
        if (sc == null) {
            return null;
        }
        return AllocVersionDTO.builder()
                .bizKind(bizKind)
                .scopeDim(sc.getScopeDim())
                .currentVersion(sc.getCurrentVersion())
                .latestDataDate(sc.getLatestDataDate())
                .publishedAt(sc.getCreatedTime())
                .publishedBy(null)
                .build();
    }

    /**
     * 解析当前用户数据范围, 生成 Mapper {@code ${dataScopeFilter}} 片段.
     *
     * <p>null → 管理员全见; 否则返回 "AND emp_id = '<当前登录 empId>'"（仅见自己名下）.
     */
    private String resolveScopeFilter() {
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeType scope = bizScopeApi.resolveScope(empId, BizType.PERF_CONFIG);
        if (scope == DataScopeType.ALL) {
            return null;
        }
        // empId 来自认证 ThreadLocal, 已可信 (见类 Javadoc 的 SQL 注入防御说明).
        return "AND emp_id = '" + empId + "'";
    }
}
