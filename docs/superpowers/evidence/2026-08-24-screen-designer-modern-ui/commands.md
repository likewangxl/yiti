# 实际命令

工作目录：`E:\cx-workspace\zcfzxt_remote_20260821\xanzc_frontend`

```powershell
# 安装项目官方 CLI 所需浏览器运行时
npm.cmd exec -- playwright-cli install-browser chrome-for-testing

# 打开真实登录页
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 open 'http://127.0.0.1:8091/#/login?normal' --browser=chromium

# 登录：密码由当前 PowerShell 进程临时环境变量注入，未写入文件；此处已脱敏
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 fill e34 '<LOGIN_USER>'
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 fill e42 '<LOGIN_PASSWORD>'
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 click e43

# 进入设计器并切换到省分行屏
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 goto 'http://127.0.0.1:8091/#/screen-admin/designer'
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 click e558
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 click e827

# 真实页面检查与截图
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 resize 1920 1080
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 screenshot --filename '..\docs\superpowers\evidence\2026-08-24-screen-designer-modern-ui\01-province-designer-modern.png'
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 fill e628 '趋势折线'
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 screenshot --filename '..\docs\superpowers\evidence\2026-08-24-screen-designer-modern-ui\02-component-search.png'
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 route-list
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 console info
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 requests --filter '/api/'
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 request 168
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 response-body 168
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 request 173
.\node_modules\.bin\playwright-cli.cmd -s=screen-designer-modern-20260824 response-body 173

# 自动化回归
npm.cmd test -- --run src/views/screen/designer/__tests__/DesignerV2.spec.js src/views/screen/designer/panels/__tests__/ComponentPanel.spec.js
npm.cmd run build
```
