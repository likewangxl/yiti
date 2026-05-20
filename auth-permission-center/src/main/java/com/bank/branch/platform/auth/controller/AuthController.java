package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.CheckPermissionReqDTO;
import com.bank.branch.platform.auth.api.dto.CheckPermissionRespDTO;
import com.bank.branch.platform.auth.api.dto.CurrentUserRespDTO;
import com.bank.branch.platform.auth.api.dto.LoginReqDTO;
import com.bank.branch.platform.auth.api.dto.LoginRespDTO;
import com.bank.branch.platform.auth.api.dto.PermissionSetRespDTO;
import com.bank.branch.platform.auth.api.dto.RoleSimpleDTO;
import com.bank.branch.platform.auth.api.dto.UniAuthLoginReqDTO;
import com.bank.branch.platform.auth.uniauth.UniAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.bank.branch.platform.auth.entity.PtRole;
import com.bank.branch.platform.auth.mapper.UserRoleMapper;
import com.bank.branch.platform.auth.service.AuthService;
import com.bank.branch.platform.auth.service.BizScopeService;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 负责用户登录、登出、当前用户信息查询、权限查询与校验接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "认证接口", description = "登录、登出、当前用户信息查询、权限查询与校验")
public class AuthController {

    private final AuthService authService;
    private final UserRoleMapper userRoleMapper;
    private final BizScopeService bizScopeService;
    private final UniAuthProperties uniauthProps;

    /**
     * 用户登录
     *
     * @param req     登录请求DTO（包含用户名和密码）
     * @param session HttpSession，用于存储登录态
     * @return 登录响应（含 token、角色等信息）
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "校验用户名和密码，登录成功后返回 Session Token")
    public ResponseWrapper<LoginRespDTO> login(@Valid @RequestBody LoginReqDTO req, HttpSession session) {
        log.info("[AuthController.login] 用户登录请求 username={}", req.getUsername());
        LoginRespDTO resp = authService.login(req.getUsername(), req.getPassword(), session);
        return ResponseWrapper.success(resp);
    }

    /**
     * 统一认证登录（开发期 mock）
     *
     * @param req     统一认证登录请求（仅 userDomainName，无密码）
     * @param session HttpSession
     * @return 登录响应
     */
    @PostMapping("/uniauth/login")
    @Operation(summary = "统一认证登录（开发期 mock / 编程式入口）",
            description = "传入工号 userDomainName，跳过密码校验，调边车 11003 走 S120030044 查授权后建 session；正式生产请走 /uniauth/redirect 重定向链路")
    public ResponseWrapper<LoginRespDTO> uniAuthLogin(@Valid @RequestBody UniAuthLoginReqDTO req, HttpSession session) {
        log.info("[AuthController.uniAuthLogin] 统一认证登录请求 userDomainName={}", req.getUserDomainName());
        LoginRespDTO resp = authService.loginByUniAuth(req.getUserDomainName(), session);
        return ResponseWrapper.success(resp);
    }

    /**
     * 统一认证：302 跳到 UIAS 单点登录页（前端按钮触发）。
     * <p>对应 xanpd 的 LoginController.redirect()。
     */
    @GetMapping("/uniauth/redirect")
    @Operation(summary = "跳转到 UIAS 单点登录页",
            description = "前端『统一认证登录』按钮直接打到此端点，后端 302 重定向到行内 UIAS 登录页，并把 yiti 回调 URL 带在 ssotarget 参数上")
    public void uniAuthRedirect(HttpServletResponse resp) throws IOException {
        String target = uniauthProps.getRedirectUrl()
                + (uniauthProps.getRedirectUrl().contains("?") ? "&" : "?")
                + uniauthProps.getSsoTargetParamName()
                + "="
                + URLEncoder.encode(uniauthProps.getCallbackUrl(), StandardCharsets.UTF_8);
        log.info("[AuthController.uniAuthRedirect] 302 → {}", target);
        resp.sendRedirect(target);
    }

    /**
     * 统一认证回调：UIAS 完认证后浏览器被 302 跳到这里。
     * <p>对应 xanpd 的 LoginController.redirectSuccess()。
     * <p>用户身份来源：默认从 query 参数取（参数名见 {@code platform.sidecar.uniauth.user-param-name}）；
     * 如果行内 UIAS 用 HTTP Header / Cookie 注入用户身份，请改下面 {@code request.getParameter(...)} 的取值方式。
     */
    @RequestMapping(value = "/uniauth/callback", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "UIAS 回调端点",
            description = "UIAS 单点登录完成后，浏览器以 GET 或 POST 方式跳到这里（SAML POST Binding 用 POST + SAMLResponse 表单字段）。本端点解析 SAML 响应或 query 参数取用户身份 → 调 S120030044 查授权 → 建 session → 302 跳前端首页")
    public void uniAuthCallback(HttpServletRequest req, HttpServletResponse resp, HttpSession session) throws IOException {
        // ===== 诊断 dump：把 UIAS 回调时带的所有 query / headers / cookies 全部打到日志 =====
        // 用途：第一次联调时，看一眼后端 console 日志找到 UIAS 实际用什么字段传用户身份。
        // 找到后改 application-*.yml 的 platform.sidecar.uniauth.user-param-name；
        // 如果是 Header/Cookie，按下面注释改本方法第一行的 getParameter 取值方式。
        if (log.isInfoEnabled()) {
            StringBuilder dump = new StringBuilder(1024)
                    .append("[UniAuth.callback] === UIAS 回调上下文 dump ===\n")
                    .append("  request URI : ").append(req.getRequestURI()).append('\n')
                    .append("  query string: ").append(req.getQueryString()).append('\n')
                    .append("  query parameters:\n");
            java.util.Enumeration<String> pn = req.getParameterNames();
            while (pn.hasMoreElements()) {
                String n = pn.nextElement();
                dump.append("    ").append(n).append(" = ").append(req.getParameter(n)).append('\n');
            }
            dump.append("  headers:\n");
            java.util.Enumeration<String> hn = req.getHeaderNames();
            while (hn.hasMoreElements()) {
                String n = hn.nextElement();
                dump.append("    ").append(n).append(": ").append(req.getHeader(n)).append('\n');
            }
            dump.append("  cookies:\n");
            if (req.getCookies() != null) {
                for (jakarta.servlet.http.Cookie c : req.getCookies()) {
                    dump.append("    ").append(c.getName()).append(" = ").append(c.getValue()).append('\n');
                }
            } else {
                dump.append("    (none)\n");
            }
            log.info(dump.toString());
        }
        // ===== 诊断 dump 结束 =====

        // 优先按 SAML 2.0 解析（行内 UIAS 走 SAML POST Binding：SAMLResponse 字段 + base64 XML）
        String userDomainName = extractUserFromSaml(req);
        if (userDomainName == null) {
            // 退化：没有 SAML 响应，按 query 参数取（兼容简单 SSO 协议）
            userDomainName = req.getParameter(uniauthProps.getUserParamName());
        }
        log.info("[AuthController.uniAuthCallback] 收到 UIAS 回调 userDomainName={}", userDomainName);

        if (userDomainName == null || userDomainName.isBlank()) {
            log.warn("[AuthController.uniAuthCallback] 缺失用户身份参数 {}", uniauthProps.getUserParamName());
            resp.sendRedirect("/#/login?error=uniauth-missing-user");
            return;
        }
        try {
            LoginRespDTO loginResp = authService.loginByUniAuth(userDomainName, session);
            log.info("[AuthController.uniAuthCallback] 统一认证回调成功 empId={}", loginResp.getEmpId());
            resp.sendRedirect(uniauthProps.getFrontHomeUrl());
        } catch (Exception e) {
            log.error("[AuthController.uniAuthCallback] 回调处理异常: {}", e.getMessage(), e);
            resp.sendRedirect("/#/login?error=uniauth-failed&msg="
                    + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
        }
    }

    /**
     * 从 SAML 2.0 响应中提取用户身份。
     * <p>UIAS 用 SAML POST Binding：SAML XML base64 编码后放在 {@code SAMLResponse} 表单字段。
     * 本方法 base64 解码 → DOM 解析 → 优先按 {@code platform.sidecar.uniauth.user-param-name}
     * 匹配 {@code <saml:Attribute Name="...">}，兜底取 {@code <saml:NameID>}。
     * <p>解码后的 XML 会被打到日志方便首次联调时定位字段名。
     * @return 用户身份；非 SAML 流程返回 null
     */
    private String extractUserFromSaml(HttpServletRequest req) {
        String samlResponse = req.getParameter("SAMLResponse");
        if (samlResponse == null || samlResponse.isBlank()) return null;
        try {
            byte[] xmlBytes = Base64.getDecoder().decode(samlResponse);
            String samlXml = new String(xmlBytes, StandardCharsets.UTF_8);
            log.info("[UniAuth.callback] SAML Response XML 解码后：\n{}", samlXml);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // 安全：禁用 DOCTYPE 防 XXE 攻击
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = dbf.newDocumentBuilder().parse(
                    new InputSource(new StringReader(samlXml)));

            final String SAML_NS = "urn:oasis:names:tc:SAML:2.0:assertion";

            // 优先按配置的 user-param-name 匹配 <saml:Attribute Name="...">
            String configName = uniauthProps.getUserParamName();
            NodeList attrs = doc.getElementsByTagNameNS(SAML_NS, "Attribute");
            for (int i = 0; i < attrs.getLength(); i++) {
                org.w3c.dom.Element attr = (org.w3c.dom.Element) attrs.item(i);
                String attrName = attr.getAttribute("Name");
                if (configName != null && configName.equalsIgnoreCase(attrName)) {
                    NodeList values = attr.getElementsByTagNameNS(SAML_NS, "AttributeValue");
                    if (values.getLength() > 0) {
                        String v = values.item(0).getTextContent().trim();
                        log.info("[UniAuth.callback] SAML 取 Attribute[{}]={}", attrName, v);
                        return v;
                    }
                }
            }

            // 兜底：取 <saml:NameID>（SAML 默认主体标识）
            NodeList nameIds = doc.getElementsByTagNameNS(SAML_NS, "NameID");
            if (nameIds.getLength() > 0) {
                String v = nameIds.item(0).getTextContent().trim();
                log.info("[UniAuth.callback] SAML 取 NameID={}", v);
                return v;
            }

            log.warn("[UniAuth.callback] SAML XML 解析后未找到 NameID 或匹配的 Attribute[name={}]", configName);
            return null;
        } catch (Exception e) {
            log.error("[UniAuth.callback] SAML 解析失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 用户登出
     *
     * @param session HttpSession，登出时销毁
     * @return 成功响应
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "销毁当前 Session，清除登录状态")
    public ResponseWrapper<Void> logout(HttpSession session) {
        log.info("[AuthController.logout] 用户登出请求");
        authService.logout(session);
        return ResponseWrapper.success();
    }

    /**
     * 获取当前登录用户信息
     *
     * @param session HttpSession，从中读取用户上下文
     * @return 当前用户详情DTO
     */
    @GetMapping("/current-user")
    @Operation(summary = "获取当前用户信息", description = "返回当前登录用户的权限和身份信息")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<CurrentUserRespDTO> getCurrentUser(HttpSession session) {
        log.debug("[AuthController.getCurrentUser] 获取当前用户请求");
        CurrentUserContext ctx = authService.getCurrentUser(session);
        CurrentUserRespDTO dto = new CurrentUserRespDTO();
        dto.setEmpId(ctx.empId());
        dto.setUsername(ctx.username());
        dto.setDisplayName(ctx.displayName());
        dto.setMainOrgCode(ctx.mainOrgCode());
        dto.setMainOrgName(ctx.mainOrgName());
        dto.setOrgLevel(ctx.orgLevel());
        dto.setIsSystemAdmin(ctx.systemAdmin());
        // 填充 roles: 从 UserRoleMapper 查询并映射为 RoleSimpleDTO
        List<PtRole> roles = userRoleMapper.selectRolesByUserId(ctx.empId());
        dto.setRoles(roles.stream().map(r -> {
            RoleSimpleDTO rd = new RoleSimpleDTO();
            rd.setRoleId(r.getRoleId());
            rd.setRoleCode(r.getRoleCode());
            rd.setRoleChName(r.getRoleChName());
            return rd;
        }).collect(Collectors.toList()));

        // 填充 permissions 和 bizScopes: 复用 AuthService.getUserPermissions()
        PermissionSetRespDTO permSet = authService.getUserPermissions(ctx.empId());
        dto.setPermissions(
            permSet.getResourceUrls() != null
                ? new ArrayList<>(permSet.getResourceUrls())
                : new ArrayList<>()
        );
        if (permSet.getBizScopes() != null) {
            dto.setBizScopes(new LinkedHashMap<>());
            permSet.getBizScopes().forEach(dto.getBizScopes()::put);
        } else {
            dto.setBizScopes(new LinkedHashMap<>());
        }
        return ResponseWrapper.success(dto);
    }

    /**
     * 获取当前用户完整权限集合（H.1）
     *
     * @param session HttpSession，从中读取用户上下文
     * @return 当前用户的资源URL集合、BizScope映射、角色列表
     */
    @GetMapping("/permissions")
    @Operation(summary = "获取当前用户完整权限集合",
            description = "返回当前用户所有有权限的资源URL列表和BizScope映射，供前端/网关做完整权限判断")
    public ResponseWrapper<PermissionSetRespDTO> getPermissions(HttpSession session) {
        log.debug("[AuthController.getPermissions] 获取当前用户权限集合请求");
        CurrentUserContext ctx = authService.getCurrentUser(session);
        PermissionSetRespDTO dto = authService.getUserPermissions(ctx.empId());
        return ResponseWrapper.success(dto);
    }

    /**
     * 校验当前用户是否有指定资源/BizType的权限（H.2）
     *
     * @param req 权限检查请求（resourceUrl、resourceMethod必填，bizType/action可选）
     * @param session HttpSession，从中读取用户上下文
     * @return 权限检查结果（含RBAC和数据范围检查详情）
     */
    @PostMapping("/check-permission")
    @Operation(summary = "校验权限",
            description = "校验当前用户是否有指定资源/BizType的权限，返回详细检查结论")
    public ResponseWrapper<CheckPermissionRespDTO> checkPermission(
            @Valid @RequestBody CheckPermissionReqDTO req, HttpSession session) {
        log.debug("[AuthController.checkPermission] 权限校验请求 url={}, method={}",
                req.getResourceUrl(), req.getResourceMethod());
        authService.getCurrentUser(session); // 确保已登录
        CheckPermissionRespDTO dto = authService.checkPermission(req);
        return ResponseWrapper.success(dto);
    }
}
