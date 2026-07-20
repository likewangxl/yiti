package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 人员标签-人员关联 Mapper，操作 PERSON_TAG_REL 表。
 * <p>USERNAME 列存工号（PT_USER.USERNAME 口径）。</p>
 */
@Mapper
public interface PersonTagRelMapper extends BaseMapper<PersonTagRel> {

    /**
     * 分页查询某标签下的关联行，按创建时间/ID 升序（稳定顺序）。
     *
     * @param tagId  标签 ID
     * @param offset 偏移
     * @param size   页大小
     * @return 关联行列表
     */
    List<PersonTagRel> selectPageByTagId(@Param("tagId") Long tagId,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    /**
     * 某标签下关联总数。
     *
     * @param tagId 标签 ID
     * @return 总条数
     */
    long countByTagId(@Param("tagId") Long tagId);

    /**
     * 某标签下已关联的工号集合（导入/新增时去重用，单次全量）。
     *
     * @param tagId 标签 ID
     * @return 工号列表
     */
    List<String> selectUsernamesByTagId(@Param("tagId") Long tagId);

    /**
     * 按标签 ID 集合查全部关联（全局导入去重用，单次 IN）。
     *
     * @param tagIds 标签 ID 集合（非空）
     * @return 关联行列表
     */
    List<PersonTagRel> selectByTagIds(@Param("tagIds") List<Long> tagIds);

    /**
     * 删除某标签下全部关联（删除标签级联 / 成员导入全量覆盖用）。
     *
     * @param tagId 标签 ID
     * @return 删除行数
     */
    int deleteByTagId(@Param("tagId") Long tagId);

    /**
     * 批量插入关联行（导入用，单条 multi-values INSERT）。
     *
     * @param rows 关联行（tagId/username/createBy 已填）
     * @return 插入行数
     */
    int insertBatch(@Param("rows") List<PersonTagRel> rows);
}
