package com.bank.branch.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 机构信息 Mapper 接口，操作 EXT_ORG_INFO 表。
 * <p>
 * EXT_ORG_INFO 为外部系统同步的只读参考表，本模块仅做查询使用。
 * selectAll() 适合在系统启动时缓存完整机构树；
 * selectChildren() 用于按层级懒加载机构节点。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，标准 CRUD 由 BaseMapper 提供。
 * 本 Mapper 所有方法均为自定义查询，不与 BaseMapper 冲突，全部保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface OrgMapper extends BaseMapper<ExtOrgInfo> {

    /**
     * 根据机构编码查询机构信息（利用唯一索引 uk_ext_org_info_org_code）。
     *
     * @param orgCode 机构编码
     * @return 机构实体，不存在时返回 null
     */
    ExtOrgInfo selectByOrgCode(String orgCode);

    /**
     * 根据机构编号（DEPT_NO）查询机构信息.
     *
     * @param deptNo 机构编号（EXT_ORG_INFO.DEPT_NO）
     * @return 机构实体，不存在时返回 null（多条时取首条）
     */
    ExtOrgInfo selectByDeptNo(String deptNo);

    /**
     * 按机构编码集合批量查询（命中唯一索引 uk_ext_org_info_org_code）。
     * <p>供批量名称回填等场景一次取数，替代逐个 {@link #selectByOrgCode} 的 N+1。</p>
     *
     * @param codes 机构编码集合（调用方保证非空、已去重去空白）
     * @return 命中的机构实体列表（无命中返回空列表）
     */
    List<ExtOrgInfo> selectByOrgCodes(@Param("codes") java.util.Collection<String> codes);

    /**
     * 查询全量机构信息列表（不过滤 ORGAN_STATE），用于构建完整机构树。
     *
     * @return 全部机构列表
     */
    List<ExtOrgInfo> selectAll();

    /**
     * 查询指定父机构编码下的直接子机构列表（利用索引 idx_p_id）。
     *
     * @param parentOrgCode 上级机构编码，对应 P_ID 字段
     * @return 子机构列表
     */
    List<ExtOrgInfo> selectChildren(String parentOrgCode);

    /**
     * 根据关键字搜索机构（模糊匹配 ORG_CODE 和 ORG_NAME），用于下拉选择场景。
     * 结果数量由 limit 控制，建议不超过 50。
     *
     * @param keyword 搜索关键字
     * @param limit   结果数量上限
     * @return 机构列表
     */
    List<ExtOrgInfo> searchByKeyword(@Param("keyword") String keyword,
                                     @Param("limit") int limit);

    /**
     * 查询当前最大的"纯数字"机构编码（CAST 为无符号整型）。
     * <p>用于新增机构时后端自增生成 ORG_CODE = max + 1。非数字编码（如历史 ORGxxx）不参与。</p>
     *
     * @return 最大数字编码，无记录或全非数字时返回 null
     */
    Long selectMaxNumericOrgCode();
}
