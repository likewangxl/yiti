# 大屏设计器 LATEST 数据预览真实联调验收

> 验收日期：2026-08-25；工具：官方 `@playwright/cli` 0.1.18。
>
> 本次访问真实前端 `http://127.0.0.1:8091`，所有 `/api/**` 均经 Vite 代理到本 checkout 的
> `http://127.0.0.1:18081` 后端。`route-list` 输出 `No active routes`，未注册任何 mock route。
> 仅执行登录和只读查询；未保存、发布或写入大屏配置。登录命令中的凭据在证据中脱敏。

## 验收命令

```bash
cd /home/djdev/lf/yiti/xanzc_frontend
./node_modules/.bin/playwright-cli -s=designer-preview-latest open --browser=chromium http://127.0.0.1:8091/#/login
./node_modules/.bin/playwright-cli -s=designer-preview-latest resize 1920 1080
./node_modules/.bin/playwright-cli -s=designer-preview-latest run-code "<通过真实 /api/auth/login 登录，凭据已脱敏>"
./node_modules/.bin/playwright-cli -s=designer-preview-latest goto http://127.0.0.1:8091/#/screen-admin/designer
./node_modules/.bin/playwright-cli -s=designer-preview-latest route-list
./node_modules/.bin/playwright-cli -s=designer-preview-latest requests
./node_modules/.bin/playwright-cli -s=designer-preview-latest request-body 168
./node_modules/.bin/playwright-cli -s=designer-preview-latest request 168
./node_modules/.bin/playwright-cli -s=designer-preview-latest response-body 168
./node_modules/.bin/playwright-cli -s=designer-preview-latest console
./node_modules/.bin/playwright-cli -s=designer-preview-latest screenshot --filename /home/djdev/lf/yiti/docs/superpowers/evidence/2026-08-25-designer-preview-latest/province-latest-preview.png
```

页面交互：在顶部选择“省分行经营总览（全辖）”，再选中画布中的“全省存款核心指标(聚合)”组件。
右侧属性确认数据源为“全省存款聚合(引导式单值)”，周期为 `LATEST`。

## 路由/拦截器清单

```text
No active routes
```

因此本结论属于真实前后端联调，不是开发态 mock。

## 目标请求与响应

请求编号 `#168`：

```text
POST http://127.0.0.1:8091/api/screen/data
status: 200 OK
duration: 54ms
type: xhr
traceId: pc-1787626733225-882465
```

请求体：

```json
{"schemaVersion":1,"screenCode":"SCR_PROVINCE","dsId":9010,"period":"LATEST","contextParams":{"orgCode":"","empId":""}}
```

响应体：

```json
{"code":"0","message":"success","traceId":"pc-1787626733225-882465","data":{"columns":["一般性存款月均余额较上月-机构","一般性存款月均余额-机构"],"rows":[[1150000.0000,null]],"columnsMeta":[{"col":"一般性存款月均余额较上月-机构","alias":"全省存款较上月净增","role":"METRIC","unit":"万元","decimals":2},{"col":"一般性存款月均余额-机构","alias":"全省存款月均余额","role":"METRIC","unit":"万元","decimals":2}]},"timestamp":"2026-08-25T02:58:53.282811455Z"}
```

后端日志同一 trace 明确记录：

```text
ScreenDataReqDTO(schemaVersion=1, dsId=9010, screenCode=SCR_PROVINCE, blockId=null, period=LATEST, ...)
ScreenDataRespDTO(... rows=[[1150000.0000, null]] ...) (9ms)
```

非静态请求均为真实 HTTP 200；省屏加载共发出 5 个 `/api/screen/data` 请求，目标数据源请求为 `#168`。

## 页面与 console

截图：[province-latest-preview.png](province-latest-preview.png)

截图可见：

- 目标组件显示“全省存款较上月净增 1,150,000”；
- 右侧数据源为“全省存款聚合(引导式单值)”；
- 周期为 `LATEST`。

console 汇总为 `Errors: 0, Warnings: 5`。警告为既有的 2 条 ECharts 零尺寸提示和 3 条
Element Plus `el-radio label` 弃用提示，均与本次数据请求修复无关；原始摘要见
[console.raw.md](console.raw.md)。

## 结论

PASS：设计器已向真实后端发出带当前 `screenCode` 的 schema1 草稿预览请求，`LATEST` 数据成功返回并渲染。
