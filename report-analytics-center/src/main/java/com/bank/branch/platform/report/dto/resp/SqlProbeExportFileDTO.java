package com.bank.branch.platform.report.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 探查导出文件下载载体（文件名 + 字节内容）.
 *
 * <p>控制器不直接接触实体，由 Service 完成归属/状态校验并返回文件元数据；新任务通过
 * {@code fileId} 调治理中心流式输出，旧任务通过 {@code content} 兼容返回。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlProbeExportFileDTO {

    /** 下载文件名 */
    private String fileName;

    /** OBS 文件对象 ID；新任务优先走 FileApi 流式读取。 */
    private String fileId;

    /** 文件 MIME 类型。 */
    private String contentType;

    /** 文件大小（若治理中心可提供）。 */
    private Long fileSize;

    /** 兼容旧任务：FILE_CONTENT 中仍保存完整文件字节时使用。 */
    private byte[] content;
}
