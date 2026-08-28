package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.TouchWorklogPictureRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** MARKETING_TOUCH_WORKLOG_PICTURE Mapper。 */
@Mapper
public interface TouchWorklogPictureRecordMapper extends BaseMapper<TouchWorklogPictureRecord> {
    @Select("SELECT * FROM MARKETING_TOUCH_WORKLOG_PICTURE WHERE worklog_id=#{worklogId} "
            + "AND record_status='ACTIVE' ORDER BY picture_type,sort_no")
    List<TouchWorklogPictureRecord> selectActiveByWorklogId(@Param("worklogId") Long worklogId);
}
