package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.PersonTagRel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业务标签-成员关联 Mapper，操作 PERSON_TAG_REL 表。
 * <p>成员按维度分两类：EMP 行 USERNAME 存工号（PT_USER.USERNAME 口径）、ORG 行 ORG_DEPT_NO
 * 存机构业务编号（EXT_ORG_INFO.DEPT_NO 口径）；分页/计数/去重均按维度隔离。</p>
 */
@Mapper
public interface PersonTagRelMapper extends BaseMapper<PersonTagRel> {

    /**
     * 分页查询某标签某维度下的成员行，按创建时间/ID 升序（稳定顺序）。
     *
     * @param tagId   标签 ID
     * @param dimType 成员维度（EMP/ORG）
     * @param offset  偏移
     * @param size    页大小
     * @return 成员行列表
     */
    List<PersonTagRel> selectPageByTagId(@Param("tagId") Long tagId,
                                         @Param("dimType") String dimType,
                                         @Param("offset") int offset,
                                         @Param("size") int size);

    /**
     * 某标签某维度下成员总数。
     *
     * @param tagId   标签 ID
     * @param dimType 成员维度（EMP/ORG）
     * @return 总条数
     */
    long countByTagId(@Param("tagId") Long tagId, @Param("dimType") String dimType);

    /**
     * 某标签下已关联的员工工号集合（EMP 维度去重用，单次全量）。
     *
     * @param tagId 标签 ID
     * @return 工号列表
     */
    List<String> selectUsernamesByTagId(@Param("tagId") Long tagId);

    /**
     * 某标签下已关联的机构编号集合（ORG 维度去重用，单次全量）。
     *
     * @param tagId 标签 ID
     * @return 机构编号（DEPT_NO）列表
     */
    List<String> selectDeptNosByTagId(@Param("tagId") Long tagId);

    /**
     * 按标签 ID 集合查全部关联（全局导入去重 / 对外按标签取成员用，单次 IN）。
     *
     * @param tagIds 标签 ID 集合（非空）
     * @return 成员行列表（含 EMP/ORG 两维）
     */
    List<PersonTagRel> selectByTagIds(@Param("tagIds") List<Long> tagIds);

    /**
     * 删除某标签下全部成员（删除标签级联用，不分维度）。
     *
     * @param tagId 标签 ID
     * @return 删除行数
     */
    int deleteByTagId(@Param("tagId") Long tagId);

    /**
     * 删除某标签某维度下的全部成员（成员导入按维度全量覆盖用，不动另一维度）。
     *
     * @param tagId   标签 ID
     * @param dimType 成员维度（EMP/ORG）
     * @return 删除行数
     */
    int deleteByTagIdAndDim(@Param("tagId") Long tagId, @Param("dimType") String dimType);

    /**
     * 批量插入成员行（导入/新增用，单条 multi-values INSERT）。
     *
     * @param rows 成员行（tagId/dimType/username 或 orgDeptNo/createBy 已填）
     * @return 插入行数
     */
    int insertBatch(@Param("rows") List<PersonTagRel> rows);
}
