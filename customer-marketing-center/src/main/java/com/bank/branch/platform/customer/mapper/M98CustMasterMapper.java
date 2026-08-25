package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.M98CustMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * M98 客户主档 Mapper，只操作存量 CUST_MASTER 表。
 *
 * <p>该 Mapper 与客户营销 {@link CustMasterMapper} 分离，防止定时同步和营销审批写入同一张表。</p>
 */
@Mapper
public interface M98CustMasterMapper extends BaseMapper<M98CustMaster> {

    /** 按 M98 客户号查询有效客户。 */
    M98CustMaster selectByCustNo(@Param("custNo") String custNo);

    /** 将指定统计日期的 M98 新客户同步到存量 CUST_MASTER。 */
    int syncNewCustomersFromStat(@Param("statisDt") String statisDt);
}
