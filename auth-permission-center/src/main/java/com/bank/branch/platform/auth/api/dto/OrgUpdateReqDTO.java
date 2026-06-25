package com.bank.branch.platform.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 机构更新请求 DTO（支持改名 / 改上级 / 改状态；不支持改 ORG_CODE） */
@Data
public class OrgUpdateReqDTO {

    @Size(max = 100)
    private String orgName;

    /** 机构状态：0-启用，1-禁用。为 null 时不改状态（仅改名）。禁用时机构下有用户则拒绝。 */
    private Integer organState;

    /**
     * 上级机构编码（P_ID）。为 null 时不改上级；为空串 "" 表示设为根节点。
     * 后端校验：不能选自身、不能选自身的子孙（避免成环）。
     * Lombok 默认把 pId getter 转 getPid()，Jackson key 也会变 pid；
     * 显式 @JsonProperty 锁定 JSON key 为 "pId"，与前端/OrgCreateReqDTO 一致。
     */
    @JsonProperty("pId")
    private String pId;
}
