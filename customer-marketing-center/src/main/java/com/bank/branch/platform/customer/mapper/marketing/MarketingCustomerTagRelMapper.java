package com.bank.branch.platform.customer.mapper.marketing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** MARKETING_CUSTOMER_TAG_REL 的 MyBatis-Plus Mapper。 */
@Mapper
public interface MarketingCustomerTagRelMapper extends BaseMapper<MarketingCustomerTagRel> {

    /** 分页查询标签下的正式有效客户群。 */
    List<MarketingCustomerTagRel> selectActivePage(@Param("tagId") Long tagId,
                                                   @Param("keyword") String keyword,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /** 统计标签下的正式有效客户数。 */
    long countActiveByTagId(@Param("tagId") Long tagId,
                            @Param("keyword") String keyword);

    /** 查询标签下全部正式有效关系，供 REPLACE 计算差异。 */
    List<MarketingCustomerTagRel> selectActiveByTagId(@Param("tagId") Long tagId);

    /** 按客户和标签查询关系，包含已失效历史关系。 */
    MarketingCustomerTagRel selectByCustIdAndTagId(@Param("custId") Long custId,
                                                   @Param("tagId") Long tagId);

    /** 将标签当前有效关系批量失效，保留历史事实。 */
    int expireActiveByTagId(@Param("tagId") Long tagId,
                            @Param("operatorEmpId") String operatorEmpId,
                            @Param("expiredTime") LocalDateTime expiredTime);

    /** REPLACE 时只失效不在本批次集合中的关系。 */
    int expireNotInCustomerIds(@Param("tagId") Long tagId,
                               @Param("custIds") List<Long> custIds,
                               @Param("operatorEmpId") String operatorEmpId,
                               @Param("expiredTime") LocalDateTime expiredTime);

    /** 恢复已有历史关系。 */
    int reactivate(@Param("id") Long id,
                   @Param("operatorEmpId") String operatorEmpId,
                   @Param("effectiveTime") LocalDateTime effectiveTime);
}
