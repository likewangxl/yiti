# 页面优化最终验收证据

结论：**GO / PASS**。本目录发布基于代码基线 `22108e35edb4f859c2d806ec4b1868d7aac274f6` 的两档官方 `playwright-cli` 真实页面验收证据。

## 验收结果

- [1920×1080 权威报告](1920/README.md)：普通后台 59/59；XIAN 草稿总览通过、四个二级分行钻取 4/4、`/api/screen/data` 20/20 HTTP 200/code 0 且请求体递归无 null/undefined。
- [2560×1440 权威报告](2560/rerun-22108e35-final/README.md)：普通后台 59/59；XIAN 草稿总览通过、四个二级分行钻取 4/4、`/api/screen/data` 20/20 HTTP 200/code 0 且 `nullPaths=[]`。
- 两档起讫均无注册 mock route；除普通登录和只读取数 `/api/screen/data` POST 外，无意外写请求。两档 console error、API fallback、非动态菜单 warning 均为 0。
- 两个权威目录的 `SHA256SUMS.txt` 均已逐文件复核通过；发布 JSON、命令、raw 摘要、README 与截图文件名均经过凭据、会话、动态实体 ID、实际参数路径和查询实体值脱敏扫描。

## 既有动态菜单告警

- 两档均观察到同一组 15 条不同的 Vue Router “No match found”动态菜单路径 warning；它们在逐路由捕获时重复出现，已与非动态 warning 分离记录，不计入本轮页面优化产品首错。

## 不能据此声称

- 真实 `ProductLib` 数据没有 `>=2px` 的长文本溢出样本，因此不能声称真实长文本 hover title 已被端到端覆盖；仅验证现有短文本不会因 1px 舍入差误加 title。
- 2560 档无法透过 `vue-echarts` 封装程序化读取内部 regions，因此不能声称该档内部 regions 已被程序化验证；该档仅由可见六区截图与真实 schema2/XIAN_COMPOSITE 契约交叉证明。
- 截图未执行像素 OCR，因此不能把截图中的文字内容表述为 OCR 自动识别结论；截图只作为视觉证据，结构化指标以归档 JSON 为准。

本次发布仅包含 `1920/` 与 `2560/rerun-22108e35-final/` 两个已审计通过的最终目录；其他 2560 历史失败批不属于本结论。
