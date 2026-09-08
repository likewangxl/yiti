package com.bank.branch.platform.auth.location.geocode;

import java.util.List;

/** 受控服务端地址解析接口。 */
public interface OrgLocationGeocoder {

    /** 当前是否已配置且允许外呼。 */
    boolean isAvailable();

    /** 供应商固定标识。 */
    String provider();

    /** 使用结构化地址和城市约束请求候选。 */
    List<OrgLocationGeocodeCandidate> geocode(String address, String cityCode);
}
