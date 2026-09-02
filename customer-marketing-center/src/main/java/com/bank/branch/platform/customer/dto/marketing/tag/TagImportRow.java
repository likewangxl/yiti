package com.bank.branch.platform.customer.dto.marketing.tag;

import lombok.Data;

/** 标签客户导入文件的一行标准化快照。 */
@Data
public class TagImportRow {
    private int rowNo;
    private String custName;
    private String unifiedCreditCode;
    private String contactPerson;
    private String contactMobile;
    private String registeredAddress;
    private String businessAddress;
    private String rawRowJson;
}
