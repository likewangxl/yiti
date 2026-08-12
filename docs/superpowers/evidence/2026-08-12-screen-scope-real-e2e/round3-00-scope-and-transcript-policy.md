# Round 3：大屏范围扩展只读真实交互

结论范围：仅覆盖本轮指定的大屏设计器、数据源、命名机构组和机构经营画像的真实运行态只读交互；登录只是一次前置，不评价登录功能。

- 浏览器唯一使用项目内官方 CLI：xanzc_frontend/node_modules/.bin/playwright-cli，版本 0.1.18。
- 未使用 Playwright Test、MCP 浏览器或 Vitest。
- 前端以 VITE_USE_MOCK=false、VITE_API_BASE=/api、VITE_DEV_HOST=127.0.0.1、VITE_DEV_PORT=8092、VITE_DEV_STRICT_PORT=true、VITE_PROXY_TARGET=http://127.0.0.1:18082 启动。
- 后端只以 screen-scope-e2e profile 监听 127.0.0.1:18082；数据库只读确认是 yiti_test。没有连接或操作 yiti。
- 初始和最终 CLI route-list 都是 No active routes，无浏览器 mock route。

本文档集合中的 .raw.txt 是对相应命令的忠实、选择性转录：保留测试结论所需命令、状态码、请求方法、控制台计数和可见 UI 文案；省略无关的完整 sidebar DOM、静态资源和自动工作台请求。每个文件都会明确其是否为完整输出。真实截图以同目录 round3-*.png 保存。

敏感约束：本轮数据库和前端测试凭据只在启动子进程环境中注入。这里不记录任何凭据值、Cookie、Session、Authorization 或 token；含认证字段的本轮应用日志没有归档，已在停机后精确删除，见 round3-12-sensitive-temp-log-removal.raw.txt。

