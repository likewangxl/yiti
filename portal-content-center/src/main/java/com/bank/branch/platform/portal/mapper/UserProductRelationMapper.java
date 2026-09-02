package com.bank.branch.platform.portal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.portal.entity.UserProductRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 用户—产品负责关系 Mapper。
 *
 * <p>关系表的查询和批量替换均使用显式 SQL；不使用 BaseMapper 的单列主键 CRUD，
 * 因为物理主键是 {@code (USER_ID, PRODUCT_ID)}。</p>
 */
@Mapper
public interface UserProductRelationMapper extends BaseMapper<UserProductRelation> {

    /** 按用户查询其负责产品。 */
    List<UserProductRelation> listByUserId(@Param("userId") String userId);

    /** 按用户批量查询负责产品。 */
    List<UserProductRelation> listByUserIds(@Param("userIds") Collection<String> userIds);

    /** 按产品反查负责人。 */
    List<UserProductRelation> listByProductId(@Param("productId") String productId);

    /** 按产品批量反查负责人。 */
    List<UserProductRelation> listByProductIds(@Param("productIds") Collection<String> productIds);

    /** 删除某一用户的全部负责关系。 */
    int deleteByUserId(@Param("userId") String userId);

    /** 删除某一用户与某一产品的负责关系。 */
    int deleteByUserAndProduct(@Param("userId") String userId,
                                @Param("productId") String productId);

    /** 按产品批量删除已移除的负责人关系。 */
    int deleteByProductAndUserIds(@Param("productId") String productId,
                                   @Param("userIds") Collection<String> userIds);

    /** 批量插入关系（调用方保证已去重）。 */
    int insertBatch(@Param("relations") Collection<UserProductRelation> relations);

    /** 产品删除前的关系引用检查。 */
    int countByProductId(@Param("productId") String productId);
}
