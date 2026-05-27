package com.bank.branch.platform.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 机构创建请求 DTO */
@Data
public class OrgCreateReqDTO {

    @NotBlank
    @Size(max = 20)
    private String orgCode;

    @NotBlank
    @Size(max = 100)
    private String orgName;

    /** 上级机构编码 P_ID，根节点传空字符串或省略。
     *  Lombok 默认把 pId getter 转 getPid()，Jackson 序列化 key 也会变 pid；
     *  显式 @JsonProperty 锁定 JSON key 仍为 "pId"，与前端 / DB 列名保持一致 */
    @JsonProperty("pId")
    @Size(max = 20)
    private String pId;

    /** 机构层级，可选；前端不传时后端按父级 + 1 */
    private Integer orgLevel;
}
