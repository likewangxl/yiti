package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.entity.PtOrgProfile;
import org.apache.ibatis.annotations.Mapper;

/** 机构本地画像 Mapper；标准 CRUD 由 MyBatis-Plus BaseMapper 提供。 */
@Mapper
public interface OrgProfileMapper extends BaseMapper<PtOrgProfile> {
}
