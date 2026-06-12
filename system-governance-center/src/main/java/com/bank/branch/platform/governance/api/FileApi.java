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
     * 上传文件到 OBS，带类型前缀 category（OBS 对象名形如 {prefix}_{uuid}）。
     *
     * @param file       文件
     * @param uploadedBy 上传人工号
     * @param category   类型前缀，见 {@link com.bank.branch.platform.governance.storage.FileCategory}
     * @return 文件对象信息
     */
    FileObjectDTO upload(MultipartFile file, String uploadedBy, String category);

    /**
     * 字节直传到 OBS（导出/导入/公告等无 MultipartFile 场景）。
     *
     * @param bytes       文件内容
     * @param filename    原始文件名（用于取扩展名与展示）
     * @param contentType MIME 类型
     * @param uploadedBy  上传人工号
     * @param category    类型前缀，见 {@link com.bank.branch.platform.governance.storage.FileCategory}
     * @return 文件对象信息
     */
    FileObjectDTO upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category);

    /**
     * 读取文件字节内容（供业务模块自行流式下载，替代原 getFilePath 本地读盘）。
     *
     * @param fileId 文件对象ID
     * @return 文件字节内容
     * @throws com.bank.branch.platform.common.web.exception.BizException GOV-40005 文件不存在
     */
    byte[] getFileContent(String fileId);

    /**
     * 获取文件下载URL（OBS 预签名临时 URL）
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

    /**
     * Get original file name for Content-Disposition header.
     *
     * @param fileId file object id
     * @return original file name; "file" if id not found
     */
    String getFileName(String fileId);
}
