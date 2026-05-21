package com.bank.branch.platform.common.web.lock;

import java.util.UUID;

/**
 * 分布式锁持锁者标识生成器：进程内 instanceId（启动时初始化一次）+ 当前线程 ID。
 * <p>多实例多线程下能区分；进程重启会换新 instanceId，旧锁自然失效。</p>
 */
public final class HolderUtil {
    private static final String INSTANCE_ID = UUID.randomUUID().toString().substring(0, 8);

    private HolderUtil() {}

    /** 当前持锁者标识：{@code <instanceId>#<threadId>} */
    public static String current() {
        return INSTANCE_ID + "#" + Thread.currentThread().getId();
    }
}
