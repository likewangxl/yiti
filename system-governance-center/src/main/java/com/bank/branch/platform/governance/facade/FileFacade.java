package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理 Facade 实现
 * <p>
 * 实现 FileApi 接口，委托给 FileService 处理实际业务逻辑。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileFacade implements FileApi {

    private final FileService fileService;

    /**
     * 上传文件
     *
     * @param file       上传的文件
     * @param uploadedBy 上传人工号
     * @return 文件对象 DTO
     */
    @Override
    public FileObjectDTO upload(MultipartFile file, String uploadedBy) {
        return fileService.upload(file, uploadedBy, null, null);
    }

    /**
     * 获取文件预签名下载 URL
     *
     * @param fileId 文件对象ID
     * @return 预签名下载 URL
     */
    @Override
    public String getDownloadUrl(String fileId) {
        return fileService.getDownloadUrl(fileId);
    }

    /**
     * 关联文件到业务对象
     *
     * @param bizType      业务类型
     * @param bizId        业务ID
     * @param fileObjectId 文件对象ID
     * @param fileRole     文件用途
     */
    @Override
    public void bindFile(String bizType, String bizId, String fileObjectId, String fileRole) {
        fileService.bindFile(bizType, bizId, fileObjectId, fileRole);
    }

    /**
     * 查询业务关联的文件列表
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 文件对象 DTO 列表
     */
    @Override
    public List<FileObjectDTO> listBizFiles(String bizType, String bizId) {
        return fileService.listBizFiles(bizType, bizId);
    }

    /**
     * 删除文件
     *
     * @param fileId 文件对象ID
     */
    @Override
    public void deleteFile(String fileId) {
        fileService.deleteFile(fileId);
    }
}
