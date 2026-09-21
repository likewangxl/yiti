package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.portal.adapter.MetricAdapter;
import com.bank.branch.platform.portal.adapter.WorkflowQueryAdapter;
import com.bank.branch.platform.portal.adapter.dto.PortalTodoItem;
import com.bank.branch.platform.portal.api.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A.1 工作台聚合服务
 *
 * <p>使用 CompletableFuture.allOf 进行 5+1 路并行查询（shortcuts、todoCount、recentTodos、
 * unreadNotificationCount、recentNotifications + metricCards），每路独立超时 + 异常降级。</p>
 *
 * <p>任何子查询失败不影响整体返回，错误信息收集到 aggregateErrors 中供前端展示。</p>
 */
@Slf4j
@Service
public class WorkspaceService {

    private static final Duration NORMAL_SOURCE_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration METRIC_SOURCE_TIMEOUT = Duration.ofSeconds(5);

    private final ShortcutService shortcutService;
    private final NotifyApi notifyApi;
    private final WorkflowQueryAdapter workflowQueryAdapter;
    private final MetricAdapter metricAdapter;
    private final CurrentUserApi currentUserApi;
    private final Executor portalAggregateExecutor;
    private final Duration normalSourceTimeout;
    private final Duration metricSourceTimeout;

    /**
     * 构造函数注入，显式使用 @Qualifier 指定线程池
     *
     * <p>notifyApi 标记为 {@code @Autowired(required=false)}，V1 阶段 governance 模块
     * 可能未提供实现，此时自动降级为 0/空列表。</p>
     *
     * @param shortcutService        快捷入口服务
     * @param notifyApi              通知 API（可为 null，V1 降级）
     * @param workflowQueryAdapter   工作流查询适配器
     * @param metricAdapter          绩效指标适配器
     * @param currentUserApi         当前用户 API
     * @param portalAggregateExecutor 工作台聚合专用线程池
     */
    @Autowired
    public WorkspaceService(ShortcutService shortcutService,
                            @Autowired(required = false) NotifyApi notifyApi,
                            WorkflowQueryAdapter workflowQueryAdapter,
                            MetricAdapter metricAdapter,
                            CurrentUserApi currentUserApi,
                            @Qualifier("portalAggregateExecutor") Executor portalAggregateExecutor) {
        this(shortcutService, notifyApi, workflowQueryAdapter, metricAdapter,
                currentUserApi, portalAggregateExecutor,
                NORMAL_SOURCE_TIMEOUT, METRIC_SOURCE_TIMEOUT);
    }

    /** 包私有测试构造器：允许用毫秒级 deadline 覆盖超时测试，不改变生产配置。 */
    WorkspaceService(ShortcutService shortcutService,
                     NotifyApi notifyApi,
                     WorkflowQueryAdapter workflowQueryAdapter,
                     MetricAdapter metricAdapter,
                     CurrentUserApi currentUserApi,
                     Executor portalAggregateExecutor,
                     Duration normalSourceTimeout,
                     Duration metricSourceTimeout) {
        this.shortcutService = shortcutService;
        this.notifyApi = notifyApi;
        this.workflowQueryAdapter = workflowQueryAdapter;
        this.metricAdapter = metricAdapter;
        this.currentUserApi = currentUserApi;
        this.portalAggregateExecutor = portalAggregateExecutor;
        this.normalSourceTimeout = Objects.requireNonNull(normalSourceTimeout, "normalSourceTimeout");
        this.metricSourceTimeout = Objects.requireNonNull(metricSourceTimeout, "metricSourceTimeout");
    }

    /**
     * A.1 工作台数据聚合（5+1 路并行 + 降级）
     *
     * <p>六个来源同时启动；普通来源各自使用 2 秒 deadline，metricCards 独立允许 5 秒。
     * 任一来源失败或超时都降级为默认值，并将对应错误收集到 aggregateErrors。</p>
     *
     * @return 工作台聚合数据 DTO
     */
    public WorkspaceDTO getWorkspace() {
        String empId = currentUserApi.getCurrentEmpId();
        Map<String, String> errors = new ConcurrentHashMap<>();

        // 指标先提交，避免有界执行器排队时被普通来源占满；所有 future 仍同时开始计时。
        CompletableFuture<List<PortalMetricCard>> fMetrics = supplyAsync(
                () -> metricAdapter.fetchForWorkspace(empId), "metricCards",
                metricSourceTimeout, errors);

        CompletableFuture<List<ShortcutDTO>> fShortcuts = supplyAsync(
                () -> shortcutService.listMyShortcuts(), "shortcuts",
                normalSourceTimeout, errors);

        CompletableFuture<Integer> fTodoCount = supplyAsync(
                () -> workflowQueryAdapter.countPending(empId), "todoCount",
                normalSourceTimeout, errors);

        CompletableFuture<List<TodoItemDTO>> fRecentTodos = supplyAsync(
                () -> workflowQueryAdapter.listPending(empId, 5).stream()
                        .map(this::toTodoItemDTO).collect(Collectors.toList()),
                "recentTodos", normalSourceTimeout, errors);

        // notifyApi 可为 null（V1 降级），直接返回默认值
        CompletableFuture<Integer> fUnreadCount = notifyApi != null
                ? supplyAsync(() -> notifyApi.countUnread(empId), "unreadNotificationCount",
                normalSourceTimeout, errors)
                : CompletableFuture.completedFuture(0);

        CompletableFuture<List<NotificationItemDTO>> fRecentNotif = notifyApi != null
                ? supplyAsync(() -> {
                    PageRequest page = new PageRequest();
                    page.setPageNo(1);
                    page.setPageSize(10);
                    PageResult<NotificationDTO> result = notifyApi.queryNotifications(empId, null, page);
                    return result.getRecords().stream()
                            .map(this::toNotificationItemDTO).collect(Collectors.toList());
                }, "recentNotifications", normalSourceTimeout, errors)
                : CompletableFuture.completedFuture(Collections.emptyList());

        // 每个 future 已有自身 deadline；allOf 只等待这些 bounded futures 完成，不提前截断 metrics。
        try {
            CompletableFuture.allOf(fShortcuts, fTodoCount, fRecentTodos, fUnreadCount, fRecentNotif, fMetrics)
                    .join();
        } catch (Exception ex) {
            log.warn("[WorkspaceService] aggregate timeout or error", ex);
        }

        return WorkspaceDTO.builder()
                .shortcuts(getOrDefault(fShortcuts, Collections.emptyList()))
                .todoCount(getOrDefault(fTodoCount, 0))
                .recentTodos(getOrDefault(fRecentTodos, Collections.emptyList()))
                .unreadNotificationCount(getOrDefault(fUnreadCount, 0))
                .recentNotifications(getOrDefault(fRecentNotif, Collections.emptyList()))
                .metricCards(getOrDefault(fMetrics, Collections.emptyList()))
                .aggregateErrors(errors.isEmpty() ? Collections.emptyMap() : new HashMap<>(errors))
                .build();
    }

    /**
     * 获取用户待办数量
     *
     * <p>直接委托给 WorkflowQueryAdapter，由适配器内部处理降级。</p>
     *
     * @param empId 员工工号
     * @return 待办总数
     */
    public int getTodoCount(String empId) {
        return workflowQueryAdapter.countPending(empId);
    }

    /**
     * 获取用户未读通知数量
     *
     * <p>委托给 NotifyApi.countUnread。当 NotifyApi 为 null（V1 阶段）时返回 0。</p>
     *
     * @param empId 员工工号
     * @return 未读通知数量，NotifyApi 不可用时返回 0
     */
    public int getUnreadNotificationCount(String empId) {
        if (notifyApi == null) {
            log.debug("NotifyApi bean is null, returning 0 (V1 fallback)");
            return 0;
        }
        try {
            return notifyApi.countUnread(empId);
        } catch (Exception ex) {
            log.warn("NotifyApi.countUnread failed for empId={}", empId, ex);
            return 0;
        }
    }

    /**
     * 通用异步任务封装：在有界执行器提交后启动独立 deadline，异常和超时写入 errors。
     *
     * @param supplier 数据提供者
     * @param name     任务名称（用于 error map 的 key）
     * @param timeout  当前来源 deadline
     * @param errors   错误收集 map
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, String name,
                                                  Duration timeout,
                                                  Map<String, String> errors) {
        CompletableFuture<T> task;
        try {
            task = CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (Exception ex) {
                    throw new CompletionException(ex);
                }
            }, portalAggregateExecutor);
        } catch (RejectedExecutionException ex) {
            errors.putIfAbsent(name, failureMessage(ex));
            return CompletableFuture.completedFuture(null);
        }
        return task.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    errors.putIfAbsent(name, failureMessage(ex));
                    return null;
                });
    }

    private String failureMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof TimeoutException) {
            return "查询超时，请刷新重试";
        }
        if (cause instanceof RejectedExecutionException) {
            return "服务繁忙，请稍后重试";
        }
        String message = cause.getMessage();
        return message != null && !message.isBlank()
                ? message
                : cause.getClass().getSimpleName();
    }

    /**
     * 安全获取 CompletableFuture 的结果，null 时返回默认值
     *
     * <p>调用方只在 allOf 等待 bounded futures 完成后读取，因此 join 不会把 pending
     * 误判为空列表；future 的异常已在 supplyAsync 中转为默认值并登记错误。</p>
     *
     * @param future       异步任务
     * @param defaultValue 默认值
     * @param <T>          返回值类型
     * @return 任务结果或默认值
     */
    private <T> T getOrDefault(CompletableFuture<T> future, T defaultValue) {
        T result = future.join();
        return result != null ? result : defaultValue;
    }

    /**
     * PortalTodoItem → TodoItemDTO 转换
     *
     * <p>initiatedTime 为 String 类型，需要通过 LocalDateTime.parse() 转换，
     * 解析失败时设为 null。</p>
     *
     * @param item 工作流待办项
     * @return 工作台待办 DTO
     */
    private TodoItemDTO toTodoItemDTO(PortalTodoItem item) {
        LocalDateTime time = null;
        try {
            if (item.getInitiatedTime() != null) {
                time = LocalDateTime.parse(item.getInitiatedTime());
            }
        } catch (Exception e) {
            log.debug("[WorkspaceService] initiatedTime 解析失败: {}", item.getInitiatedTime());
        }
        return TodoItemDTO.builder()
                .taskId(item.getTaskId())
                .processInstanceId(item.getProcessInstanceId())
                .processName(item.getProcessName())
                .taskTitle(item.getTaskTitle())
                .initiatorName(item.getInitiatorName())
                .initiatedTime(time)
                .lightStatus(item.getLightStatus())
                .overdueInfo(item.getOverdueInfo())
                .bizDetailUrl(item.getBizDetailUrl())
                .build();
    }

    /**
     * NotificationDTO → NotificationItemDTO 转换
     *
     * <p>isRead(Boolean) → readStatus("READ"/"UNREAD")；
     * createdTime(String) → sentTime(LocalDateTime)，解析失败时设为 null。</p>
     *
     * @param dto 通知 DTO
     * @return 工作台通知项 DTO
     */
    private NotificationItemDTO toNotificationItemDTO(NotificationDTO dto) {
        LocalDateTime time = null;
        try {
            if (dto.getCreatedTime() != null) {
                time = LocalDateTime.parse(dto.getCreatedTime());
            }
        } catch (Exception e) {
            log.debug("[WorkspaceService] createdTime 解析失败: {}", dto.getCreatedTime());
        }
        return NotificationItemDTO.builder()
                .notificationId(dto.getId())
                .title(dto.getTitle())
                .summary(dto.getContent())
                .sentTime(time)
                .readStatus(Boolean.TRUE.equals(dto.getIsRead()) ? "READ" : "UNREAD")
                .bizType(dto.getBizType())
                .bizId(dto.getBizId())
                .bizDetailUrl(dto.getLinkUrl())
                .build();
    }
}
