package com.bank.branch.platform.yundun.mapper;

import com.bank.branch.platform.yundun.entity.AccountabilityViolation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 人员违规问责 Mapper。 */
public interface AccountabilityViolationMapper extends BaseMapper<AccountabilityViolation> {

    /** 使用一条多值 INSERT 写入一批人员违规记录。 */
    int insertBatch(@Param("rows") List<AccountabilityViolation> rows);
}
