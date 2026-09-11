package com.bank.branch.platform.customer.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 机构营销快照公共 DTO。
 *
 * <p>sourceMode 明确标识当前实现没有历史回放能力；sourceAsOfDate 是本次读取的真实
 * 当前状态日期，asOfDate 是调用方请求的截至日期。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingOrgSnapshotDTO {

    /** 机构编码。 */
    private String orgCode;

    /** 有效营销客户数，按客户主档去重。 */
    private Long validCustomerCount;

    /** 机构下待跟进触达任务数，即 PENDING + IN_PROGRESS。 */
    private Long pendingFollowUpTaskCount;

    /** 调用方请求的截至日期；null 输入会归一为当前日期。 */
    private LocalDate asOfDate;

    /** 真实读取发生的当前状态日期。 */
    private LocalDate sourceAsOfDate;

    /** 相关客户或任务记录的最新更新时间；零值机构为空。 */
    private LocalDateTime sourceUpdatedAt;

    /** 当前固定为 CURRENT_STATE。 */
    private String sourceMode;
}
