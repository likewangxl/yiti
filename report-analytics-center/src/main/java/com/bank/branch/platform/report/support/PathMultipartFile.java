package com.bank.branch.platform.report.support;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地临时文件到 {@link MultipartFile} 的适配器。
 *
 * <p>导出成品先落在受控临时目录，再交由治理中心上传；{@link #getInputStream()}、
 * {@link #transferTo(java.io.File)} 和 {@link #transferTo(Path)} 均按文件流处理，避免
 * report 侧为了调用 FileApi 重新复制完整成品到 byte[]。</p>
 */
public final class PathMultipartFile implements MultipartFile {

    private final Path path;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    public PathMultipartFile(Path path, String name, String originalFilename, String contentType) {
        this.path = path;
        this.name = name == null ? "file" : name;
        this.originalFilename = originalFilename == null ? "file" : originalFilename;
        this.contentType = contentType == null ? "application/octet-stream" : contentType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException("读取临时导出文件大小失败: " + path, e);
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        Files.copy(path, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void transferTo(Path dest) throws IOException, IllegalStateException {
        Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
