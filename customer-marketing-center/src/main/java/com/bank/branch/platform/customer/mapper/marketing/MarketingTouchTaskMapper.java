package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingTouchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MARKETING_TOUCH_TASK 数据访问。 */
@Mapper
public interface MarketingTouchTaskMapper extends BaseMapper<MarketingTouchTask> {

    MarketingTouchTask selectBySource(@Param("sourceType") String sourceType,
                                      @Param("sourceBizId") Long sourceBizId);
}
