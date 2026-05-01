package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.LeadImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 线索导入批次 Mapper 接口，操作 lead_import_batch 表。
 * <p>
 * 该表无逻辑删除字段，通过 status 管理批次生命周期。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code updateById(T)} 由 BaseMapper 提供。
 * selectById 因签名含 @Param 保留原 XML 实现。
 * </p>
 */
@Mapper
public interface LeadImportBatchMapper extends BaseMapper<LeadImportBatch> {

    /**
     * 按 id 查询导入批次。
     *
     * @param id 批次ID
     * @return 批次实体，不存在时返回 null
     */
    LeadImportBatch selectById(@Param("id") String id);

    /**
     * 按批次号查询（用于对外展示和唯一性预检）。
     *
     * @param batchNo 批次号
     * @return 批次实体，不存在时返回 null
     */
    LeadImportBatch selectByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 分页查询导入批次列表。
     * <p>
     * keyword 模糊搜索 batch_no 和 source_file_name，status 精确匹配。
     * </p>
     *
     * @param keyword 关键词（搜索 batch_no 和 source_file_name），可为 null
     * @param status  批次状态过滤，可为 null
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 导入批次列表
     */
    List<LeadImportBatch> selectPage(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数（与 selectPage 共享 WHERE 条件）。
     *
     * @param keyword 关键词，可为 null
     * @param status  批次状态过滤，可为 null
     * @return 总记录数
     */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status);

    // insert(T) 和 updateById(T) 由 MyBatis-Plus BaseMapper 提供
}
