package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 担保信息 Mapper。
 *
 * <p>遵循 MyBatis-Plus 规范：单条 CRUD / 批量删除 / 分页查询走 {@link BaseMapper} 内置方法
 * + {@code LambdaQueryWrapper}；BaseMapper 覆盖不到的批量插入落自定义 XML
 * （{@code ZhGuaranteeInfoMapper.xml}）。</p>
 */
@Mapper
public interface ZhGuaranteeInfoMapper extends BaseMapper<ZhGuaranteeInfo> {

    /**
     * 批量插入担保信息（合同导入场景，单条 SQL 多 VALUES）。
     *
     * @param list 待插入实体列表（非空）
     * @return 实际插入行数
     */
    int batchInsert(@Param("list") List<ZhGuaranteeInfo> list);
}
