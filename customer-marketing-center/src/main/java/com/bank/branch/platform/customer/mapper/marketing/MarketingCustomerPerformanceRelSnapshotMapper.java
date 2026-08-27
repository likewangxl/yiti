package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerPerformanceRelSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT 数据访问。 */
@Mapper
public interface MarketingCustomerPerformanceRelSnapshotMapper
        extends BaseMapper<MarketingCustomerPerformanceRelSnapshot> {

    /** 查询当前有效且刷新成功的业绩关系快照。 */
    List<MarketingCustomerPerformanceRelSnapshot> selectActiveByCustId(@Param("custId") Long custId);
}
