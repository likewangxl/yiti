package com.bank.branch.platform.customer.dto.marketing.tag;

import lombok.Data;

import java.util.List;

/** 标签客户导入预览结果，不代表已写入正式标签关系。 */
@Data
public class TagImportPreviewResponse {
    private Long tagId;
    private String tagName;
    private String importMode;
    private String sourceFileName;
    private int totalCount;
    private int validCount;
    private int errorCount;
    private List<TagImportRow> rows;
}
