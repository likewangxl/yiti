package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.facade.assembler.RunTaskAssembler;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 绩效任务执行日志服务（V1.0 只读）.
 *
 * <p>职责:
 * <ul>
 *   <li>只读查询 {@code perf_run_task}（主键 / 任务编号 / 条件分页 / 类型日期计数）。</li>
 *   <li>数据范围过滤：{@link #page} 依据 {@link BizScopeApi#resolveScope} 决定
 *       是否为 Mapper 追加 {@code AND started_by = '<empId>'} 过滤片段。</li>
 * </ul>
 *
 * <p>写操作由 V1.1 计算引擎模块提供；本 Service 故意不暴露 create/update/delete 方法。
 *
 * <p><strong>数据范围过滤规则</strong>（对齐 spec §5.4）:
 * <ul>
 *   <li>{@link DataScopeType#ALL}（管理员 / 全局可见）→ 传 {@code null} filter, Mapper 不加 started_by 约束。</li>
 *   <li>其他 DataScopeType（SELF_CREATED / SELF / ORG / ...）→ V1.0 简化为 "仅见自己发起"：
 *       组装 {@code "AND started_by = '<empId>'"} 片段传 Mapper。</li>
 *   <li>V1.1 可根据 DataScopeType 精细化（ORG_SUBTREE 改走 orgCode IN (...) 等），
 *       届时仅需替换 {@link #resolveScopeFilter} 实现，不影响 Service 公共契约。</li>
 * </ul>
 *
 * <p><strong>SQL 注入防御</strong>: filter 片段中的 {@code empId} 来自
 * {@link CurrentUserApi#getCurrentEmpId()}（认证 ThreadLocal 上下文），已经过
 * {@code AuthenticationFilter} 校验，属于可信来源；禁止接受任何 Controller 层用户入参
 * 拼入本片段（由 Mapper Javadoc §SQL 注入注意 统一约束）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfRunTaskService {

    private final PerfRunTaskMapper runTaskMapper;
    private final CurrentUserApi currentUserApi;
    private final BizScopeApi bizScopeApi;

    /**
     * 按主键查询任务日志.
     *
     * @param id 主键
     * @return Optional 包装的任务, 不存在返回 Optional.empty
     */
    @Transactional(readOnly = true)
    public Optional<PerfRunTask> getById(String id) {
        Assert.hasText(id, "id 不能为空");
        return Optional.ofNullable(runTaskMapper.selectById(id));
    }

    /**
     * 按任务编号（对应 DDL {@code task_key}）查询任务日志.
     *
     * @param taskNo 任务编号
     * @return Optional 包装的任务, 不存在返回 Optional.empty
     */
    @Transactional(readOnly = true)
    public Optional<PerfRunTask> getByTaskNo(String taskNo) {
        Assert.hasText(taskNo, "taskNo 不能为空");
        return Optional.ofNullable(runTaskMapper.selectByTaskNo(taskNo));
    }

    /**
     * 条件分页查询（含数据范围过滤）.
     *
     * <p>流程:
     * <ol>
     *   <li>{@link #resolveScopeFilter} 解析当前用户数据范围 → filter 片段（null 表示管理员全见）</li>
     *   <li>{@link PerfRunTaskMapper#countByCondition} 先取总数，total=0 直接返回空分页（跳过 select）</li>
     *   <li>{@link PerfRunTaskMapper#selectByCondition} 取当前页数据</li>
     * </ol>
     *
     * @param taskType 任务类型（nullable）
     * @param taskKey  任务关键键（nullable）
     * @param status   状态（nullable）
     * @param dataDate 数据日期（nullable）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfRunTask> page(String taskType, String taskKey, String status,
                                        LocalDate dataDate, int pageNo, int pageSize) {
        String dataScopeFilter = resolveScopeFilter();
        long total = runTaskMapper.countByCondition(taskType, taskKey, status, dataDate, dataScopeFilter);
        if (total == 0L) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        List<PerfRunTask> records = runTaskMapper.selectByCondition(
                taskType, taskKey, status, dataDate, dataScopeFilter, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 按任务类型 + 数据日期统计任务数
     * （V1.1 SysControl 切版前置校验使用：判断当日是否仍有 RUNNING 任务）.
     *
     * @param taskType 任务类型（必填）
     * @param dataDate 数据日期（必填）
     * @return 任务数
     */
    @Transactional(readOnly = true)
    public long countByTypeAndDate(String taskType, LocalDate dataDate) {
        Assert.hasText(taskType, "taskType 不能为空");
        Assert.notNull(dataDate, "dataDate 不能为空");
        return runTaskMapper.countByTypeAndDate(taskType, dataDate);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本分页查询，内部委托 {@link #page}
     * 并使用 {@link RunTaskAssembler} 装配 DTO。
     *
     * <p>引入理由：Controller 层不得感知 entity（{@code NoEntityInControllerLocalsArchTest}
     * 守护），本方法为 Controller 提供"入 DTO 出 DTO"的纯接口，entity 装配收敛到
     * Service 层完成。
     *
     * @param taskType 任务类型（nullable）
     * @param taskKey  任务关键键（nullable）
     * @param status   状态（nullable）
     * @param dataDate 数据日期（nullable）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     * @return 分页 DTO 结果
     */
    @Transactional(readOnly = true)
    public PageResult<PerfRunTaskDTO> pageDto(String taskType, String taskKey, String status,
                                              LocalDate dataDate, int pageNo, int pageSize) {
        PageResult<PerfRunTask> raw = page(taskType, taskKey, status, dataDate, pageNo, pageSize);
        List<PerfRunTaskDTO> dtos = new ArrayList<>(raw.getRecords().size());
        for (PerfRunTask task : raw.getRecords()) {
            dtos.add(RunTaskAssembler.toDto(task));
        }
        return PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos);
    }

    /**
     * V1.3 R4.1：Controller 专用 DTO 版本详情查询。
     *
     * <p>任务不存在时抛 {@link PerfErrorCode#RUN_TASK_NOT_FOUND}（PERF-40007），
     * 语义与 {@code PerfRunTaskController.getById} 一致，避免 Controller 再持有
     * {@code Optional<PerfRunTask>} 并手工抛异常。
     *
     * @param id 任务主键
     * @return 任务 DTO（绝不返回 null；不存在则抛异常）
     * @throws PerfException {@code RUN_TASK_NOT_FOUND} 任务不存在
     */
    @Transactional(readOnly = true)
    public PerfRunTaskDTO getByIdDto(String id) {
        PerfRunTask task = getById(id)
                .orElseThrow(() -> new PerfException(PerfErrorCode.RUN_TASK_NOT_FOUND, id));
        return RunTaskAssembler.toDto(task);
    }

    /**
     * 解析当前用户的数据范围，生成 Mapper {@code ${dataScopeFilter}} 片段.
     *
     * <p>返回 {@code null} → 管理员全见（Mapper 不追加 started_by 约束）；
     * 否则返回 {@code "AND started_by = '<empId>'"} 片段。
     *
     * <p>V1.1 升级路径：可根据 {@link DataScopeType} 精细化（ORG/ORG_SUBTREE 改走
     * orgCode 过滤），届时仅需改动本方法。
     *
     * @return 数据范围 SQL 片段（nullable）
     */
    private String resolveScopeFilter() {
        String empId = currentUserApi.getCurrentEmpId();
        DataScopeType scope = bizScopeApi.resolveScope(empId, BizType.PERF_CONFIG);
        if (scope == DataScopeType.ALL) {
            return null;
        }
        // empId 来自认证 ThreadLocal, 已可信; 禁止接受用户入参拼入本片段 (见类 Javadoc).
        return "AND started_by = '" + empId + "'";
    }
}
