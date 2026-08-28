package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.TouchWorklog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** MARKETING_TOUCH_WORKLOG Mapper。 */
@Mapper
public interface TouchWorklogMapper extends BaseMapper<TouchWorklog> {
    @Select("SELECT * FROM MARKETING_TOUCH_WORKLOG WHERE task_id=#{taskId} AND client_uuid=#{clientUuid} LIMIT 1")
    TouchWorklog selectByTaskAndClientUuid(@Param("taskId") Long taskId, @Param("clientUuid") String clientUuid);

    @Select("SELECT * FROM MARKETING_TOUCH_WORKLOG WHERE task_id=#{taskId} AND record_status='VALID' ORDER BY touch_time DESC,id DESC")
    List<TouchWorklog> selectValidByTaskId(@Param("taskId") Long taskId);

    @Select("SELECT COUNT(*) FROM MARKETING_TOUCH_WORKLOG WHERE task_id=#{taskId} AND record_status='VALID'")
    long countValidByTaskId(@Param("taskId") Long taskId);
}
