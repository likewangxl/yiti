package com.bank.branch.platform.report.dto.resp;

import lombok.Data;

/** 异地机构示意锚点节点，不携带伪造经纬度。 */
@Data
public class ScreenMapSatelliteNodeDTO {

    private String orgCode;
    private String orgName;
    private String anchor;
    private String targetScreenCode;
}
