# 大屏字段元数据金额量级验收

- 日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/datasources`、`http://127.0.0.1:8091/#/screen/SCR_AMOUNT`
- 工具：官方 `playwright-cli 0.1.18`，Chromium
- 会话：`screen-amount-scale`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。所有业务接口均由浏览器 route 拦截，未修改真实数据源、SQL 或数据库。

## 验收结论

1. 字段角色为“度量 METRIC”时显示“金额量级”，可选择元、万元、亿元。
2. 选择“亿元”后，单位显示“亿元”、小数位显示“2”，两个输入框均为 disabled。
3. 清空金额量级后，单位和小数位恢复可编辑。
4. 运行态接口返回原始值 `100000000` 及 `amountScale=HUNDRED_MILLION_YUAN`；数值卡和明细表均使用换算结果 `1.00`，明细表头显示“全省存款余额(亿元)”。
5. Console 为 0 error、0 warning；运行态两个区块均发出 `POST /api/screen/data` 并获得 HTTP 200。

## 证据

- `01-amount-scale-locked.png`：亿元预设下单位、小数位锁定。
- `02-custom-fields-enabled.png`：清空预设后单位、小数位恢复编辑。
- `03-chart-calculated-values.png`：原始一亿元在数值卡和明细表中换算为 `1.00`。
- `routes.raw.txt`：全部开发态 mock 路由。
- `network.raw.txt`：运行态取数请求、原始响应及换算后的页面断言。
- `console.raw.txt`：浏览器 Console 结果。
- `commands.md`：实际 CLI 操作顺序。
