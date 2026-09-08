package com.bank.branch.platform.auth.location.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** 基于 MyBatis-Plus 的 PT_ORG_LOCATION 存储实现。 */
@RequiredArgsConstructor
public class MybatisOrgLocationStore implements OrgLocationStore {

    private final OrgLocationMapper mapper;

    @Override
    public boolean isAvailable() {
        try {
            // 只做表存在性探测，不依赖具体机构编码，也不写入数据。
            mapper.selectById("__ORG_LOCATION_CAPABILITY_PROBE__");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public PtOrgLocation findByOrgCode(String orgCode) {
        return mapper.selectById(orgCode);
    }

    @Override
    public List<PtOrgLocation> findByOrgCodes(Collection<String> orgCodes) {
        if (orgCodes == null || orgCodes.isEmpty()) {
            return List.of();
        }
        return OptionalList.of(mapper.selectBatchIds(orgCodes));
    }

    @Override
    public PtOrgLocation insert(PtOrgLocation location) {
        mapper.insert(location);
        return location;
    }

    @Override
    public boolean updateWithVersion(PtOrgLocation location, Integer expectedVersion) {
        Integer nextVersion = location.getVersion();
        int updated = mapper.update(new PtOrgLocation(), new LambdaUpdateWrapper<PtOrgLocation>()
                .set(PtOrgLocation::getAddress, location.getAddress())
                .set(PtOrgLocation::getAddressSource, location.getAddressSource())
                .set(PtOrgLocation::getCityCode, location.getCityCode())
                .set(PtOrgLocation::getLng, location.getLng())
                .set(PtOrgLocation::getLat, location.getLat())
                .set(PtOrgLocation::getCoordSys, location.getCoordSys())
                .set(PtOrgLocation::getProvider, location.getProvider())
                .set(PtOrgLocation::getMatchLevel, location.getMatchLevel())
                .set(PtOrgLocation::getStatus, location.getStatus())
                .set(PtOrgLocation::getVersion, nextVersion)
                .set(PtOrgLocation::getLocationSource, location.getLocationSource())
                .set(PtOrgLocation::getUpdatedBy, location.getUpdatedBy())
                .set(PtOrgLocation::getUpdatedTime, location.getUpdatedTime())
                .eq(PtOrgLocation::getOrgCode, location.getOrgCode())
                .eq(PtOrgLocation::getVersion, expectedVersion));
        return updated == 1;
    }

    /** MP 某些驱动可能返回 null 列表，统一成空列表。 */
    private static final class OptionalList {
        private OptionalList() {
        }

        private static <T> List<T> of(List<T> values) {
            return values == null ? Collections.emptyList() : values;
        }
    }
}
