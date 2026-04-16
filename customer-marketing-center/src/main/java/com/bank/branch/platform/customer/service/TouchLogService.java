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

        // 检查触达任务存在且处于 PENDING 状态
        TouchTask task = taskMapper.selectById(touchTaskId);
        if (task == null) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getCode(),
                    CustomerErrorCode.TOUCH_TASK_NOT_FOUND.getMessage());
        }
        if (!TouchTaskStatus.PENDING.getCode().equals(task.getTaskStatus())) {
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
}
