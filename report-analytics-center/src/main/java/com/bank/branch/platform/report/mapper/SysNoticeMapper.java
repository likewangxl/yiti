package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.SysNotice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知公告 sys_notice Mapper：列表分页 + 详情，全部走 MyBatis-Plus 条件查询.
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {
}
