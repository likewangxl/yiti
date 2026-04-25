package com.bank.branch.platform.it;

import com.bank.branch.platform.it.config.TestMockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Option A 启动级集成测试 —— REST 端点 vs PT_RESOURCE 对账。
 *
 * <p>枚举所有 RequestMappingHandlerMapping 注册的 /api/ 端点（含 HTTP 方法），
 * 与 {@code docs/schema/seed-v1.sql §3 PT_RESOURCE 接口资源种子数据} 章节做差集对账：</p>
 * <ul>
 *   <li>类型 A：代码注册的端点未在 PT_RESOURCE 出现（潜在权限漏配）</li>
 *   <li>类型 B：PT_RESOURCE 注册的资源没有对应代码端点（孤儿资源 / 已下线）</li>
 * </ul>
 *
 * <p>结果写入 {@code docs/superpowers/reports/2026-04-25-endpoint-resource-audit.md}。</p>
 *
 * <p>本测试不强制 0 差异，但设警戒线（A 类 ≤ 50 / B 类 ≤ 50），
 * 超过则 fail 并引导查看报告。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
class RestEndpointInventoryIT {

    /** 报告写入路径（相对 worktree 根） */
    private static final String REPORT_RELATIVE_PATH =
            "docs/superpowers/reports/2026-04-25-endpoint-resource-audit.md";

    /** seed-v1.sql 路径（相对 worktree 根） */
    private static final String SEED_RELATIVE_PATH = "docs/schema/seed-v1.sql";

    /** PT_RESOURCE INSERT 行解析正则（捕获 RESOURCE_ID / URL / METHOD / STATUS） */
    private static final Pattern PT_RESOURCE_INSERT = Pattern.compile(
            "INSERT IGNORE INTO PT_RESOURCE\\s*\\([^)]+\\)\\s*VALUES\\s*\\("
                    + "'([^']+)'"            // 1: RESOURCE_ID
                    + ",'([^']+)'"           // 2: RESOURCE_URL
                    + ",'([^']+)'"           // 3: RESOURCE_METHOD
                    + ",[^,]*"                // MENU_NAME
                    + ",[^,]*"                // MENU_ICON_URL
                    + ",[^,]*"                // MENU_RANK_NO
                    + ",[^,]*"                // ISMENU
                    + ",[^,]*"                // MENU_ENDFLAG
                    + ",[^,]*"                // PARENT_RESOURCE_ID
                    + ",(\\d+)"               // 4: STATUS
    );

    /** 警戒线：类型 A 端点未注册数最大值 */
    private static final int TYPE_A_THRESHOLD = 100;

    /** 警戒线：类型 B 资源孤儿数最大值 */
    private static final int TYPE_B_THRESHOLD = 100;

    @Autowired(required = false)
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("端点 vs 资源对账 - 生成报告 + 警戒线校验")
    void auditEndpointVsResource_shouldGenerateReport() throws IOException {
        assertThat(handlerMapping).as("RequestMappingHandlerMapping 必须存在").isNotNull();

        // 1) 收集代码端点
        List<EndpointEntry> endpoints = collectEndpoints(handlerMapping);
        Set<EndpointKey> endpointKeys = new LinkedHashSet<>();
        for (EndpointEntry ep : endpoints) {
            endpointKeys.add(new EndpointKey(normalize(ep.url), ep.method));
        }

        // 2) 解析 seed-v1.sql PT_RESOURCE
        Path worktreeRoot = resolveWorktreeRoot();
        Path seedPath = worktreeRoot.resolve(SEED_RELATIVE_PATH);
        assertThat(Files.exists(seedPath))
                .as("seed-v1.sql 必须存在，路径：%s", seedPath)
                .isTrue();

        List<ResourceEntry> resources = parsePtResource(seedPath);
        Set<EndpointKey> resourceKeys = new LinkedHashSet<>();
        for (ResourceEntry r : resources) {
            // STATUS=0 启用，STATUS=1 禁用 —— 报告里都参与对账，但分类时仅启用项算资源
            if (r.status == 0) {
                resourceKeys.add(new EndpointKey(normalize(r.url), r.method));
            }
        }

        // 3) 计算差集
        Set<EndpointKey> typeA = new TreeSet<>();   // 端点未注册
        Set<EndpointKey> typeB = new TreeSet<>();   // 资源孤儿

        for (EndpointKey k : endpointKeys) {
            if (!resourceKeys.contains(k)) {
                typeA.add(k);
            }
        }
        for (EndpointKey k : resourceKeys) {
            if (!endpointKeys.contains(k)) {
                typeB.add(k);
            }
        }

        // 4) 写报告
        Path reportPath = worktreeRoot.resolve(REPORT_RELATIVE_PATH);
        String report = renderReport(endpoints, resources, typeA, typeB);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);

        // 5) 断言：报告写入成功 + 类型 A/B 在警戒线内
        assertThat(Files.exists(reportPath))
                .as("audit 报告文件应成功写入：%s", reportPath)
                .isTrue();
        assertThat(typeA.size())
                .as("类型 A 端点未注册到 PT_RESOURCE 数 = %s（警戒线 %s），详见 %s",
                        typeA.size(), TYPE_A_THRESHOLD, REPORT_RELATIVE_PATH)
                .isLessThanOrEqualTo(TYPE_A_THRESHOLD);
        assertThat(typeB.size())
                .as("类型 B PT_RESOURCE 孤儿资源数 = %s（警戒线 %s），详见 %s",
                        typeB.size(), TYPE_B_THRESHOLD, REPORT_RELATIVE_PATH)
                .isLessThanOrEqualTo(TYPE_B_THRESHOLD);
    }

    // ====================================================================
    // 数据收集
    // ====================================================================

    /**
     * 从 handlerMapping 拿出所有 /api/ 端点（每个 url + method 组合一条）。
     */
    private static List<EndpointEntry> collectEndpoints(RequestMappingHandlerMapping mapping) {
        List<EndpointEntry> entries = new ArrayList<>();
        Map<RequestMappingInfo, HandlerMethod> handlers = mapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlers.entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();

            Set<String> patterns = extractPatterns(info);
            Set<String> httpMethods = extractMethods(info);

            for (String pattern : patterns) {
                if (pattern == null || !pattern.startsWith("/api/")) {
                    continue;
                }
                if (httpMethods.isEmpty()) {
                    // 无显式 HTTP method 的端点（罕见）—— 用 ANY 占位
                    entries.add(new EndpointEntry(pattern, "ANY",
                            hm.getBeanType().getName()));
                } else {
                    for (String m : httpMethods) {
                        entries.add(new EndpointEntry(pattern, m, hm.getBeanType().getName()));
                    }
                }
            }
        }
        entries.sort((a, b) -> {
            int c = a.url.compareTo(b.url);
            return c != 0 ? c : a.method.compareTo(b.method);
        });
        return entries;
    }

    private static Set<String> extractPatterns(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }

    private static Set<String> extractMethods(RequestMappingInfo info) {
        RequestMethodsRequestCondition cond = info.getMethodsCondition();
        if (cond == null || cond.getMethods().isEmpty()) {
            return Set.of();
        }
        Set<String> methods = new LinkedHashSet<>();
        cond.getMethods().forEach(m -> methods.add(m.name()));
        return methods;
    }

    /**
     * 解析 seed-v1.sql 提取所有 INSERT IGNORE INTO PT_RESOURCE 行。
     */
    private static List<ResourceEntry> parsePtResource(Path seedPath) throws IOException {
        String content = Files.readString(seedPath, StandardCharsets.UTF_8);
        Matcher m = PT_RESOURCE_INSERT.matcher(content);
        List<ResourceEntry> list = new ArrayList<>();
        while (m.find()) {
            String resourceId = m.group(1);
            String url = m.group(2);
            String method = m.group(3);
            int status = Integer.parseInt(m.group(4));
            list.add(new ResourceEntry(resourceId, url, method, status));
        }
        return list;
    }

    // ====================================================================
    // URL 归一化：处理 Spring {var} ↔ AntPath '*' 差异
    //
    // 例如：
    //   Spring 写法：/api/loans/{id}            -> /api/loans/{X}
    //   AntPath 写法：/api/loans/*              -> /api/loans/{X}
    //   带正则：/api/users/{id:\\d+}            -> /api/users/{X}
    //   多段：/api/orgs/{orgCode}/users         -> /api/orgs/{X}/users
    // ====================================================================
    static String normalize(String urlPattern) {
        if (urlPattern == null) {
            return null;
        }
        String s = urlPattern;
        // 处理 Spring 嵌套 {var:regex} 形式（如 /{id:[A-Za-z0-9_-]{1,64}}）：
        // 循环替换最内层 {...}（不含嵌套大括号）为 __PV__，直到无变化。
        // 然后再次替换最外层 {...__PV__...} 为 __PV__。最后统一替换为 {X}。
        String prev;
        do {
            prev = s;
            s = s.replaceAll("\\{[^{}]*\\}", "__PV__");
        } while (!s.equals(prev));
        s = s.replace("__PV__", "{X}");
        // 末尾 ** -> {X}
        s = s.replaceAll("\\*\\*", "{X}");
        // 单 * -> {X}
        s = s.replaceAll("\\*", "{X}");
        // 去重 /{X}/{X} 仍保留（每段都是独立变量），不合并
        return s;
    }

    // ====================================================================
    // 工作目录探测
    //
    // 测试运行时 CWD 通常是 bootstrap/ 目录，需要 ../ 回到 worktree 根。
    // 为兼容 IDE 在 worktree 根 / 在 bootstrap 子目录两种 CWD，逐级向上找
    // 第一个含 docs/schema/seed-v1.sql 的目录。
    // ====================================================================
    static Path resolveWorktreeRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd;
        for (int i = 0; i < 5; i++) {
            if (Files.exists(candidate.resolve(SEED_RELATIVE_PATH))) {
                return candidate;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        // 兜底：返回 cwd（让后续 assertThat exists 给出清晰错误）
        return cwd;
    }

    // ====================================================================
    // 报告渲染
    // ====================================================================

    private static String renderReport(List<EndpointEntry> endpoints,
                                        List<ResourceEntry> resources,
                                        Set<EndpointKey> typeA,
                                        Set<EndpointKey> typeB) {
        StringBuilder sb = new StringBuilder();
        sb.append("# REST 端点 vs PT_RESOURCE 对账报告\n\n");
        sb.append("> 由 `RestEndpointInventoryIT.auditEndpointVsResource_shouldGenerateReport` ")
                .append("自动生成，请勿手工编辑。\n\n");
        sb.append("**生成时间**：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n\n");
        sb.append("## 概览\n\n");
        sb.append("| 指标 | 数量 |\n");
        sb.append("|---|---|\n");
        sb.append("| 代码注册 /api/ 端点（去重后 url+method）| ")
                .append(distinctEndpointKeys(endpoints).size()).append(" |\n");
        sb.append("| seed-v1.sql PT_RESOURCE 总数（包含 STATUS=1 禁用）| ")
                .append(resources.size()).append(" |\n");
        sb.append("| seed-v1.sql PT_RESOURCE 启用数（STATUS=0）| ")
                .append(resources.stream().filter(r -> r.status == 0).count()).append(" |\n");
        sb.append("| 类型 A 端点未注册到 PT_RESOURCE | ")
                .append(typeA.size()).append(" |\n");
        sb.append("| 类型 B PT_RESOURCE 孤儿（启用但代码无端点）| ")
                .append(typeB.size()).append(" |\n");
        sb.append("\n");

        // §1 类型 A
        sb.append("## §1 类型 A：代码端点未注册到 PT_RESOURCE（共 ")
                .append(typeA.size()).append(" 项）\n\n");
        if (typeA.isEmpty()) {
            sb.append("- 无\n\n");
        } else {
            sb.append("| URL（归一化）| METHOD | Controller |\n");
            sb.append("|---|---|---|\n");
            for (EndpointKey k : typeA) {
                String controller = endpoints.stream()
                        .filter(e -> normalize(e.url).equals(k.url) && e.method.equals(k.method))
                        .map(e -> e.controllerClass)
                        .findFirst().orElse("-");
                sb.append("| `").append(k.url).append("` | ").append(k.method)
                        .append(" | `").append(simpleClassName(controller)).append("` |\n");
            }
            sb.append("\n");
        }

        // §2 类型 B
        sb.append("## §2 类型 B：PT_RESOURCE 孤儿（启用但代码无对应端点，共 ")
                .append(typeB.size()).append(" 项）\n\n");
        if (typeB.isEmpty()) {
            sb.append("- 无\n\n");
        } else {
            sb.append("| URL（归一化）| METHOD | RESOURCE_ID |\n");
            sb.append("|---|---|---|\n");
            for (EndpointKey k : typeB) {
                String resId = resources.stream()
                        .filter(r -> r.status == 0
                                && normalize(r.url).equals(k.url)
                                && r.method.equals(k.method))
                        .map(r -> r.resourceId)
                        .findFirst().orElse("-");
                sb.append("| `").append(k.url).append("` | ").append(k.method)
                        .append(" | `").append(resId).append("` |\n");
            }
            sb.append("\n");
        }

        // §3 完整端点清单（折叠）
        sb.append("## §3 完整代码端点清单（折叠）\n\n");
        sb.append("<details>\n<summary>展开查看 ")
                .append(endpoints.size()).append(" 条端点</summary>\n\n");
        sb.append("| URL | METHOD | URL（归一化）| Controller |\n");
        sb.append("|---|---|---|---|\n");
        for (EndpointEntry e : endpoints) {
            sb.append("| `").append(e.url).append("` | ").append(e.method)
                    .append(" | `").append(normalize(e.url)).append("` | `")
                    .append(simpleClassName(e.controllerClass)).append("` |\n");
        }
        sb.append("\n</details>\n\n");

        // §4 完整资源清单（折叠）
        sb.append("## §4 完整 PT_RESOURCE 清单（折叠）\n\n");
        sb.append("<details>\n<summary>展开查看 ")
                .append(resources.size()).append(" 条资源</summary>\n\n");
        sb.append("| RESOURCE_ID | URL | METHOD | URL（归一化）| STATUS |\n");
        sb.append("|---|---|---|---|---|\n");
        for (ResourceEntry r : resources) {
            sb.append("| `").append(r.resourceId).append("` | `")
                    .append(r.url).append("` | ").append(r.method)
                    .append(" | `").append(normalize(r.url)).append("` | ")
                    .append(r.status).append(" |\n");
        }
        sb.append("\n</details>\n");

        return sb.toString();
    }

    private static Set<EndpointKey> distinctEndpointKeys(List<EndpointEntry> endpoints) {
        Set<EndpointKey> set = new LinkedHashSet<>();
        for (EndpointEntry e : endpoints) {
            set.add(new EndpointKey(normalize(e.url), e.method));
        }
        return set;
    }

    private static String simpleClassName(String fqcn) {
        if (fqcn == null) {
            return "-";
        }
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 ? fqcn.substring(idx + 1) : fqcn;
    }

    // ====================================================================
    // POJO
    // ====================================================================

    private static class EndpointEntry {
        final String url;
        final String method;
        final String controllerClass;

        EndpointEntry(String url, String method, String controllerClass) {
            this.url = url;
            this.method = method;
            this.controllerClass = controllerClass;
        }
    }

    private static class ResourceEntry {
        final String resourceId;
        final String url;
        final String method;
        final int status;

        ResourceEntry(String resourceId, String url, String method, int status) {
            this.resourceId = resourceId;
            this.url = url;
            this.method = method;
            this.status = status;
        }
    }

    /** 对账主键：归一化 url + method（大写）。 */
    private record EndpointKey(String url, String method) implements Comparable<EndpointKey> {
        @Override
        public int compareTo(EndpointKey o) {
            int c = url.compareTo(o.url);
            return c != 0 ? c : method.compareTo(o.method);
        }
    }
}
