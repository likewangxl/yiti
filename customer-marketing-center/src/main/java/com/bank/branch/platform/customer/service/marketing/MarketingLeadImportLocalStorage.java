package com.bank.branch.platform.customer.service.marketing;

import com.bank.branch.platform.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 线索批量导入文件的本地暂存。
 *
 * <p>仅用于线索导入原文件和失败明细，不改变公告、附件等通用文件的 OBS 存储策略。
 * 对外存储键以 {@code local:} 开头，便于与历史 {@code FileApi} 文件 ID 区分。</p>
 */
@Slf4j
@Component
public class MarketingLeadImportLocalStorage {

    private static final String LOCAL_PREFIX = "local:";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> SOURCE_EXTENSIONS = Set.of("csv", "xlsx", "xls");
    private static final int BUFFER_SIZE = 8192;

    private final Path baseDir;

    public MarketingLeadImportLocalStorage(
            @Value("${marketing.lead-import.local-dir:file-storage/marketing-lead-import}") String directory) {
        this.baseDir = Paths.get(directory).toAbsolutePath().normalize();
        log.info("[MarketingLeadImportLocalStorage] 线索导入文件本地目录={}", baseDir);
    }

    /** 保存用户上传的导入源文件，并返回可落库的本地键和 MD5。 */
    public StoredFile saveSource(MultipartFile file) {
        String extension = sourceExtension(file == null ? null : file.getOriginalFilename());
        String relativeKey = datedKey("source", extension);
        Path target = resolveRelative(relativeKey);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }
            moveIntoPlace(temporary, target);
            return new StoredFile(LOCAL_PREFIX + relativeKey,
                    String.format("%032x", new BigInteger(1, digest.digest())));
        } catch (BizException exception) {
            deleteTemporary(temporary);
            throw exception;
        } catch (Exception exception) {
            deleteTemporary(temporary);
            log.error("[MarketingLeadImportLocalStorage] 保存线索导入源文件失败", exception);
            throw new BizException("MARKETING_LEAD_IMPORT_LOCAL_SAVE_FAILED",
                    "导入文件保存到本地失败，请稍后重试", exception);
        }
    }

    /** 保存服务端生成的失败明细 CSV，并返回可落库的本地键。 */
    public String saveErrorCsv(byte[] bytes) {
        String relativeKey = datedKey("error", "csv");
        Path target = resolveRelative(relativeKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            return LOCAL_PREFIX + relativeKey;
        } catch (IOException exception) {
            log.error("[MarketingLeadImportLocalStorage] 保存线索导入失败明细失败", exception);
            throw new BizException("MARKETING_LEAD_IMPORT_LOCAL_SAVE_FAILED",
                    "导入失败明细保存到本地失败，请稍后重试", exception);
        }
    }

    /** 读取本地键对应的文件内容。 */
    public byte[] read(String storageKey) {
        Path target = resolveStorageKey(storageKey);
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            log.warn("[MarketingLeadImportLocalStorage] 本地导入文件不存在或不可读 key={}", storageKey);
            throw new BizException("MARKETING_LEAD_IMPORT_FILE_NOT_FOUND", "本地导入文件不存在或已被清理");
        }
    }

    public boolean supports(String storageKey) {
        return storageKey != null && storageKey.startsWith(LOCAL_PREFIX);
    }

    private String datedKey(String kind, String extension) {
        return kind + "/" + LocalDate.now().format(DAY) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    private String sourceExtension(String filename) {
        if (filename == null) {
            throw new BizException("MARKETING_LEAD_IMPORT_FORMAT_INVALID", "仅支持CSV、XLSX或XLS文件");
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!SOURCE_EXTENSIONS.contains(extension)) {
            throw new BizException("MARKETING_LEAD_IMPORT_FORMAT_INVALID", "仅支持CSV、XLSX或XLS文件");
        }
        return extension;
    }

    private Path resolveStorageKey(String storageKey) {
        if (!supports(storageKey)) {
            throw new BizException("MARKETING_LEAD_IMPORT_LOCAL_PATH_INVALID", "导入文件本地路径不合法");
        }
        return resolveRelative(storageKey.substring(LOCAL_PREFIX.length()));
    }

    private Path resolveRelative(String relativeKey) {
        Path resolved = baseDir.resolve(relativeKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BizException("MARKETING_LEAD_IMPORT_LOCAL_PATH_INVALID", "导入文件本地路径不合法");
        }
        return resolved;
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            log.warn("[MarketingLeadImportLocalStorage] 清理上传临时文件失败", cleanupFailure);
        }
    }

    public record StoredFile(String key, String md5Hash) {
    }
}
