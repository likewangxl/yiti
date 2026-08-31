package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.redengine.api.dto.ReTaskAttachmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskFileDownloadDTO;

import java.util.List;

/** 任务附件归属校验与治理文件访问服务。 */
public interface ReTaskFileService {

    /** 查询当前用户有权查看的 assignment 当前版本附件。 */
    List<ReTaskAttachmentDTO> listAttachments(Long assignmentId, String operatorId);

    /** 校验红色引擎业务归属后读取治理中心文件内容。 */
    ReTaskFileDownloadDTO download(Long taskId, Long assignmentId, String fileId, String operatorId);
}
