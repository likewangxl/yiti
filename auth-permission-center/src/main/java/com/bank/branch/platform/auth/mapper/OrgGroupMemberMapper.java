package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.entity.PtOrgGroupMember;
import org.apache.ibatis.annotations.Mapper;

/** 命名机构组成员 Mapper；直接成员查询和覆盖保存使用 BaseMapper + Wrapper。 */
@Mapper
public interface OrgGroupMemberMapper extends BaseMapper<PtOrgGroupMember> {
}
