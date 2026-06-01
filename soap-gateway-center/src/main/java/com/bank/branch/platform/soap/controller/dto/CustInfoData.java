package com.bank.branch.platform.soap.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CASH_GETCUST_INFO 成功载荷，对应手机端 {@code response.RspMsg.custName}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustInfoData {

    /** 客户名称。 */
    private String custName;
}
