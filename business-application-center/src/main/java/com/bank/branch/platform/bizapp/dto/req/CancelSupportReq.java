package com.bank.branch.platform.bizapp.dto.req;

import lombok.Data;

/** 中台支持撤回请求。 */
@Data
public class CancelSupportReq {

    /** 撤回原因，生产请求必须填写；空请求体仅为兼容旧客户端保留。 */
    private String reason;
}
