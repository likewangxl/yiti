package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.TouchWorklogParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** MARKETING_TOUCH_WORKLOG_PARTICIPANT Mapper。 */
@Mapper
public interface TouchWorklogParticipantMapper extends BaseMapper<TouchWorklogParticipant> {
    @Select("SELECT * FROM MARKETING_TOUCH_WORKLOG_PARTICIPANT WHERE worklog_id=#{worklogId} ORDER BY id")
    List<TouchWorklogParticipant> selectByWorklogId(@Param("worklogId") Long worklogId);
}
