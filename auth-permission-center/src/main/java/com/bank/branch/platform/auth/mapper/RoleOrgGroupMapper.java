package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.entity.PtRoleOrgGroup;
import org.apache.ibatis.annotations.Mapper;

/** 角色-机构组授权 Mapper；标准 CRUD 由 MyBatis-Plus BaseMapper 提供。 */
@Mapper
public interface RoleOrgGroupMapper extends BaseMapper<PtRoleOrgGroup> {
}
