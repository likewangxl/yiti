package com.bank.branch.platform.auth.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 命名机构组跨模块 DTO。 */
@Data
public class OrgGroupDTO {

    private Long id;
    private String groupCode;
    private String groupName;
    private String groupPurpose;
    private String status;
    private Integer version;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private String remark;
    private List<String> memberOrgCodes;
    private List<String> roleCodes;

    /**
     * 以集合形式读取直接成员，兼容 report 发布校验的只读快照语义。
     * 返回新集合，避免跨模块调用方修改 DTO 内部列表。
     */
    public Set<String> memberCodes() {
        return memberOrgCodes == null ? Set.of() : new LinkedHashSet<>(memberOrgCodes);
    }
}
