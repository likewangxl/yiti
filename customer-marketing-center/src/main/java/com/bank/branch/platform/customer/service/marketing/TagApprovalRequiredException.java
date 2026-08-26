package com.bank.branch.platform.customer.service.marketing;

import com.bank.branch.platform.common.web.exception.BizException;
import lombok.Getter;

import java.util.List;

/**
 * 标签客户审批的前置依赖异常。
 *
 * <p>调用方可据此弹窗询问是否先审批标签，然后使用原明细集合重试；异常本身不改变任何业务状态。</p>
 */
@Getter
public class TagApprovalRequiredException extends BizException {
    private final Long tagId;
    private final String tagName;
    private final Long batchId;
    private final List<Long> detailIds;

    public TagApprovalRequiredException(Long tagId, String tagName,
                                        Long batchId, List<Long> detailIds) {
        super("CUST-40905", "标签【" + tagName + "】尚未审批，请先审批标签");
        this.tagId = tagId;
        this.tagName = tagName;
        this.batchId = batchId;
        this.detailIds = detailIds == null ? List.of() : List.copyOf(detailIds);
    }
}
