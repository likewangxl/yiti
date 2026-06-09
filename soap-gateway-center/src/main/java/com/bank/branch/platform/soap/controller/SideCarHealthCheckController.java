package com.bank.branch.platform.soap.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 边车（SideCar）健康检查 controller。
 *
 * <p>边车会定时请求 {@code /ishealth}，宿主应用据此上报自身健康状态：
 * 返回 {@code 0} 表示健康，{@code 1} 表示不健康。</p>
 *
 * <p>基础设施探活入口，由边车内网调用，故未挂 {@code @BizAuth}。</p>
 */
@RestController
public class SideCarHealthCheckController {

    /**
     * 边车定时探活接口。
     *
     * @return 健康返回 0，不健康返回 1
     */
    @RequestMapping("/ishealth")
    public int isHealth() {
        // 宿主应用判断自身健康状态
        // 宿主应用判断自身是否健康的逻辑代码
        // ... 暂未接入具体健康检查逻辑，恒定上报健康
        return 0;
    }
}
