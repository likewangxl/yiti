package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 已废弃的大屏元数据兼容请求。
 *
 * <p>HTTP 管理端不再使用该 DTO：创建走 {@link ScreenCreateReqDTO}，更新走
 * {@link ScreenMetadataUpdateReqDTO}。保留仅为少量 Java 调用方平滑迁移；它不再含
 * blocks，也不会触碰画布区块或屏级角色。</p>
 */
@Deprecated(since = "2026-08-11", forRemoval = false)
@Data
public class ScreenSaveReqDTO {

    /** 空=新建；非空=更新 */
    private Long id;

    /** 空则服务端生成 SCR_XXXXXXXX */
    private String screenCode;

    @NotBlank
    private String screenName;

    /** PROVINCE / BRANCH / PERSON */
    @NotBlank
    private String viewLevel;

    /** CORP / RETAIL / COMMON；新建必须显式提交，缺省仅兼容既有记录的更新。 */
    private String bizLine;

    /** LEGACY_CONTEXT / NAMED_GROUP；新建必须显式提交，缺省仅兼容既有记录的更新。 */
    private String orgScopeMode;

    /** NAMED_GROUP 时绑定的 auth 机构组编码。 */
    private String orgGroupCode;

    private String themeJson;

    /** ACTIVE / DISABLED（空=ACTIVE） */
    private String status;

}
