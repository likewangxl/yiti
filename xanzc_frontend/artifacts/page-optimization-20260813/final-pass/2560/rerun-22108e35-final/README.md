# 2560x1440 最终官方 playwright-cli 终验

结论：**GO / PASS**。

- 基线：`22108e35edb4f859c2d806ec4b1868d7aac274f6`；`bf330852`、`16c74710`、`22108e35` 均为 HEAD 祖先。
- 会话：`pageopt-final-2560-22108e35`，Chromium，视口 `2560x1440`。
- 起讫 `route-list` 均为 `No active routes`，扫描注册 mock/route 数为 0。
- 普通后台 59/59 PASS；XIAN 草稿概览与四个二级分行钻取 4/4 PASS；无产品首错。
- 登录仅使用普通管理员真实会话；凭据未写入任何证据。
- 除登录 POST 与只读 `/api/screen/data` POST 外，没有业务写请求；没有改代码、配置或数据库。

## 59 路由

- 59/59 命名路由通过，6 个参数化详情均从真实 GET 响应发现 ID，真实值未归档。
- document、`.content`、main 外层横滚均为 0；48 张实载表的 630 个 data row、4553 个 data cell、48 个 header row、341 个 header cell 均严格为 40px。
- 4553 个 data cell 的上下 padding 为 0，bottom/right border 均为 1px；内部宽表滚动容器和 554 个 sticky 操作列门禁全部通过。
- 150 个筛选控件均为 32px；524 个实载 tag 均为 24px。
- 166 个路由扫描请求均为只读请求，失败、坏响应、写请求均为 0；console error、非动态菜单 warning、fallback 均为 0。
- 六个点名取消、两个 reset、四个 warning=0 目标均完成。
- 长页签：容器 48px、57 个页签均 40px、宽度 112–200px、关闭按钮 40x40、内部横滚与 active 可见、ArrowLeft/Home/End 均通过。
- `ProductLib` 现有 8 个 clamp cell 没有 `>=2px` 的真实长文本溢出样本；短样本仅 1px 高度舍入差，hover 前后均未误加 title，记录 `not_exercised_no_real_overflow_sample`。
- `PerfTaskMonitor` 当前真实数据未出现 `.err-inline` 样本；页面的 40px 表格、sticky 操作列与横滚门禁通过，数据态错误行项记未实际覆盖。

## `22108e35` 专项

- `PerfMetrics`：14/14 个实载 small tag 均为 24px，line-height 22px，vertical-align middle。
- `WorkflowMonitor`：20/20 个 `main.bp-crud td.compact-stack-cell .cell > code.mono` 均为 rect 18px、clientHeight 16、scrollHeight 16、line-height 16px、上下 1px 边框、padding/margin 0、border-box，无纵向裁切。
- 当前 Workflow 真实数据没有 `>=2px` 横向溢出 code；全部 94 个短 stack 子行 hover 后均未误加 title。

## XIAN 草稿与钻取

- 草稿 GET 为 HTTP 200、`code=0`；最小投影为原生 `schemaVersion=2`、`mode=XIAN_COMPOSITE`、`baseRegion=XIAN_OUTLINE`，四节点锚点/编码匹配规范。
- 可见截图明确显示未央区、莲湖区、新城区、碑林区、雁塔区、长安区六个标签；内部 ECharts regions API 因 `vue-echarts` 封装不可读取，因此不宣称内部 regions 已被程序化验证。六区结论由可见 canvas 像素截图与真实 schema2 响应契约交叉证明。
- 外层标题底部 96px、内层标题顶部 97.3px，间隔 1.3px；设计稿 padding-top 为 72px，随 2560 视口缩放，无重叠。
- 四个可访问节点、示意位置说明、OSM 链接/ODbL 文案、无 redengine 元素/脚本均通过。
- 宝鸡 click→128、渭南 Enter→191、咸阳 Space→169、榆林 click→129；4/4 路由正确。
- 四次钻取共 20 个 `/api/screen/data` POST：20/20 HTTP 200、`code=0`、`nullPaths=[]`；每次均为 5 个 schema v1 `SCR_BRANCH` 请求，保留 `dsId`、`period`、目标 `orgCode`。
- `dateFrom`/`dateTo` 均未序列化；`empId:''` 是已确认的合法 String 假值并原样保留，不作为 null，也不被探针误判。
- XIAN 阶段意外写请求、console error、fallback、redengine 元素均为 0。

## 证据边界

- `vue-echarts` Web Component 不暴露内部 regions，因此最终证据不宣称内部 regions 已程序化验证；六区由可见截图与真实 schema2/XIAN_COMPOSITE 契约交叉证明。不可用的探索性探针输出不纳入最终发布目录。
- `empId:''` 是合法 String 假值；最终权威结果 `json/xian-drill.json` 仅禁止 null/undefined，并记录四节点从头复验的 20/20 请求。

## 会话与脱敏

- 末尾官方 CLI：`route-list` 为 `No active routes`；`console error` 返回 0；随后会话已关闭。
- `playwright-cli list` 中本会话已不存在；另一个 `final1920xian` 会话属于并行验收，未触碰。
- 前端 8091 与后端 18081 未重启、未停止。
- 六张取消弹窗截图因背景表格仍可能辨识真实 ID，已在归档前删除且不可恢复；取消结果保留在脱敏 JSON。其余普通页表体和四张支行数据区均已遮罩。

权威证据：`json/routes-59.json`、`json/targeted-regression.json`、`json/xian-draft-contract.json`、`json/xian-drill.json`。执行脚本与门禁摘要见 `raw/`；机械脱敏清单、递归扫描报告与逐文件校验和分别见 `json/redaction-manifest.json`、`raw/redaction-scan.txt`、`SHA256SUMS.txt`。
