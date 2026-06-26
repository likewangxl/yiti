package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.CcmsBusinessContract;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务合同信息 Mapper（只读引用表 {@code ccms_business_contract}）。
 *
 * <p>担保信息新增时按 customerid + customername 反查客户名下合同记录，查询走 MyBatis-Plus
 * {@code BaseMapper} + {@code LambdaQueryWrapper}，无自定义 XML。</p>
 */
@Mapper
public interface CcmsBusinessContractMapper extends BaseMapper<CcmsBusinessContract> {
}
