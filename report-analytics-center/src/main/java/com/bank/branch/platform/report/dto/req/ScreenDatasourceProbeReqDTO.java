package com.bank.branch.platform.report.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 已保存数据源的管理端列探测请求。
 *
 * <p>这是设计器需要的受审计探测能力；它有独立管理资源，绝不可经运行时
 * {@code /api/screen/data} 的 schemaVersion=1 兼容链路取得任意数据源结果。</p>
 */
@Data
public class ScreenDatasourceProbeReqDTO {

    /** 预设周期，空时按 LATEST 处理。 */
    private String period;

    private String dateFrom;

    private String dateTo;

    /** 仅非 NAMED_GROUP 数据源使用的受限上下文参数。 */
    private Map<String, String> contextParams;

    /** NAMED_GROUP 数据源探测时必须指定，服务端解析机构组成员。 */
    private String testOrgGroupCode;

    /** 高危 SQL/数据探测操作的审计原因。 */
    @NotBlank
    private String reason;
}
