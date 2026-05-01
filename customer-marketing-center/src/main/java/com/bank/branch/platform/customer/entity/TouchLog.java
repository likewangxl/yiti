package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 触达日志实体，对应 touch_log 表。
 * <p>
 * 记录每次实际触达客户的详情，包含文字描述和照片。
 * 唯一索引 uk_task_uuid(touch_task_id, client_uuid) 保证移动端重试幂等性。
 * photo_urls 存储 JSON 数组格式的 MinIO 照片 URL，单条最多 9 张。
 * 注意：该表使用 log_content（非 touch_content），有 log_time/owner_org_id，
 * 无 touch_type/touch_result 字段。
 * </p>
 */
@Data
@TableName("touch_log")
public class TouchLog {

    /** 主键ID（UUID，32位去连字符），对应 id */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 触达任务ID（关联 touch_task.id），对应 touch_task_id */
    private String touchTaskId;

    /** 触达时间，对应 log_time */
    private LocalDateTime logTime;

    /** 客户端幂等键（防重复提交），对应 client_uuid */
    private String clientUuid;

    /** 触达内容（文字描述），对应 log_content */
    private String logContent;

    /** 照片URL列表（JSON数组，最多9张），对应 photo_urls */
    private String photoUrls;

    /** 归属机构代码，对应 owner_org_id */
    private String ownerOrgId;

    /** 创建人（员工工号），对应 created_by */
    private String createdBy;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;
}
