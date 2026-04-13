package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.DocInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档信息 Mapper 接口，操作 doc_info 表。
 * <p>
 * 支持分页查询、按分类查询、启用/禁用状态管理等操作。
 * 本表无 deleted 列，softDelete 通过将 status 设为 DISABLED 实现。
 * </p>
 */
@Mapper
public interface DocInfoMapper {

    /**
     * 插入新文档。
     *
     * @param entity 文档实体
     * @return 受影响行数
     */
    int insert(DocInfo entity);

    /**
     * 按 id 查询。
     *
     * @param id 文档ID
     * @return 文档实体，不存在时返回 null
     */
    DocInfo selectById(@Param("id") String id);

    /**
     * 按部分字段更新，使用动态 SET 仅更新非 null 字段。
     *
     * @param entity 包含 id 及待更新字段的文档实体
     * @return 受影响行数
     */
    int updateById(DocInfo entity);

    /**
     * 逻辑停用（将 status 设为 DISABLED）。
     *
     * @param id        文档ID
     * @param updatedBy 操作人
     * @return 受影响行数
     */
    int softDeleteById(@Param("id") String id, @Param("updatedBy") String updatedBy);

    /**
     * 分页查询文档列表。
     *
     * @param keyword  关键词（搜索 doc_title），可为 null
     * @param category 文档分类，可为 null
     * @param status   状态过滤，可为 null
     * @param offset   偏移量
     * @param limit    每页条数
     * @return 文档列表
     */
    List<DocInfo> selectPage(@Param("keyword") String keyword,
                             @Param("category") String category,
                             @Param("status") String status,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数。
     *
     * @param keyword  关键词（搜索 doc_title），可为 null
     * @param category 文档分类，可为 null
     * @param status   状态过滤，可为 null
     * @return 总记录数
     */
    long countPage(@Param("keyword") String keyword,
                   @Param("category") String category,
                   @Param("status") String status);

    /**
     * 按分类查询启用的文档列表。
     *
     * @param category 文档分类
     * @return 启用状态的文档列表
     */
    List<DocInfo> listActiveByCategory(@Param("category") String category);
}
