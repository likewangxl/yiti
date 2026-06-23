package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 担保信息 Mapper。
 *
 * <p>遵循 MyBatis-Plus 规范：单条 CRUD / 批量删除 / 分页查询全部走 {@link BaseMapper} 内置方法
 * + {@code LambdaQueryWrapper}，无自定义 XML。</p>
 */
@Mapper
public interface ZhGuaranteeInfoMapper extends BaseMapper<ZhGuaranteeInfo> {
}
