# playwright-cli console 原始摘要

```text
Total messages: 7 (Errors: 0, Warnings: 5)

[WARNING] [ECharts] Can't get DOM width or height. Please check dom.clientWidth and dom.clientHeight. They should not be 0.
[WARNING] [ECharts] Can't get DOM width or height. Please check dom.clientWidth and dom.clientHeight. They should not be 0.
[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
```

CLI 的 `console` 默认按 info 级别返回上述 5 条 warning；其余 2 条为普通消息。没有 error。
