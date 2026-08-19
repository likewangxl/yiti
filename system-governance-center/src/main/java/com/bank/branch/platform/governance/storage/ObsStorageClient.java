package com.bank.branch.platform.governance.storage;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObsObject;
import com.obs.services.model.TemporarySignatureRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 华为云 OBS 读写工具类（仿参考 PdObsClient 的调用习惯）。
 * <p>懒连接：{@link #init()} 仅构造客户端对象，不发起网络请求；真正连接发生在首次 put/get 调用时，
 * 因此 dev/CI 无真实 OBS 也能正常启动，单测通过 mock {@link ObsClient} 验证委托。</p>
 *
 * <p>{@code obs.enabled=false} 时仍保留本 Bean，避免依赖具体类型的 {@code FileService} 无法装配；
 * 但不会构造 {@link ObsClient}，所有对象操作都会明确拒绝，避免测试环境误触远端存储。</p>
 */
@Slf4j
@Component
public class ObsStorageClient {

    @Value("${obs.enabled:true}")
    private boolean enabled = true;

    @Value("${obs.endPoint}")
    private String endPoint;

    @Value("${obs.accessKey}")
    private String accessKey;

    @Value("${obs.secretKey}")
    private String secretKey;

    @Value("${obs.bucketName}")
    private String bucketName;

    @Value("${obs.presignExpireSeconds:600}")
    private long presignExpireSeconds;

    private ObsClient obsClient;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("[ObsStorageClient] obs.enabled=false，跳过 OBS 客户端初始化");
            return;
        }
        this.obsClient = new ObsClient(accessKey, secretKey, endPoint);
        log.info("[ObsStorageClient] 初始化完成 endPoint={}, bucket={}", endPoint, bucketName);
    }

    @PreDestroy
    public void close() {
        if (obsClient != null) {
            try {
                obsClient.close();
            } catch (Exception e) {
                log.warn("[ObsStorageClient] 关闭失败", e);
            }
        }
    }

    /** 写：字节数组 → OBS 对象。 */
    public void putObject(byte[] bytes, String key) {
        ObsClient client = requireClient("上传对象");
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(bucketName, key, in);
        } catch (Exception e) {
            log.error("[ObsStorageClient] putObject 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 上传失败: " + e.getMessage(), e);
        }
    }

    /** 写：输入流 → OBS 对象；该方法负责关闭输入流。 */
    public void putObject(InputStream input, String key) {
        ObsClient client = requireClient("上传对象");
        try (InputStream in = input) {
            client.putObject(bucketName, key, in);
        } catch (Exception e) {
            log.error("[ObsStorageClient] putObject(stream) 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 上传失败: " + e.getMessage(), e);
        }
    }

    /** 读：OBS 对象 → 字节数组。 */
    public byte[] getBytes(String key) {
        ObsClient client = requireClient("读取对象");
        try {
            ObsObject obj = client.getObject(bucketName, key);
            try (InputStream in = obj.getObjectContent()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.error("[ObsStorageClient] getBytes 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读：OBS 对象 → 调用方输出流；只关闭 OBS 返回的输入流，不关闭调用方输出流。
     */
    public void writeTo(String key, OutputStream outputStream) {
        ObsClient client = requireClient("读取对象");
        try {
            ObsObject object = client.getObject(bucketName, key);
            try (InputStream input = object.getObjectContent()) {
                input.transferTo(outputStream);
            }
        } catch (Exception e) {
            log.error("[ObsStorageClient] writeTo 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 读取失败: " + e.getMessage(), e);
        }
    }

    /** 删：OBS 对象。 */
    public void deleteByKey(String key) {
        ObsClient client = requireClient("删除对象");
        try {
            client.deleteObject(bucketName, key);
        } catch (Exception e) {
            log.error("[ObsStorageClient] deleteByKey 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 删除失败: " + e.getMessage(), e);
        }
    }

    /** 预签名临时下载 URL（GET）。 */
    public String generatePresignedUrl(String key) {
        ObsClient client = requireClient("生成预签名下载链接");
        try {
            TemporarySignatureRequest req = new TemporarySignatureRequest(HttpMethodEnum.GET, presignExpireSeconds);
            req.setBucketName(bucketName);
            req.setObjectKey(key);
            return client.createTemporarySignature(req).getSignedUrl();
        } catch (Exception e) {
            log.error("[ObsStorageClient] generatePresignedUrl 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 生成预签名下载链接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取可用客户端；禁用或生命周期未完成时均显式拒绝，避免误调用退化为 NPE。
     *
     * @param operation 当前请求的 OBS 操作
     * @return 已初始化的 OBS 客户端
     */
    private ObsClient requireClient(String operation) {
        if (!enabled) {
            throw new BizException(GovErrorCode.OBS_DISABLED.getCode(),
                    "OBS 已通过 obs.enabled=false 禁用，拒绝执行" + operation);
        }
        if (obsClient == null) {
            throw new BizException("GOV-50001", "OBS 客户端尚未初始化，拒绝执行" + operation);
        }
        return obsClient;
    }
}
