package com.bank.branch.platform.report.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.bank.branch.platform.report.support.ScreenDataReqDTODeserializer;
import com.bank.branch.platform.report.support.StrictJsonIntegerDeserializer;
import com.bank.branch.platform.report.support.StrictJsonLongDeserializer;
import lombok.Data;

import java.util.Map;
import java.util.List;

/**
 * 大屏统一取数请求.
 */
@Data
@JsonDeserialize(using = ScreenDataReqDTODeserializer.class)
public class ScreenDataReqDTO {

    /**
     * 运行取数契约版本。v1 必须精确传 {@code 1} 且同时携带 screenCode + dsId，服务端核验
     * dsId 属于该屏当前发布包的可信不可变绑定；v2 必须精确传 {@code 2} 并以 screenCode + blockId 解析发布快照。
     */
    @JsonDeserialize(using = StrictJsonIntegerDeserializer.class)
    private Integer schemaVersion;

    /** schemaVersion=1 的当前发布包历史兼容数据源 ID；schemaVersion=2 由服务端解析，客户端不得覆盖。 */
    @JsonDeserialize(using = StrictJsonLongDeserializer.class)
    private Long dsId;

    /** v1/v2 均必填的运行时屏编码。 */
    private String screenCode;

    /** schemaVersion=2 当前发布快照中的区块 ID。 */
    @JsonDeserialize(using = StrictJsonLongDeserializer.class)
    private Long blockId;

    /** 管理端草稿预览取数标记；仅允许显式 draft，发布态请求不得借此改变身份语义。 */
    private String previewState;

    /** 预设周期：LATEST/LAST_10D/LAST_1M/LAST_6M_EOM/RANGE（空=LATEST） */
    private String period;

    /** RANGE 时必填 yyyy-MM-dd */
    private String dateFrom;

    /** RANGE 时必填 yyyy-MM-dd */
    private String dateTo;

    /** 上下文参数：orgCode / empId（大屏路由参数透传） */
    private Map<String, String> contextParams;

    /** 服务端授权后注入的机构集合，不接受 JSON 输入。 */
    @JsonIgnore
    private List<String> serverOrgCodes;

    /** 服务端标记：命名机构组请求。 */
    @JsonIgnore
    private boolean namedGroup;
}
