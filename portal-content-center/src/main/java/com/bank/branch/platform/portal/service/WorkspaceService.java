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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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

    private final ShortcutService shortcutService;
    private final NotifyApi notifyApi;
    private final WorkflowQueryAdapter workflowQueryAdapter;
    private final MetricAdapter metricAdapter;
    private final CurrentUserApi currentUserApi;
    private final Executor portalAggregateExecutor;

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
    public WorkspaceService(ShortcutService shortcutService,
                            @Autowired(required = false) NotifyApi notifyApi,
                            WorkflowQueryAdapter workflowQueryAdapter,
                            MetricAdapter metricAdapter,
                            CurrentUserApi currentUserApi,
                            @Qualifier("portalAggregateExecutor") Executor portalAggregateExecutor) {
        this.shortcutService = shortcutService;
        this.notifyApi = notifyApi;
        this.workflowQueryAdapter = workflowQueryAdapter;
        this.metricAdapter = metricAdapter;
        this.currentUserApi = currentUserApi;
        this.portalAggregateExecutor = portalAggregateExecutor;
    }

    /**
     * A.1 工作台数据聚合（5+1 路并行 + 降级）
     *
     * <p>并行查询 shortcuts、todoCount、recentTodos、unreadNotificationCount、
     * recentNotifications、metricCards 六个维度数据，整体超时 2 秒。
     * 任何子查询失败时降级为默认值（0 或空列表），并将错误收集到 aggregateErrors。</p>
     *
     * @return 工作台聚合数据 DTO
     */
    public WorkspaceDTO getWorkspace() {
        String empId = currentUserApi.getCurrentEmpId();
        Map<String, String> errors = new ConcurrentHashMap<>();

        // 6 个 CompletableFuture 并行执行
        CompletableFuture<List<ShortcutDTO>> fShortcuts = supplyAsync(
                () -> shortcutService.listMyShortcuts(), "shortcuts", errors);

        CompletableFuture<Integer> fTodoCount = supplyAsync(
                () -> workflowQueryAdapter.countPending(empId), "todoCount", errors);

        CompletableFuture<List<TodoItemDTO>> fRecentTodos = supplyAsync(
                () -> workflowQueryAdapter.listPending(empId, 5).stream()
                        .map(this::toTodoItemDTO).collect(Collectors.toList()),
                "recentTodos", errors);

        // notifyApi 可为 null（V1 降级），直接返回默认值
        CompletableFuture<Integer> fUnreadCount = notifyApi != null
                ? supplyAsync(() -> notifyApi.countUnread(empId), "unreadNotificationCount", errors)
                : CompletableFuture.completedFuture(0);

        CompletableFuture<List<NotificationItemDTO>> fRecentNotif = notifyApi != null
                ? supplyAsync(() -> {
                    PageRequest page = new PageRequest();
                    page.setPageNo(1);
                    page.setPageSize(5);
                    PageResult<NotificationDTO> result = notifyApi.queryNotifications(empId, false, page);
                    return result.getRecords().stream()
                            .map(this::toNotificationItemDTO).collect(Collectors.toList());
                }, "recentNotifications", errors)
                : CompletableFuture.completedFuture(Collections.emptyList());

        CompletableFuture<List<PortalMetricCard>> fMetrics = supplyAsync(
                () -> metricAdapter.fetch(empId), "metricCards", errors);

        // 整体超时 2 秒
        try {
            CompletableFuture.allOf(fShortcuts, fTodoCount, fRecentTodos, fUnreadCount, fRecentNotif, fMetrics)
                    .orTimeout(2, TimeUnit.SECONDS)
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
     * 通用异步任务封装，自动捕获异常放入 errors map
     *
     * @param supplier 数据提供者
     * @param name     任务名称（用于 error map 的 key）
     * @param errors   错误收集 map
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, String name,
                                                  Map<String, String> errors) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                errors.put(name, msg);
                throw new CompletionException(ex);
            }
        }, portalAggregateExecutor)
        .exceptionally(ex -> {
            errors.putIfAbsent(name, "error");
            return null;
        });
    }

    /**
     * 安全获取 CompletableFuture 的结果，null 时返回默认值
     *
     * <p>当 exceptionally 处理后 future 以 null 完成时，getNow 返回 null 而非 fallback，
     * 因此需要额外的 null 检查。</p>
     *
     * @param future       异步任务
     * @param defaultValue 默认值
     * @param <T>          返回值类型
     * @return 任务结果或默认值
     */
    private <T> T getOrDefault(CompletableFuture<T> future, T defaultValue) {
        T result = future.getNow(defaultValue);
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
