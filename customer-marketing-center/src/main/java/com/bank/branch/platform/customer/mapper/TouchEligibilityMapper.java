package com.bank.branch.platform.customer.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 触达资格校验专用工作日志读取 Mapper。 */
@Mapper
public interface TouchEligibilityMapper {

    /** 统计指定自然周期内尚未销毁的工作日志。endTime 为开区间。 */
    long countValidWorklogs(@Param("custId") String custId,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);
}
