package com.bank.branch.platform.customer.dto.marketing.tag;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 多条/全部标签客户审批请求。 */
@Data
public class TagCustomerBatchApprovalRequest {
    /** 当前操作限定的标签 ID；批量接口必须由后端据此重新取待审批集合。 */
    private Long tagId;
    /** 可选批次 ID；传入后只处理该批次，避免跨批次误审。 */
    private Long batchId;
    /** 选中的明细 ID；allPending=true 时可以为空。 */
    private List<Long> detailIds;
    /** 按当前登录人的授权重新获取全部待审批明细。 */
    private boolean allPending;
    /** 标签未审批时是否先完成标签审批。 */
    private boolean approveTag;
    @Size(max = 500)
    private String opinion;
}
