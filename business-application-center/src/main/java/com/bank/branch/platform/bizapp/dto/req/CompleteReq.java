package com.bank.branch.platform.bizapp.dto.req;

import lombok.Data;

/**
 * 办理完成请求 DTO。
 */
@Data
public class CompleteReq {

    /** 是否成功完成（true=完成，false=拒绝） */
    private boolean success;
}
