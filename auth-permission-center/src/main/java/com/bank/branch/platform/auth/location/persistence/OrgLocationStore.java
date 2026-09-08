package com.bank.branch.platform.auth.location.persistence;

import java.util.Collection;
import java.util.List;

/** 机构位置存储抽象，便于关闭能力时完全隔离新表 Mapper。 */
public interface OrgLocationStore {

    /** 底层表结构/仓储是否可用。 */
    boolean isAvailable();

    /** 查询单个机构位置。 */
    PtOrgLocation findByOrgCode(String orgCode);

    /** 批量查询机构位置。 */
    List<PtOrgLocation> findByOrgCodes(Collection<String> orgCodes);

    /** 新增位置。 */
    PtOrgLocation insert(PtOrgLocation location);

    /** 按机构编码及期望版本 CAS 更新。 */
    boolean updateWithVersion(PtOrgLocation location, Integer expectedVersion);
}
