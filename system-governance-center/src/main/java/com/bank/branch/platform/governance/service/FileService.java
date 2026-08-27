package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.entity.BizFileRel;
import com.bank.branch.platform.governance.entity.FileObject;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.BizFileRelMapper;
import com.bank.branch.platform.governance.mapper.FileObjectMapper;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.governance.storage.ObsStorageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文件管理服务
 * <p>
 * 提供文件上传（含格式/大小校验、MD5 去重）、下载 URL 生成、
 * 业务关联绑定、关联查询和文件删除等能力。
 * 文件实际存储在华为云 OBS 对象存储中（{@link ObsStorageClient}）。
 * </p>
 */
@Slf4j
@Service
public class FileService {

    private final ObsStorageClient obsStorageClient;
    private final FileObjectMapper fileObjectMapper;
    private final BizFileRelMapper bizFileRelMapper;

    /** 允许上传的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "zip", "rar", "csv"
    );

    /** 最大文件大小：50MB */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 日期格式化器，用于生成 OBS 对象 key 前缀 */
    private static final DateTimeFormatter PATH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public FileService(ObsStorageClient obsStorageClient,
                       FileObjectMapper fileObjectMapper, BizFileRelMapper bizFileRelMapper) {
        this.obsStorageClient = obsStorageClient;
        this.fileObjectMapper = fileObjectMapper;
        this.bizFileRelMapper = bizFileRelMapper;
    }

    /**
     * 上传 MultipartFile 到 OBS（兼容旧调用，category 默认通用）。
     */
    @Transactional
    public FileObjectDTO upload(MultipartFile file, String uploadedBy, String bizType, String bizId) {
        return upload(file, uploadedBy, bizType, bizId, FileCategory.GENERAL);
    }

    /**
     * 上传 MultipartFile 到 OBS，带类型前缀 category。
     *
     * @param file       上传的文件
     * @param uploadedBy 上传人工号
     * @param bizType    业务类型（可选，传入则建立业务关联）
     * @param bizId      业务ID（可选）
     * @param category   类型前缀（见 {@link FileCategory}）
     * @return 文件对象 DTO
     */
    @Transactional
    public FileObjectDTO upload(MultipartFile file, String uploadedBy, String bizType, String bizId, String category) {
        log.info("[FileService.upload] fileName={}, size={}, uploadedBy={}, bizType={}, bizId={}, category={}",
                file.getOriginalFilename(), file.getSize(), uploadedBy, bizType, bizId, category);
        String filename = file.getOriginalFilename();
        String extension = validateExtension(filename);
        validateFileSize(file.getSize());

        // 第一遍只使用固定大小缓冲区计算 MD5，避免 MultipartFile.getBytes() 将文件整体读入堆内存。
        UploadDigest digest = calculateDigest(file);
        FileObject existing = fileObjectMapper.selectByMd5Hash(digest.md5Hash());
        if (existing != null) {
            log.info("[FileService.upload] MD5 重复，返回已有记录 id={}", existing.getId());
            FileObjectDTO dto = toDTO(existing);
            dto.setNewlyCreated(Boolean.FALSE);
            bindIfNecessary(bizType, bizId, dto);
            return dto;
        }

        LocalDateTime now = LocalDateTime.now();
        String objectKey = buildObjectKey(category, extension, now);
        try (InputStream input = file.getInputStream()) {
            // 第二遍重新打开输入流，OBS SDK 直接从流读取并上传。
            obsStorageClient.putObject(input, objectKey);
        } catch (Exception e) {
            throw new BizException(GovErrorCode.MINIO_ERROR.getCode(), "读取或上传文件失败: " + e.getMessage(), e);
        }

        FileObjectDTO dto = saveUploadedFile(filename, digest.size(), file.getContentType(), uploadedBy,
                digest.md5Hash(), objectKey, now);
        bindIfNecessary(bizType, bizId, dto);
        return dto;
    }

    private void bindIfNecessary(String bizType, String bizId, FileObjectDTO dto) {
        if (bizType != null && bizId != null && !bizType.isEmpty() && !bizId.isEmpty()) {
            bindFile(bizType, bizId, dto.getId(), null);
        }
    }

    /**
     * 字节直传到 OBS（导出/导入/公告等无 MultipartFile 场景）。
     *
     * @param bytes       文件内容
     * @param filename    原始文件名（取扩展名 + 展示）
     * @param contentType MIME 类型
     * @param uploadedBy  上传人工号
     * @param category    类型前缀（见 {@link FileCategory}）
     * @return 文件对象 DTO
     */
    @Transactional
    public FileObjectDTO upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category) {
        return storeBytes(bytes, filename, contentType, uploadedBy, category);
    }

    /** 上传核心：校验 → MD5 去重 → OBS put → 落库。 */
    private FileObjectDTO storeBytes(byte[] bytes, String filename, String contentType,
                                     String uploadedBy, String category) {
        // 1. 校验文件格式
        String extension = validateExtension(filename);

        // 2. 校验文件大小
        validateFileSize(bytes.length);

        // 3. 计算 MD5
        String md5Hash;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md5Hash = String.format("%032x", new BigInteger(1, md.digest(bytes)));
        } catch (Exception e) {
            throw new BizException(GovErrorCode.MINIO_ERROR.getCode(), "MD5 计算失败: " + e.getMessage(), e);
        }

        // 4. 去重：MD5 命中直接返回已有记录
        FileObject existing = fileObjectMapper.selectByMd5Hash(md5Hash);
        if (existing != null) {
            log.info("[FileService.storeBytes] MD5 重复，返回已有记录 id={}", existing.getId());
            FileObjectDTO dto = toDTO(existing);
            dto.setNewlyCreated(Boolean.FALSE);
            return dto;
        }

        // 5. 生成 OBS 对象 key：yyyy/MM/dd/{prefix}_{uuid}.ext（前缀便于在 OBS 区分类型）
        LocalDateTime now = LocalDateTime.now();
        String objectKey = buildObjectKey(category, extension, now);

        // 6. 写入 OBS
        obsStorageClient.putObject(bytes, objectKey);

        // 7. 落库；若落库失败，仅清理本次新生成的对象 key，绝不触碰 MD5 命中的共享对象。
        return saveUploadedFile(filename, bytes.length, contentType, uploadedBy, md5Hash, objectKey, now);
    }

    /**
     * 将文件元数据落库；插入失败时清理本次上传产生的孤儿 OBS 对象。
     */
    private FileObjectDTO saveUploadedFile(String filename, long fileSize, String contentType,
                                            String uploadedBy, String md5Hash, String objectKey,
                                            LocalDateTime now) {
        String id = "F_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        FileObject fileObject = new FileObject();
        fileObject.setId(id);
        fileObject.setFileName(filename);
        fileObject.setFileSize(fileSize);
        fileObject.setFileType(contentType);
        fileObject.setStoragePath(objectKey);
        fileObject.setBucketName("obs");
        fileObject.setMd5Hash(md5Hash);
        fileObject.setUploadedBy(uploadedBy);
        fileObject.setUploadedTime(now);
        try {
            fileObjectMapper.insert(fileObject);
        } catch (RuntimeException insertFailure) {
            cleanupUploadedObject(objectKey, insertFailure);
            throw insertFailure;
        }

        log.info("[FileService.saveUploadedFile] 文件上传成功 id={}, key={}", id, objectKey);
        FileObjectDTO dto = toDTO(fileObject);
        dto.setNewlyCreated(Boolean.TRUE);
        return dto;
    }

    private void cleanupUploadedObject(String objectKey, RuntimeException insertFailure) {
        try {
            obsStorageClient.deleteByKey(objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.error("[FileService] 文件元数据落库失败且清理 OBS 对象失败 key={}", objectKey, cleanupFailure);
            insertFailure.addSuppressed(cleanupFailure);
        }
    }

    private String buildObjectKey(String category, String extension, LocalDateTime now) {
        String prefix = (category == null || category.isBlank()) ? FileCategory.GENERAL : category;
        return now.format(PATH_DATE_FORMAT) + "/" + prefix + "_"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    private String validateExtension(String filename) {
        String extension = getFileExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(GovErrorCode.FILE_FORMAT_INVALID.getCode(),
                    GovErrorCode.FILE_FORMAT_INVALID.getMessage());
        }
        return extension;
    }

    private void validateFileSize(long size) {
        if (size > MAX_FILE_SIZE) {
            throw new BizException(GovErrorCode.FILE_SIZE_EXCEEDED.getCode(),
                    GovErrorCode.FILE_SIZE_EXCEEDED.getMessage());
        }
    }

    private UploadDigest calculateDigest(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            long size = 0;
            byte[] buffer = new byte[8192];
            try (InputStream input = file.getInputStream()) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    validateFileSize(size);
                    md.update(buffer, 0, read);
                }
            }
            return new UploadDigest(String.format("%032x", new BigInteger(1, md.digest())), size);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(GovErrorCode.MINIO_ERROR.getCode(), "读取上传文件或计算 MD5 失败: " + e.getMessage(), e);
        }
    }

    private record UploadDigest(String md5Hash, long size) {
    }

    /**
     * 获取文件下载 URL（OBS 预签名临时 URL）。
     *
     * @param fileId 文件对象ID
     * @return 预签名下载 URL
     * @throws BizException GOV-40005 文件不存在
     */
    public String getDownloadUrl(String fileId) {
        log.info("[FileService.getDownloadUrl] fileId={}", fileId);
        FileObject fileObject = fileObjectMapper.selectById(fileId);
        if (fileObject == null) {
            throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(),
                    GovErrorCode.FILE_NOT_FOUND.getMessage());
        }
        return obsStorageClient.generatePresignedUrl(fileObject.getStoragePath());
    }

    /**
     * 读取文件字节内容（供业务模块自行流式下载，替代原 getFilePath 本地读盘）。
     *
     * @param fileId 文件对象ID
     * @return 文件字节内容
     * @throws BizException GOV-40005 文件不存在
     */
    public byte[] getFileContent(String fileId) {
        FileObject fileObject = fileObjectMapper.selectById(fileId);
        if (fileObject == null) {
            throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(),
                    GovErrorCode.FILE_NOT_FOUND.getMessage());
        }
        return obsStorageClient.getBytes(fileObject.getStoragePath());
    }

    /**
     * 将文件内容流式写入调用方提供的输出流，不关闭调用方输出流。
     *
     * @param fileId       文件对象ID
     * @param outputStream 调用方输出流（由调用方负责关闭）
     * @throws BizException GOV-40005 文件不存在
     */
    public void writeFileContent(String fileId, OutputStream outputStream) {
        FileObject fileObject = fileObjectMapper.selectById(fileId);
        if (fileObject == null) {
            throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(),
                    GovErrorCode.FILE_NOT_FOUND.getMessage());
        }
        obsStorageClient.writeTo(fileObject.getStoragePath(), outputStream);
    }

    /**
     * 关联文件到业务对象（幂等）。
     */
    @Transactional
    public void bindFile(String bizType, String bizId, String fileObjectId, String fileRole) {
        log.info("[FileService.bindFile] bizType={}, bizId={}, fileObjectId={}, fileRole={}",
                bizType, bizId, fileObjectId, fileRole);

        FileObject fileObject = fileObjectMapper.selectById(fileObjectId);
        if (fileObject == null) {
            throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(),
                    GovErrorCode.FILE_NOT_FOUND.getMessage());
        }

        if (bizFileRelMapper.existsByBizTypeAndBizIdAndFileObjectId(bizType, bizId, fileObjectId)) {
            log.info("[FileService.bindFile] 关联已存在，跳过 bizType={}, bizId={}, fileObjectId={}",
                    bizType, bizId, fileObjectId);
            return;
        }

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
     * 查询业务关联的文件列表（按关联创建时间升序）。
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
     * 全局文件列表分页查询（管理后台用）。上传时间倒序。
     */
    public PageResult<FileObjectDTO> listAllFiles(String fileName, String fileType, String uploadedBy,
                                                   String startTime, String endTime,
                                                   int pageNo, int pageSize) {
        log.debug("[FileService.listAllFiles] fileName={}, fileType={}, uploadedBy={}, page={}/{}",
                fileName, fileType, uploadedBy, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;
        long total = fileObjectMapper.countByCondition(fileName, fileType, uploadedBy, startTime, endTime);
        List<FileObject> records = fileObjectMapper.selectByCondition(
                fileName, fileType, uploadedBy, startTime, endTime, offset, pageSize);
        List<FileObjectDTO> dtoList = new ArrayList<>(records.size());
        for (FileObject fo : records) {
            dtoList.add(toDTO(fo));
        }
        return PageResult.of(pageNo, pageSize, total, dtoList);
    }

    /** 获取文件名（供 Content-Disposition） */
    public String getFileName(String fileId) {
        FileObject fileObject = fileObjectMapper.selectById(fileId);
        return fileObject != null ? fileObject.getFileName() : "file";
    }

    /** 批量获取文件大小：id -> fileSize（用 MyBatis-Plus BaseMapper.selectBatchIds 一次查出）。 */
    public java.util.Map<String, Long> getFileSizes(java.util.List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<String, Long> sizeMap = new java.util.HashMap<>();
        for (FileObject fo : fileObjectMapper.selectBatchIds(fileIds)) {
            sizeMap.put(fo.getId(), fo.getFileSize());
        }
        return sizeMap;
    }

    /** 批量获取文件名：id -> fileName（一次查出，供列表展示附件名/tooltip）。 */
    public java.util.Map<String, String> getFileNames(java.util.List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<String, String> nameMap = new java.util.HashMap<>();
        for (FileObject fo : fileObjectMapper.selectBatchIds(fileIds)) {
            nameMap.put(fo.getId(), fo.getFileName());
        }
        return nameMap;
    }

    /**
     * 删除文件：删 OBS 对象 + 文件记录 + 业务关联。
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

        obsStorageClient.deleteByKey(fileObject.getStoragePath());
        fileObjectMapper.deleteById(fileId);
        bizFileRelMapper.deleteByFileObjectId(fileId);

        log.info("[FileService.deleteFile] 文件删除成功 fileId={}", fileId);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /** 提取文件扩展名（小写），无扩展名返回空串。 */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /** FileObject → FileObjectDTO。 */
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
