package com.bank.branch.platform.common.security.meta;

import com.bank.branch.platform.common.web.exception.BizException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务对象元数据注册中心
 * 管理所有业务对象的元数据，支持注册和查询操作
 * 使用 ConcurrentHashMap 保证线程安全
 */
public class ObjectMetaRegistry {
    private final ConcurrentHashMap<String, ObjectMeta> registry = new ConcurrentHashMap<>();

    /**
     * 注册业务对象元数据
     *
     * @param meta 业务对象元数据
     * @throws IllegalStateException 当对象标识已被注册时抛出
     */
    public void register(ObjectMeta meta) {
        if (registry.putIfAbsent(meta.objectKey(), meta) != null) {
            throw new IllegalStateException("ObjectMeta already registered: " + meta.objectKey());
        }
    }

    /**
     * 根据对象标识获取元数据（可选）
     *
     * @param objectKey 业务对象标识
     * @return 包含元数据的 Optional，未找到则返回空
     */
    public Optional<ObjectMeta> get(String objectKey) {
        return Optional.ofNullable(registry.get(objectKey));
    }

    /**
     * 根据对象标识获取元数据（必须存在）
     *
     * @param objectKey 业务对象标识
     * @return 业务对象元数据
     * @throws BizException 当对象标识未注册时抛出
     */
    public ObjectMeta getRequired(String objectKey) {
        return get(objectKey).orElseThrow(() ->
            new BizException("META_001", "未注册的业务对象: " + objectKey));
    }
}
