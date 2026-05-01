package com.bank.branch.platform.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.governance.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字典 Mapper 接口，操作 sys_dict 表。
 * <p>
 * 字典查询默认只返回 ACTIVE 状态的记录，按 sort_order 升序排列。
 * 分页查询支持按 dictType 精确匹配和 keyword 模糊搜索。
 * </p>
 * <p>
 * selectById / insert / updateById 由 MyBatis-Plus BaseMapper 提供。
 * </p>
 */
@Mapper
public interface DictMapper extends BaseMapper<SysDict> {

    /**
     * 根据字典类型查询所有启用状态的字典项，按排序号升序排列。
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    List<SysDict> selectByDictType(String dictType);

    /**
     * 根据字典类型和字典编码查询单条启用状态的字典项。
     *
     * @param dictType 字典类型
     * @param dictCode 字典编码
     * @return 字典实体，不存在时返回 null
     */
    SysDict selectByDictTypeAndDictCode(@Param("dictType") String dictType,
                                        @Param("dictCode") String dictCode);

    /**
     * 判断指定字典类型和编码是否已存在（不限状态）。
     *
     * @param dictType 字典类型
     * @param dictCode 字典编码
     * @return 存在返回 true，否则返回 false
     */
    boolean existsByDictTypeAndDictCode(@Param("dictType") String dictType,
                                        @Param("dictCode") String dictCode);

    /**
     * 分页查询字典列表，支持按类型精确匹配和关键词模糊搜索。
     *
     * @param dictType 字典类型，为 null 时不过滤
     * @param keyword  关键词，为 null 时不过滤
     * @param offset   偏移量
     * @param limit    每页大小
     * @return 字典列表
     */
    List<SysDict> selectByPage(@Param("dictType") String dictType,
                               @Param("keyword") String keyword,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /**
     * 统计分页查询的总记录数。
     *
     * @param dictType 字典类型，为 null 时不过滤
     * @param keyword  关键词，为 null 时不过滤
     * @return 总记录数
     */
    long countByPage(@Param("dictType") String dictType,
                     @Param("keyword") String keyword);

    /**
     * 按字典类型分组聚合，返回类型汇总信息。
     * 用于 A.1 字典类型列表查询（GET /api/sys/dicts）。
     *
     * @param dictType 字典类型精确匹配，为 null 时不过滤
     * @param keyword  关键词模糊匹配字典类型，为 null 时不过滤
     * @param status   状态筛选（ACTIVE/DISABLED/null 不过滤）
     * @return 按字典类型聚合的汇总列表
     */
    List<DictTypeVO> selectGroupByType(@Param("dictType") String dictType,
                                       @Param("keyword") String keyword,
                                       @Param("status") String status);
}
