package com.bank.branch.platform.auth.location.persistence;

import java.util.Collection;
import java.util.List;

/** 位置存储关闭或不可用时的 Fail Close 实现；不会触碰 PT_ORG_LOCATION。 */
public class UnavailableOrgLocationStore implements OrgLocationStore {

    private final String reason;

    public UnavailableOrgLocationStore(String reason) {
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
    public PtOrgLocation findByOrgCode(String orgCode) {
        throw new IllegalStateException(reason);
    }

    @Override
    public List<PtOrgLocation> findByOrgCodes(Collection<String> orgCodes) {
        throw new IllegalStateException(reason);
    }

    @Override
    public PtOrgLocation insert(PtOrgLocation location) {
        throw new IllegalStateException(reason);
    }

    @Override
    public boolean updateWithVersion(PtOrgLocation location, Integer expectedVersion) {
        throw new IllegalStateException(reason);
    }
}
