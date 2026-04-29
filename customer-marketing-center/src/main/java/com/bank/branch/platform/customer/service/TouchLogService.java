package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.TouchLog;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TouchTaskStatus;
import com.bank.branch.platform.customer.mapper.TouchLogMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 触达日志业务服务。
 * <p>
 * 负责触达日志的新增和查询。
 * 利用唯一索引 uk(touch_task_id, client_uuid) 保证移动端重试的幂等性：
 * 先查后插，若已存在直接返回；若并发下 INSERT 触发唯一键冲突，则捕获转为业务异常。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouchLogService {

    private final TouchLogMapper logMapper;
    private final TouchTaskMapper taskMapper;
    private final TouchTaskService touchTaskService;

    /** 允许提交触达日志的任务状态集合（PENDING 为首次日志，IN_PROGRESS 为后续日志）。 */
    private static final Set<String> LOGGABLE_STATUSES = Set.of(
            TouchTaskStatus.PENDING.getCode(),
            TouchTaskStatus.IN_PROGRESS.getCode()
    );

    /** 触达照片单次上传上限（CUST-42207）。 */
    private static final int MAX_PHOTO_COUNT = 9;

    /** 触达照片允许的扩展名（CUST-42208，小写匹配）。 */
    private static final Set<String> ALLOWED_PHOTO_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic");

    /**
     * 新增触达日志（幂等接口）。
     * <p>
     * 幂等策略：先按 (touchTaskId, clientUuid) 查询，若已存在则直接返回已有记录；
     * 若不存在则插入新记录；若并发导致 INSERT 触发 DuplicateKeyException，转为 TOUCH_LOG_DUPLICATE 异常。
     * 前置检查：任务必须存在且处于 PENDING 状态。
     * </p>
     *
     * @param touchTaskId    触达任务ID
     * @param clientUuid     客户端幂等键（由移动端生成）
     * @param logContent     触达内容描述
     * @param photoUrls      照片 URL 列表（JSON 数组字符串，最多 9 张），可为 null
     * @param operatorEmpId  操作人员工工号
     * @param orgId          归属机构代码
     * @return 触达日志实体（新建或已存在的）
     */
    @Transactional
    public TouchLog addLog(String touchTaskId, String clientUuid, String logContent,
                           String photoUrls, String operatorEmpId, String orgId) {
        log.info("[TouchLogService.addLog] touchTaskId={}, clientUuid={}, operatorEmpId={}",
                touchTaskId, clientUuid, operatorEmpId);

        // 必填校验（CUST-42206）：logContent 与 photoUrls 至少一个非空
        assertContentOrPhotoPresent(logContent, photoUrls);

        // 照片数量与格式校验（CUST-42207 / CUST-42208）：photoUrls 非空时解析 JSON 数组
        List<String> photos = parsePhotoUrls(photoUrls);
        assertPhotoCountWithinLimit(photos);
        assertPhotoFormatsAllowed(photos);

        // 检查触达任务存在且处于可提交日志的状态（PENDING 或 IN_PROGRESS）
        TouchTask task = taskMapper.selectById(touchTaskId);
        if (task == null) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage());
        }
        if (!LOGGABLE_STATUSES.contains(task.getTaskStatus())) {
            // 终态任务（SUCCESS/CANCELLED）不允许再提交日志
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_PENDING.getMessage());
        }

        // 幂等检查：先查是否已存在相同 (touchTaskId, clientUuid) 的日志
        TouchLog existing = logMapper.selectByTaskIdAndClientUuid(touchTaskId, clientUuid);
        if (existing != null) {
            // 幂等返回：移动端重试场景，直接返回已有记录
            log.info("[TouchLogService.addLog] idempotent hit, logId={}", existing.getId());
            return existing;
        }

        // 构建新日志记录
        LocalDateTime now = LocalDateTime.now();
        TouchLog entity = new TouchLog();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTouchTaskId(touchTaskId);
        entity.setClientUuid(clientUuid);
        entity.setLogContent(logContent);
        entity.setPhotoUrls(photoUrls);
        entity.setLogTime(now);
        entity.setOwnerOrgId(orgId);
        entity.setCreatedBy(operatorEmpId);
        entity.setCreatedTime(now);

        try {
            logMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发场景：两个请求同时通过幂等检查，其中一个会触发唯一键冲突
            log.warn("[TouchLogService.addLog] concurrent duplicate, touchTaskId={}, clientUuid={}",
                    touchTaskId, clientUuid);
            throw new BizException(CustomerErrorCode.TOUCH_LOG_DUPLICATE.getCode(),
                    CustomerErrorCode.TOUCH_LOG_DUPLICATE.getMessage());
        }

        log.info("[TouchLogService.addLog] log created, logId={}", entity.getId());

        // 首次日志触发 PENDING → IN_PROGRESS 状态迁移（依据《功能规格》§7.3bis）
        // 统计插入后的日志总数：count=1 说明本次是首条日志，驱动状态转移
        long logCount = logMapper.countByTaskId(touchTaskId);
        if (logCount == 1L && TouchTaskStatus.PENDING.getCode().equals(task.getTaskStatus())) {
            touchTaskService.markInProgress(touchTaskId);
        }

        return entity;
    }

    /**
     * 按触达任务 ID 查询该任务的所有日志（按 log_time 降序）。
     *
     * @param touchTaskId 触达任务ID
     * @return 该任务的触达日志列表，按 log_time 降序
     */
    public List<TouchLog> listByTaskId(String touchTaskId) {
        log.info("[TouchLogService.listByTaskId] touchTaskId={}", touchTaskId);
        return logMapper.selectByTaskId(touchTaskId);
    }

    // ============================= 私有校验方法 =============================

    /**
     * 校验 logContent 与 photoUrls 至少一个非空（CUST-42206）。
     */
    private void assertContentOrPhotoPresent(String logContent, String photoUrls) {
        boolean contentBlank = logContent == null || logContent.isBlank();
        boolean photosBlank = photoUrls == null || photoUrls.isBlank() || "[]".equals(photoUrls.trim());
        if (contentBlank && photosBlank) {
            throw new BizException(CustomerErrorCode.TOUCH_LOG_CONTENT_REQUIRED.getCode(),
                    CustomerErrorCode.TOUCH_LOG_CONTENT_REQUIRED.getMessage());
        }
    }

    /**
     * 解析 photoUrls JSON 数组字符串为 URL 列表。
     * 仅支持 JSON 数组形态（["url1","url2"]），其它形态视作单一 URL；空/null 返回空列表。
     */
    private List<String> parsePhotoUrls(String photoUrls) {
        if (photoUrls == null || photoUrls.isBlank() || "[]".equals(photoUrls.trim())) {
            return List.of();
        }
        String trimmed = photoUrls.trim();
        // 只接受 JSON 数组形式，否则视为格式错误（防御性 — 调用方约定 JSON 数组）
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new BizException(CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID.getCode(),
                    CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID.getMessage());
        }
        // 简化解析：去掉首尾 [ ] 后按逗号分割，剥离引号与空格。
        // 不引入 Jackson 依赖（addLog 高频调用，避免 ObjectMapper 开销 + 反射风险）。
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) {
            return List.of();
        }
        String[] parts = inner.split(",");
        List<String> urls = new java.util.ArrayList<>(parts.length);
        for (String p : parts) {
            String s = p.trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
            if (!s.isEmpty()) {
                urls.add(s);
            }
        }
        return urls;
    }

    /**
     * 校验照片数量不超过 9 张（CUST-42207）。
     */
    private void assertPhotoCountWithinLimit(List<String> photos) {
        if (photos.size() > MAX_PHOTO_COUNT) {
            throw new BizException(CustomerErrorCode.TOUCH_LOG_PHOTO_LIMIT_EXCEEDED.getCode(),
                    CustomerErrorCode.TOUCH_LOG_PHOTO_LIMIT_EXCEEDED.getMessage());
        }
    }

    /**
     * 校验每个照片 URL 后缀属于允许格式 jpg/jpeg/png/heic（CUST-42208）。
     */
    private void assertPhotoFormatsAllowed(List<String> photos) {
        for (String url : photos) {
            int dot = url.lastIndexOf('.');
            int qm = url.indexOf('?');
            int end = qm > 0 ? qm : url.length();
            if (dot < 0 || dot >= end - 1) {
                throw new BizException(CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID.getCode(),
                        CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID.getMessage());
            }
            String ext = url.substring(dot + 1, end).toLowerCase();
            if (!ALLOWED_PHOTO_EXTENSIONS.contains(ext)) {
                throw new BizException(CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID.getCode(),
                        CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID.getMessage());
            }
        }
    }
}
