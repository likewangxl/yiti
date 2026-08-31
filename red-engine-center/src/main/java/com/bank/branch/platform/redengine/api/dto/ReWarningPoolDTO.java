package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 红黄牌预警池汇总；所有已认证角色均可读取。 */
@Data
@Schema(description = "红色引擎红黄牌预警池")
public class ReWarningPoolDTO {

    private String quarter;
    private String previousQuarter;
    private List<ReQuarterWarningDTO> redBranches = new ArrayList<>();
    private List<ReQuarterWarningDTO> yellowBranches = new ArrayList<>();
}
