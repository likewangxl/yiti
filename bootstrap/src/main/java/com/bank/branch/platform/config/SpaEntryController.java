package com.bank.branch.platform.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

/**
 * 前后端一体下路径式入口重定向。
 * <p>前端为 hash 路由（{@code createWebHashHistory}），登录页真实地址是 {@code /#/login}。
 * 浏览器直接输入 path 式 {@code /login} 不被前端 hash 路由识别，且会落到后端静态回退，
 * 表现为打不开/报错。这里统一 302 到 {@code /#/login}，让应用从 {@code /} 启动并进入 hash 登录页。</p>
 */
@Controller
public class SpaEntryController {

    /** 浏览器直接访问 /login → 重定向到前端 hash 登录页。 */
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        response.sendRedirect("/#/login");
    }
}
