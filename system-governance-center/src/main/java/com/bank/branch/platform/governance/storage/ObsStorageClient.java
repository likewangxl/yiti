package com.bank.branch.platform.governance.storage;

import com.bank.branch.platform.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件存储客户端。
 *
 * <p>⚠️ 临时本地实现：本地无华为云 OBS，暂改为写本地文件系统（基目录 {@code file-storage}），
 * 方法签名与原 OBS 版本保持一致，便于联调验证后整体切回 OBS。
 * 切回 OBS 时还原本类即可（git 历史保留原实现）。</p>
 */
@Slf4j
@Component
public class ObsStorageClient {

    /** 本地存储基目录（相对运行目录，与既有 file-storage/yyyy/MM/dd 结构一致） */
    private static final Path BASE_DIR = Paths.get("file-storage");

    /** 把对象 key 解析为本地路径，并防止路径穿越。 */
    private Path resolve(String key) {
        Path base = BASE_DIR.normalize().toAbsolutePath();
        Path p = base.resolve(key).normalize();
        if (!p.startsWith(base)) {
            throw new BizException("GOV-50001", "非法存储路径: " + key);
        }
        return p;
    }

    /** 写：字节数组 → 本地文件。 */
    public void putObject(byte[] bytes, String key) {
        try {
            Path p = resolve(key);
            Files.createDirectories(p.getParent());
            Files.write(p, bytes);
            log.info("[ObsStorageClient(local)] putObject key={} -> {}", key, p);
        } catch (IOException e) {
            log.error("[ObsStorageClient(local)] putObject 失败 key={}", key, e);
            throw new BizException("GOV-50001", "本地存储写入失败: " + e.getMessage(), e);
        }
    }

    /** 读：本地文件 → 字节数组。 */
    public byte[] getBytes(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            log.error("[ObsStorageClient(local)] getBytes 失败 key={}", key, e);
            throw new BizException("GOV-50001", "本地存储读取失败: " + e.getMessage(), e);
        }
    }

    /** 删：本地文件。 */
    public void deleteByKey(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.error("[ObsStorageClient(local)] deleteByKey 失败 key={}", key, e);
            throw new BizException("GOV-50001", "本地存储删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 预签名临时下载 URL —— 本地实现下不适用。
     * <p>本地方案改由 {@code FileService.getDownloadUrl} 返回应用内流式下载端点
     * {@code /api/files/{fileId}/download}，本方法保留仅为签名兼容。</p>
     */
    public String generatePresignedUrl(String key) {
        throw new UnsupportedOperationException("本地存储无预签名 URL，请走 /api/files/{fileId}/download");
    }
}
