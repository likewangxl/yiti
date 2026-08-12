package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.entity.PtOrgGroup;
import org.apache.ibatis.annotations.Mapper;

/** 命名机构组 Mapper；标准 CRUD 由 MyBatis-Plus BaseMapper 提供。 */
@Mapper
public interface OrgGroupMapper extends BaseMapper<PtOrgGroup> {
}
