package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.AmasApprRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录 Mapper：按 REGION_DT_ID 取审批流转记录、按 APPR_SEQ 倒序（详情页用）.
 */
@Mapper
public interface AmasApprRecordMapper extends BaseMapper<AmasApprRecord> {
}
