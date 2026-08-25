# Playwright CLI 命令记录

工作目录：`E:\cx-workspace\zcfzxt_remote_20260821\xanzc_frontend`

```powershell
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 open http://127.0.0.1:8091/#/screen-admin/designer
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 goto http://127.0.0.1:8091/#/login?normal
# 使用测试账号完成登录；凭据不写入证据文件
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 goto http://127.0.0.1:8091/#/screen-admin/designer
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 resize 1920 1080
# 通过页面控件选择“省分行经营总览（全辖）”并选中折线组件
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 route-list
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 console info
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 requests
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 screenshot
node_modules\.bin\playwright-cli.cmd -s=screen-charts-rich-20260824 close
```

没有调用 `route`，没有注册前端 mock。验收结束后直接关闭会话，未执行保存或发布。
