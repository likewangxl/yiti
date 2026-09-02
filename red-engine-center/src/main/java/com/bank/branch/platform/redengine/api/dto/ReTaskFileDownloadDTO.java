package com.bank.branch.platform.redengine.api.dto;

import lombok.Data;

/** 已完成红色引擎归属校验的附件下载载荷。 */
@Data
public class ReTaskFileDownloadDTO {

    private String fileName;
    private String contentType;
    private byte[] content;
}
