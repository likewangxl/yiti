package com.bank.branch.platform.report.support;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * byte[] → MultipartFile 适配器（M6.0 内部工具）.
 *
 * <p>背景：governance.FileApi.upload 仅接收 {@link MultipartFile}（HTTP 场景设计），
 * 而 4 个 ExportStrategy 在内存中生成 EasyExcel 字节流后需要走该 API。
 * 该适配器把内部 byte[] + 文件名 + contentType 包装为一个最小可用的 MultipartFile，
 * 不真正经过 HTTP multipart 解析层。
 *
 * <p>不可变：构造时锁定字节数组与元信息，{@code transferTo} 之外的方法只读。
 */
public final class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    public ByteArrayMultipartFile(byte[] content, String name, String originalFilename,
                                  String contentType) {
        this.content = content == null ? new byte[0] : content;
        this.name = name == null ? "file" : name;
        this.originalFilename = originalFilename == null ? "file" : originalFilename;
        this.contentType = contentType == null
            ? "application/octet-stream" : contentType;
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
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        Files.write(dest.toPath(), content);
    }

    @Override
    public void transferTo(Path dest) throws IOException, IllegalStateException {
        Files.write(dest, content);
    }

    /**
     * Spring 5+ 默认 {@code transferTo(File)} 调 inputStream + copy，
     * 这里直接 {@code Files.write} 避免额外流复制开销。
     *
     * <p>显式 transferTo(OutputStream) 兼容（部分场景 governance 内部可能拿 stream 写出）.
     */
    public void writeTo(OutputStream out) throws IOException {
        out.write(content);
    }
}
