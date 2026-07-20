package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.api.dto.PersonTagRespDTO;
import com.bank.branch.platform.governance.entity.PersonTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 人员标签 Mapper，操作 PERSON_TAG 表。
 * <p>selectById / insert / updateById / deleteById 由 MyBatis-Plus BaseMapper 提供。</p>
 */
@Mapper
public interface PersonTagMapper extends BaseMapper<PersonTag> {

    /**
     * 分页查询标签列表（含关联人数），按创建时间倒序。
     *
     * @param keyword 标签名称模糊关键字（可空）
     * @param offset  偏移
     * @param size    页大小
     * @return 标签行（含 memberCount）
     */
    List<PersonTagRespDTO> selectPageWithMemberCount(@Param("keyword") String keyword,
                                                     @Param("offset") int offset,
                                                     @Param("size") int size);

    /**
     * 与 {@link #selectPageWithMemberCount} 同条件的总数。
     *
     * @param keyword 标签名称模糊关键字（可空）
     * @return 总条数
     */
    long countByKeyword(@Param("keyword") String keyword);

    /**
     * 按名称精确查单个标签（唯一键 UK_PERSON_TAG_NAME）。
     *
     * @param tagName 标签名称
     * @return 标签或 null
     */
    PersonTag selectByTagName(@Param("tagName") String tagName);

    /**
     * 按名称集合批量查标签（导入时解析已存在标签用，单次 IN）。
     *
     * @param tagNames 标签名称集合（非空）
     * @return 命中的标签列表
     */
    List<PersonTag> selectByTagNames(@Param("tagNames") List<String> tagNames);
}
