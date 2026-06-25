package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 探查导出文件下载载体（文件名 + 字节内容）.
 *
 * <p>控制器不直接接触实体，由 Service 完成归属/状态校验并取出文件后返回本 DTO。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlProbeExportFileDTO {

    /** 下载文件名 */
    private String fileName;

    /** 文件字节内容 */
    private byte[] content;
}
