package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.entity.BizFileRel;
import com.bank.branch.platform.governance.entity.FileObject;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.BizFileRelMapper;
import com.bank.branch.platform.governance.mapper.FileObjectMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件管理服务
 * <p>
 * 提供文件上传（含格式/大小校验、MD5 去重）、下载 URL 生成、
 * 业务关联绑定、关联查询和文件删除等能力。
 * 文件实际存储在 MinIO 对象存储中。
 * </p>
 */
@Slf4j
@Service
public class FileService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final FileObjectMapper fileObjectMapper;
    private final BizFileRelMapper bizFileRelMapper;

    /** 允许上传的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "zip", "rar"
    );

    /** 最大文件大小：50MB */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 日期格式化器，用于生成存储路径 */
    private static final DateTimeFormatter PATH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 构造函数
     *
     * @param minioClient      MinIO 客户端
     * @param bucketName       存储桶名称
     * @param fileObjectMapper 文件对象 Mapper
     * @param bizFileRelMapper 业务关联 Mapper
     */
    public FileService(MinioClient minioClient, String bucketName,
                       FileObjectMapper fileObjectMapper, BizFileRelMapper bizFileRelMapper) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.fileObjectMapper = fileObjectMapper;
        this.bizFileRelMapper = bizFileRelMapper;
    }

    /**
     * 上传文件到 MinIO
     * <p>
     * 执行流程：格式校验 → 大小校验 → 计算 MD5 → 去重检查 → 上传 MinIO → 插入记录 → 绑定业务关联（若传入 bizType/bizId）。
     * 若 MD5 相同的文件已存在，直接返回已有记录，跳过重复上传。
     * 若传入了 bizType 和 bizId，在上传成功后自动建立业务关联。
     * </p>
     *
     * @param file       上传的文件
     * @param uploadedBy 上传人工号
     * @param bizType    业务类型（可选）
     * @param bizId      业务ID（可选）
     * @return 文件对象 DTO
     * @throws BizException GOV-42203 文件格式不合法
     * @throws BizException GOV-42204 文件大小超限
     * @throws BizException GOV-50001 MinIO 存储异常
     */
    @Transactional
    public FileObjectDTO upload(MultipartFile file, String uploadedBy, String bizType, String bizId) {
        log.info("[FileService.upload] fileName={}, size={}, uploadedBy={}, bizType={}, bizId={}",
                file.getOriginalFilename(), file.getSize(), uploadedBy, bizType, bizId);

        // 1. 校验文件格式
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BizException(GovErrorCode.FILE_FORMAT_INVALID.getCode(),
                    GovErrorCode.FILE_FORMAT_INVALID.getMessage());
        }

        // 2. 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(GovErrorCode.FILE_SIZE_EXCEEDED.getCode(),
                    GovErrorCode.FILE_SIZE_EXCEEDED.getMessage());
        }

        // 3. 计算 MD5 哈希值
        String md5Hash;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md5Hash = String.format("%032x", new BigInteger(1, md.digest(file.getBytes())));
        } catch (Exception e) {
            throw new BizException(GovErrorCode.MINIO_ERROR.getCode(),
                    "MD5 计算失败: " + e.getMessage(), e);
        }

        // 4. 去重检查：MD5 相同则直接返回已有记录
        FileObject existing = fileObjectMapper.selectByMd5Hash(md5Hash);
        if (existing != null) {
            log.info("[FileService.upload] MD5 重复，返回已有记录 id={}", existing.getId());
            return toDTO(existing);
        }

        // 5. 生成存储路径
        LocalDateTime now = LocalDateTime.now();
        String storagePath = "/" + now.format(PATH_DATE_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 6. 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BizException(GovErrorCode.MINIO_ERROR.getCode(),
                    GovErrorCode.MINIO_ERROR.getMessage() + ": " + e.getMessage(), e);
        }

        // 7. 插入文件对象记录
        String id = "F_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        FileObject fileObject = new FileObject();
        fileObject.setId(id);
        fileObject.setFileName(originalFilename);
        fileObject.setFileSize(file.getSize());
        fileObject.setFileType(file.getContentType());
        fileObject.setStoragePath(storagePath);
        fileObject.setBucketName(bucketName);
        fileObject.setMd5Hash(md5Hash);
        fileObject.setUploadedBy(uploadedBy);
        fileObject.setUploadedTime(now);
        fileObjectMapper.insert(fileObject);

        FileObjectDTO dto = toDTO(fileObject);

        // 如果传入了 bizType 和 bizId，建立业务关联
        if (bizType != null && bizId != null && !bizType.isEmpty() && !bizId.isEmpty()) {
            bindFile(bizType, bizId, fileObject.getId(), null);
        }

        log.info("[FileService.upload] 文件上传成功 id={}, path={}", id, storagePath);
        return dto;
    }

    /**
     * 获取文件预签名下载 URL
     * <p>
     * 通过 MinIO 预签名机制生成有效期 1 小时的临时下载链接。
     * </p>
     *
     * @param fileId 文件对象ID
     * @return 预签名下载 URL
     * @throws BizException GOV-40005 文件不存在
     * @throws BizException GOV-50001 MinIO 服务异常
     */
    public String getDownloadUrl(String fileId) {
        log.info("[FileService.getDownloadUrl] fileId={}", fileId);
        FileObject fileObject = fileObjectMapper.selectById(fileId);
        if (fileObject == null) {
            throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(),
                    GovErrorCode.FILE_NOT_FOUND.getMessage());
        }

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(fileObject.getBucketName())
                            .object(fileObject.getStoragePath())
                            .method(Method.GET)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            throw new BizException(GovErrorCode.MINIO_ERROR.getCode(),
                    GovErrorCode.MINIO_ERROR.getMessage() + ": " + e.getMessage(), e);
        }
    }

    /**
     * 关联文件到业务对象
     * <p>
     * 在 biz_file_rel 表中创建关联记录。
     * 若关联已存在则跳过，保证幂等性。
     * </p>
     *
     * @param bizType      业务类型
     * @param bizId        业务ID
     * @param fileObjectId 文件对象ID
     * @param fileRole     文件用途
     * @throws BizException GOV-40005 文件不存在
     */
    @Transactional
    public void bindFile(String bizType, String bizId, String fileObjectId, String fileRole) {
        log.info("[FileService.bindFile] bizType={}, bizId={}, fileObjectId={}, fileRole={}",
                bizType, bizId, fileObjectId, fileRole);

        // 检查文件是否存在
        FileObject fileObject = fileObjectMapper.selectById(fileObjectId);
        if (fileObject == null) {
            throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(),
                    GovErrorCode.FILE_NOT_FOUND.getMessage());
        }

        // 幂等：已存在则跳过
        if (bizFileRelMapper.existsByBizTypeAndBizIdAndFileObjectId(bizType, bizId, fileObjectId)) {
            log.info("[FileService.bindFile] 关联已存在，跳过 bizType={}, bizId={}, fileObjectId={}",
                    bizType, bizId, fileObjectId);
            return;
        }

        // 插入关联记录
        String id = "R_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        BizFileRel rel = new BizFileRel();
        rel.setId(id);
        rel.setBizType(bizType);
        rel.setBizId(bizId);
        rel.setFileObjectId(fileObjectId);
        rel.setFileRole(fileRole);
        rel.setCreatedBy(fileObject.getUploadedBy());
        rel.setCreatedTime(LocalDateTime.now());
        bizFileRelMapper.insert(rel);

        log.info("[FileService.bindFile] 关联创建成功 id={}", id);
    }

    /**
     * 查询业务关联的文件列表
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 文件对象 DTO 列表（按关联创建时间升序）
     */
    public List<FileObjectDTO> listBizFiles(String bizType, String bizId) {
        log.debug("[FileService.listBizFiles] bizType={}, bizId={}", bizType, bizId);
        List<BizFileRel> rels = bizFileRelMapper.selectByBizTypeAndBizId(bizType, bizId);
        List<FileObjectDTO> result = new ArrayList<>();
        for (BizFileRel rel : rels) {
            FileObject fo = fileObjectMapper.selectById(rel.getFileObjectId());
            if (fo != null) {
                FileObjectDTO dto = toDTO(fo);
                dto.setFileRole(rel.getFileRole());
                result.add(dto);
            }
        }
        return result;
    }

    /**
     * 删除文件
     * <p>
     * 删除文件对象记录及所有业务关联记录。
     * MinIO 中的实际文件不删除，保留用于审计和恢复。
     * </p>
     *
     * @param fileId 文件对象ID
     * @throws BizException GOV-40005 文件不存在
     */
    @Transactional
    public void deleteFile(String fileId) {
        log.info("[FileService.deleteFile] fileId={}", fileId);
        FileObject fileObject = fileObjectMapper.selectById(fileId);
        if (fileObject == null) {
            throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(),
                    GovErrorCode.FILE_NOT_FOUND.getMessage());
        }

        // 删除文件对象记录
        fileObjectMapper.deleteById(fileId);
        // 删除所有业务关联记录
        bizFileRelMapper.deleteByFileObjectId(fileId);
        // 注意：MinIO 中的文件不删除，保留用于审计/恢复

        log.info("[FileService.deleteFile] 文件删除成功 fileId={}", fileId);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 提取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（小写），无扩展名时返回空字符串
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 将 FileObject 实体转换为 FileObjectDTO
     *
     * @param entity 文件对象实体
     * @return FileObjectDTO
     */
    private FileObjectDTO toDTO(FileObject entity) {
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId(entity.getId());
        dto.setFileName(entity.getFileName());
        dto.setFileSize(entity.getFileSize());
        dto.setFileType(entity.getFileType());
        dto.setMd5Hash(entity.getMd5Hash());
        dto.setUploadedBy(entity.getUploadedBy());
        dto.setUploadedTime(entity.getUploadedTime() != null
                ? entity.getUploadedTime().toString() : null);
        return dto;
    }
}
