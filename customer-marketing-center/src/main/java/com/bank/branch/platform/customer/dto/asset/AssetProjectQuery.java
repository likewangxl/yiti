package com.bank.branch.platform.customer.dto.asset;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 资产立项工作台查询条件。 */
@Data
public class AssetProjectQuery {
    private String tab = "MY";
    private String keyword;
    private String customerName;
    private String projectName;
    private String status;
    private Boolean urgent;
    private Boolean keyProject;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
