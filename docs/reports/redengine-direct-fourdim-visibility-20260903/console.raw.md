# Playwright CLI 控制台原始摘要

命令：`playwright-cli -s=direct-fourdim-evidence console error`

```text
Total messages: 5 (Errors: 0, Warnings: 5)
Returning 0 messages for level "error"
```

命令：`playwright-cli -s=direct-fourdim-evidence console warning`

```text
Total messages: 5 (Errors: 0, Warnings: 5)

[WARNING] [Vue Router warn]: <router-view> can no longer be used directly inside <transition> or <keep-alive>.
Use slot props instead.

[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
[WARNING] ElementPlusError: [el-radio] [API] label act as value is about to be deprecated in version 3.0.0, please use value instead.
```

重复的 Element Plus 告警来自四个状态页签单选项，不包含本次接口失败或运行时异常。
