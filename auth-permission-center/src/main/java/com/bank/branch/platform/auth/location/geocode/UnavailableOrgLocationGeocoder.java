package com.bank.branch.platform.auth.location.geocode;

import java.util.List;

/** 地理编码关闭时的 Fail Close 实现。 */
public class UnavailableOrgLocationGeocoder implements OrgLocationGeocoder {

    private final String reason;

    public UnavailableOrgLocationGeocoder(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String provider() {
        return "AMAP";
    }

    @Override
    public List<OrgLocationGeocodeCandidate> geocode(String address, String cityCode) {
        throw new IllegalStateException(reason);
    }
}
