# 大屏设计器图表标题高对比度真实验收

> 验收日期：2026-08-25；工具：官方 `@playwright/cli` 0.1.18。
>
> 页面访问真实前端 `http://127.0.0.1:8091`，`/api/**` 经 Vite 代理到本 checkout 的
> `http://127.0.0.1:18081` 后端。`route-list` 输出 `No active routes`，未注册 mock。
> 仅执行登录和只读查询，登录凭据已脱敏；未保存、发布或写入大屏配置。

## 命令与交互

```bash
cd /home/djdev/lf/yiti/xanzc_frontend
./node_modules/.bin/playwright-cli -s=designer-title-contrast open --browser=chromium http://127.0.0.1:8091/#/login
./node_modules/.bin/playwright-cli -s=designer-title-contrast resize 1920 1080
./node_modules/.bin/playwright-cli -s=designer-title-contrast run-code "<通过真实 /api/auth/login 登录，凭据已脱敏>"
./node_modules/.bin/playwright-cli -s=designer-title-contrast goto http://127.0.0.1:8091/#/screen-admin/designer
./node_modules/.bin/playwright-cli -s=designer-title-contrast run-code "<选择省分行经营总览并读取目标标题 getComputedStyle>"
./node_modules/.bin/playwright-cli -s=designer-title-contrast route-list
./node_modules/.bin/playwright-cli -s=designer-title-contrast requests
./node_modules/.bin/playwright-cli -s=designer-title-contrast request-body 168
./node_modules/.bin/playwright-cli -s=designer-title-contrast request 168
./node_modules/.bin/playwright-cli -s=designer-title-contrast response-body 168
./node_modules/.bin/playwright-cli -s=designer-title-contrast console
./node_modules/.bin/playwright-cli -s=designer-title-contrast screenshot --filename /home/djdev/lf/yiti/docs/superpowers/evidence/2026-08-25-designer-title-contrast/province-title-contrast.png
```

## 视觉结果

目标标题“全省存款核心指标(聚合)”的浏览器最终计算样式：

```json
{"color":"rgb(213, 230, 255)","fontWeight":"600","textShadow":"rgba(0, 229, 255, 0.35) 0px 0px 10px"}
```

`rgb(213, 230, 255)` 对应设计器主题变量 `--scr-text: #d5e6ff`，在深蓝画布上呈浅白蓝高对比度。
同一规则也覆盖省屏内其他图表标题，但作用域仅限设计器 `.scr-surface-host`，未改变运行态标题样式。

截图：[province-title-contrast.png](province-title-contrast.png)

## 路由与真实请求

```text
No active routes
```

省屏目标数据源请求仍正常：

```text
#168 POST http://127.0.0.1:8091/api/screen/data
status: 200 OK
duration: 34ms
traceId: pc-1787628043565-863360
```

```json
{"schemaVersion":1,"screenCode":"SCR_PROVINCE","dsId":9010,"period":"LATEST","contextParams":{"orgCode":"","empId":""}}
```

响应继续包含 `rows=[[1150000.0000,null]]`，说明标题样式调整未破坏上一节点的数据预览链路。

## Console

`Errors: 0, Warnings: 2`。两条 warning 均为既有 ECharts 零尺寸提示，与标题样式无关；摘要见
[console.raw.md](console.raw.md)。

## 结论

PASS：设计器所有图表标题已使用浅白蓝高对比度文字并带弱青色发光，目标数据请求仍为真实 HTTP 200。
