package com.bank.branch.platform.performance.service.importer;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 导入源文件「本地目录」存储（2026-06-25 恢复）.
 *
 * <p>替代 OBS 归档：把前端上传的导入文件直接保存到后端本地目录并就地解析，
 * 不依赖对象存储。仅 <strong>指标导入（METRIC_DEF）/ KPI 导入（KPI_SCHEME）/ 目标导入（TARGET_PLAN）</strong>
 * 三类配置型导入使用本地存储；其余导入（TARGET / BASE_DATA / ALLOC / METRIC_RESULT / KPI_SCORE）仍归档 OBS。
 * 路由分流逻辑见 {@code PerfImportServiceImpl}。
 *
 * <p>目录由 {@code perf.import.local-dir} 配置（默认 {@code file-storage/perf-import}，
 * 相对应用工作目录）。key 形如 {@code yyyyMMdd/<uuid>.<ext>}，落 {@code PERF_IMPORT_BATCH.source_object_key}，
 * 供「下载源文件」按 key 取回。
 */
@Slf4j
@Component
public class LocalImportFileStorage {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DEFAULT_EXT = ".xlsx";

    /** 存储根目录（绝对化 + 规范化，用于路径穿越校验）. */
    private final Path baseDir;

    public LocalImportFileStorage(
            @Value("${perf.import.local-dir:file-storage/perf-import}") String dir) {
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
        log.info("[LocalImportFileStorage] 导入源文件本地目录 = {}", this.baseDir);
    }

    /**
     * 保存导入源文件到本地目录.
     *
     * @param file 上传文件
     * @return 相对 key（{@code yyyyMMdd/<uuid>.<ext>}），存入 source_object_key
     */
    public String save(MultipartFile file) {
        String key = LocalDate.now().format(DAY) + "/" + uuid32() + ext(file.getOriginalFilename());
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, file.getBytes());
            return key;
        } catch (IOException e) {
            log.error("[LocalImportFileStorage] 保存导入文件失败 key={}", key, e);
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "导入文件本地保存失败: " + e.getMessage());
        }
    }

    /**
     * 按 key 读取本地源文件字节（供下载源文件）.
     *
     * @param key save 返回并落库的相对 key
     * @return 文件字节
     */
    public byte[] read(String key) {
        Path target = resolve(key);
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            // 文件缺失/不可读：按「无源文件」语义返回，避免泄露本地路径
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_NO_SOURCE_FILE, key);
        }
    }

    /** 解析 key 到 baseDir 下的绝对路径，并拦截路径穿越（../）. */
    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new PerfException(PerfErrorCode.IMPORT_BATCH_NO_SOURCE_FILE, "");
        }
        Path p = baseDir.resolve(key).normalize();
        if (!p.startsWith(baseDir)) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "非法文件路径: " + key);
        }
        return p;
    }

    /** 取原文件扩展名（含点），缺失则默认 .xlsx. */
    private static String ext(String name) {
        if (name == null) {
            return DEFAULT_EXT;
        }
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i) : DEFAULT_EXT;
    }

    private static String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
