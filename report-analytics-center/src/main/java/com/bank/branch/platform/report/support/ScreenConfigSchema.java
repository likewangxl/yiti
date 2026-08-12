package com.bank.branch.platform.report.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 大屏数据源 config_json schemaVersion / scopeMode 读时兼容适配器（spec 2026-07-17 §3.4/§4）.
 *
 * <p>历史数据（2026-07-17 之前落库）根节点没有 schemaVersion，读取时统一视为版本 1；
 * 没有 scopeMode 视为 SUBJECT（主体口径，DATA_SCOPE 按主体参数约束校验）。
 * 适配集中在本类唯一入口（引擎 readConfig、数据源服务 readJson 与 ScreenDataScopeGuard 共用），
 * 不写迁移 SQL、不在各处散落默认值判断。
 */
public final class ScreenConfigSchema {

    /** 无 schemaVersion 的历史配置默认版本 */
    public static final int DEFAULT_SCHEMA_VERSION = 1;

    /** 无 scopeMode 的历史配置默认口径（SUBJECT=主体参数约束；GLOBAL=全省口径需 ALL/省级 ORG_SUBTREE） */
    public static final String DEFAULT_SCOPE_MODE = "SUBJECT";

    private ScreenConfigSchema() {
    }

    /**
     * 补默认值：根节点为对象时缺 schemaVersion 补 1、缺 scopeMode 补 SUBJECT，其余原样返回.
     *
     * @param root 解析后的 config_json 根节点（可为任意 JsonNode，非对象节点不处理）
     * @return 适配后的根节点（与入参同一实例）
     */
    public static JsonNode withDefaults(JsonNode root) {
        if (root instanceof ObjectNode obj) {
            if (!obj.hasNonNull("schemaVersion")) {
                obj.put("schemaVersion", DEFAULT_SCHEMA_VERSION);
            } else {
                JsonNode version = obj.path("schemaVersion");
                if (!version.canConvertToInt() || (version.asInt() != 1 && version.asInt() != 2)) {
                    // 仅“字段缺失”属于历史 v1；显式未知版本必须 fail-close，不能悄然按 v1 执行。
                    throw new IllegalArgumentException("unsupported screen datasource schemaVersion");
                }
            }
            if (!obj.hasNonNull("scopeMode")) {
                obj.put("scopeMode", DEFAULT_SCOPE_MODE);
            }
        }
        return root;
    }
}
