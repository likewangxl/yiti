package com.bank.branch.platform.portal.controller.dto.guarantee;

import lombok.Data;

/**
 * 担保信息分页查询入参。
 */
@Data
public class GuaranteeQueryReqDTO {

    /** 客户名称（模糊匹配，可空） */
    private String clientName;

    /** 页码（从 1 开始），默认 1 */
    private Integer pageNo = 1;

    /** 每页条数，默认 20，最大 100 */
    private Integer pageSize = 20;

    /** 规范化页码，最小为 1 */
    public int normalizedPageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    /** 规范化每页条数，落在 [1,100] */
    public int normalizedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
