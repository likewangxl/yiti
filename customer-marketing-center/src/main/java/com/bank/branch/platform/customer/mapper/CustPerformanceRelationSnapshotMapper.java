package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CustPerformanceRelationSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 客户业绩归属快照 Mapper。 */
@Mapper
public interface CustPerformanceRelationSnapshotMapper extends BaseMapper<CustPerformanceRelationSnapshot> {
    /** 查询当前仍生效的成功快照关系。 */
    @Select("SELECT * FROM CUST_PERFORMANCE_RELATION_SNAPSHOT WHERE cust_id=#{custId} " +
            "AND refresh_status='SUCCESS' AND effective_date<=CURRENT_DATE " +
            "AND (expiry_date IS NULL OR expiry_date>=CURRENT_DATE)")
    List<CustPerformanceRelationSnapshot> selectActiveByCustId(String custId);
}
