package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CustTransferTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 客户转交接收人 Mapper。 */
@Mapper
public interface CustTransferTargetMapper extends BaseMapper<CustTransferTarget> {
    /** 批量保存接收人快照。 */
    int insertBatch(@Param("list") List<CustTransferTarget> list);

    /** 查询多笔转交记录的接收人。 */
    List<CustTransferTarget> selectByTransferIds(@Param("transferIds") List<String> transferIds);
}
