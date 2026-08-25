package com.bank.branch.platform.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.customer.entity.CustTransferLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 客户转交记录 Mapper。 */
@Mapper
public interface CustTransferLogMapper extends BaseMapper<CustTransferLog> {
    /** 按机构范围和关键词查询转交记录。 */
    List<CustTransferLog> selectVisible(@Param("keyword") String keyword,
                                        @Param("orgId") String orgId,
                                        @Param("allData") boolean allData);
}
