package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 触达日志对外 DTO
 * <p>
 * 用于跨模块传递单次触达操作的日志记录，包含照片凭证、操作人定位信息等。
 * 触达日志是触达任务执行过程的证据留存，支持合规审计。
 * </p>
 */
@Data
public class TouchLogDTO {

    /** 触达日志 ID */
    private String id;

    /** 关联触达任务 ID */
    private String touchTaskId;

    /** 客户端唯一标识 (用于幂等去重) */
    private String clientUuid;

    /** 日志内容 */
    private String logContent;

    /** 照片凭证 URL 列表 */
    private List<String> photoUrls;

    /** 操作人工号 */
    private String operatorEmpId;

    /** 操作人姓名 */
    private String operatorEmpName;

    /** 操作人定位信息 */
    private String operatorLocation;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
