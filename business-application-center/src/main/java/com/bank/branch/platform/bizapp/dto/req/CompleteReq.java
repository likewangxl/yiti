package com.bank.branch.platform.bizapp.dto.req;

import lombok.Data;

/**
 * 办理完成请求 DTO。
 */
@Data
public class CompleteReq {

    /** 是否成功完成（true=完成，false=拒绝） */
    private boolean success;

    /** 办理结果/总结，写入 RESULT 过程记录并作为流程表单变量。 */
    private String handleResult;

    /** 兼容设计稿字段名 summary。 */
    private String summary;

    /** 设计稿结果枚举：SUCCESS/FAILED/CANCELLED。未提供时由 success 推导。 */
    private String result;

    /** 办理成果照片/附件文件 ID，复用 SUPPORT_LOG 文件关联。 */
    private java.util.List<String> outputAttachmentIds;

    /** 兼容客户端字段名 fileIds。 */
    private java.util.List<String> fileIds;
}
