package com.bank.branch.platform.redengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.redengine.entity.ReItemCode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 四维明细项只读 Mapper。
 *
 * <p>逻辑字典 {@code RE_ITEM_CODE} 映射到现有 {@code SYS_DICT} 表；服务层只调用查询方法，
 * 不通过该 Mapper 执行任何写操作。</p>
 */
@Mapper
public interface ReItemCodeMapper extends BaseMapper<ReItemCode> {
}
