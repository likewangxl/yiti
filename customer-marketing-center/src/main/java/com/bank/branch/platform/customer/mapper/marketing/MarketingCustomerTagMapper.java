package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_CUSTOMER_TAG 的 MyBatis-Plus Mapper。 */
@Mapper
public interface MarketingCustomerTagMapper extends BaseMapper<MarketingCustomerTag> {

    /** 按标签名称（忽略首尾空白和大小写）查询有效标签。 */
    MarketingCustomerTag selectByTagName(@Param("tagName") String tagName);

    /** 分页查询标签及正式有效客户数。 */
    List<MarketingCustomerTag> selectPage(@Param("keyword") String keyword,
                                           @Param("category") String category,
                                           @Param("status") String status,
                                           @Param("approvalStatus") String approvalStatus,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /** 统计标签分页总数。 */
    long countPage(@Param("keyword") String keyword,
                   @Param("category") String category,
                   @Param("status") String status,
                   @Param("approvalStatus") String approvalStatus);
}
