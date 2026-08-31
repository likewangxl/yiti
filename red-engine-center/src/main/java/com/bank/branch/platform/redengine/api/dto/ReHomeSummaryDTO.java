package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 按当前登录角色裁剪后的红色引擎首页汇总。 */
@Data
@Schema(description = "红色引擎首页汇总")
public class ReHomeSummaryDTO {

    /** ORGANIZATION（组织管理员/组织审核员）或 INSTITUTION（报送员/支部书记）。 */
    private String mode;
    private String quarter;
    private Long branchId;
    private String branchName;
    private BigDecimal branchScore;
    private Integer branchRank;
    private Integer branchCount;
    private long todoCount;
    private long completedCount;
    private long organizationTaskCount;
    private List<ReHomeTodoItemDTO> todoItems = new ArrayList<>();
    private List<ReHomeTodoItemDTO> completedItems = new ArrayList<>();
    private List<ReHomeTaskItemDTO> organizationTasks = new ArrayList<>();
    private List<ReHomeBranchRankingDTO> branchRankings = new ArrayList<>();

    /** 兼容前端“所在机构得分”字段命名。 */
    public BigDecimal getInstitutionScore() {
        return branchScore;
    }

    /** 兼容前端“所在机构得分”字段命名。 */
    public void setInstitutionScore(BigDecimal score) {
        this.branchScore = score;
    }

    /** 兼容前端“所在机构排名”字段命名。 */
    public Integer getInstitutionRank() {
        return branchRank;
    }

    /** 兼容前端“所在机构排名”字段命名。 */
    public void setInstitutionRank(Integer rank) {
        this.branchRank = rank;
    }

    /** 兼容首页组件使用的“组织名称”字段命名。 */
    public String getOrganizationName() {
        return branchName;
    }

    /** 兼容首页组件使用的“组织名称”字段命名。 */
    public void setOrganizationName(String name) {
        this.branchName = name;
    }

    /** 兼容首页组件使用的“组织得分”字段命名。 */
    public BigDecimal getOrganizationScore() {
        return branchScore;
    }

    /** 兼容首页组件使用的“组织得分”字段命名。 */
    public void setOrganizationScore(BigDecimal score) {
        this.branchScore = score;
    }

    /** 兼容首页组件使用的“组织排名”字段命名。 */
    public Integer getOrganizationRank() {
        return branchRank;
    }

    /** 兼容首页组件使用的“组织排名”字段命名。 */
    public void setOrganizationRank(Integer rank) {
        this.branchRank = rank;
    }
}
