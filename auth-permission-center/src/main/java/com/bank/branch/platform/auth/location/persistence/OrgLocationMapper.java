package com.bank.branch.platform.auth.location.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** PT_ORG_LOCATION Mapper；只在 storage-enabled=true 的条件扫描配置中装配。 */
@Mapper
public interface OrgLocationMapper extends BaseMapper<PtOrgLocation> {
}
