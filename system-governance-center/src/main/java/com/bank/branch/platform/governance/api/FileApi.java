package com.bank.branch.platform.governance.api;

import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理对外API
 * <p>
 * 提供统一的文件上传/下载/关联管理能力。
 * 文件存储在 MinIO 对象存储中。
 * </p>
 */
public interface FileApi {

    /**
     * 上传文件到 MinIO
     * 内部进行格式白名单校验和大小限制校验，支持 MD5 去重
     *
     * @param file       文件（MultipartFile）
     * @param uploadedBy 上传人工号
     * @return 文件对象信息
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-42203 文件格式不在白名单
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-42204 文件大小超限
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-50001 MinIO 存储异常
     */
    FileObjectDTO upload(MultipartFile file, String uploadedBy);

    /**
     * 获取文件下载URL（MinIO 预签名 URL，有效期 1 小时）
     *
     * @param fileId 文件对象ID
     * @return 预签名下载 URL
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40005 文件不存在
     */
    String getDownloadUrl(String fileId);

    /**
     * 关联文件到业务对象
     * 幂等：相同 bizType + bizId + fileObjectId 不会重复创建关联
     *
     * @param bizType      业务类型（如 LEAD、CUSTOMER）
     * @param bizId        业务ID
     * @param fileObjectId 文件对象ID
     * @param fileRole     文件用途（ATTACHMENT / PHOTO / ...），可为 null
     */
    void bindFile(String bizType, String bizId, String fileObjectId, String fileRole);

    /**
     * 查询业务关联的文件列表
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 文件对象列表（按 created_time 升序）
     */
    List<FileObjectDTO> listBizFiles(String bizType, String bizId);

    /**
     * 删除文件
     * 解除所有 biz_file_rel 关联，删除 file_object 记录
     *
     * @param fileId 文件对象ID
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40005 文件不存在
     */
    void deleteFile(String fileId);
}
