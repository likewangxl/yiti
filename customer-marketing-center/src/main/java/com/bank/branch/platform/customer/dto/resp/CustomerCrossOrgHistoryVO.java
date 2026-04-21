package com.bank.branch.platform.customer.dto.resp;

import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import lombok.Data;

import java.util.List;

/**
 * 客户跨机构全量历史视图对象（VO）。
 *
 * <p>
 * 高危数据结构，聚合客户在全行范围内的所有认领记录与触达历史，
 * 仅供拥有 CROSS_ORG 权限的总行部门人员调用。
 * 审计要求：specialCategory=CROSS_ORG（见文档 07-审计要求.md），5 年留存。
 * </p>
 *
 * <p>TODO: 待 @AuditLog 支持 specialCategory 属性后，补充 specialCategory=CROSS_ORG 字段。</p>
 */
@Data
public class CustomerCrossOrgHistoryVO {

    /**
     * 客户基础信息（脱敏处理，含 custNo / custName / status 等公开字段）。
     */
    private CustomerDTO customer;

    /**
     * 全行范围内所有认领记录（含 CANCELLED 历史），按认领时间降序排列。
     */
    private List<CustClaimDTO> claims;

    /**
     * 全行范围内所有触达任务历史（含所有状态），按创建时间降序排列。
     */
    private List<TouchTaskDTO> touchTasks;

    /**
     * 认领总次数（含历史取消记录）。
     */
    private long totalClaimCount;

    /**
     * 当前有效认领数（claimStatus = CLAIMED）。
     */
    private long activeClaimCount;

    /**
     * 触达任务总数（全状态）。
     */
    private long totalTouchCount;
}
