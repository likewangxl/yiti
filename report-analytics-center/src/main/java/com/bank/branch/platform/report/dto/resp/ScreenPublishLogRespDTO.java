package com.bank.branch.platform.report.dto.resp;

import lombok.Data;
import java.time.LocalDateTime;

/** 发布归档条目(回滚选择列表用,不回传 snapshotJson 全文). */
@Data
public class ScreenPublishLogRespDTO {
    private Long id;
    private Long screenId;
    private String publishedBy;
    private LocalDateTime publishedAt;
}
