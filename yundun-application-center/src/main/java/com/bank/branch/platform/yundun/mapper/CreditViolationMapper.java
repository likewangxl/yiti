package com.bank.branch.platform.yundun.mapper;

import com.bank.branch.platform.yundun.entity.CreditViolation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 信贷风险责任认定 Mapper。 */
public interface CreditViolationMapper extends BaseMapper<CreditViolation> {

    /** 使用一条多值 INSERT 写入一批信贷风险记录。 */
    int insertBatch(@Param("rows") List<CreditViolation> rows);
}
