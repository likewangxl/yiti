package com.bank.branch.platform.auth.location.geocode;

import java.math.BigDecimal;

/** 地址解析供应商返回的内部候选，不直接暴露给客户端。 */
public record OrgLocationGeocodeCandidate(
        String provider,
        String formattedAddress,
        String cityCode,
        BigDecimal lng,
        BigDecimal lat,
        String coordSys,
        String matchLevel) {

    /** 测试及适配层可替换精度，保持候选其他字段不变。 */
    public OrgLocationGeocodeCandidate withMatchLevel(String value) {
        return new OrgLocationGeocodeCandidate(provider, formattedAddress, cityCode,
                lng, lat, coordSys, value);
    }
}
