# 代码化经营大屏实施计划

用户已确认蓝紫分行总览、市级展开与支行列表/地图/详情联动。保留数据源配置、发布快照、屏级角色、机构数据范围，逐步退出拖拽布局。参考图位于本机 generated_images，实施验收记录不包含连接口令。

## 中央地图与信息密度调整（2026-09-08）

用户要求把地图移到中央、参考旧分行级设计器补足数据项并改善视觉。对照 2026-08-24 旧分行截图，采用中央地图与趋势、两侧存款经营/业务构成/流程关注/排名/目标进度的布局。保留已确认的蓝紫风格和城市、支行下钻，在 1280、1366、1920 宽度验证；不以缩放整页或隐藏内容冒充适配。

Sol 先核定最小契约：新增净增和月均余额单值、排名可选净增/月均列、分行净增趋势；既有字段语义、授权目录、单位验证与失败清空不变。Luna 分别负责冻结契约下互不重叠的数据层和展示层实现，测试先红后绿；Sol 在真实 diff 和浏览器证据上最终审查。主任务负责整合、文档、官方 CLI 及运行验证。

以下模型为首版基线；本轮加法字段详见 [代码化经营大屏](modules/report-analytics-center/11-代码化经营大屏.md)。历史截图只用于业务类别和布局参考，不作为当前数据库数值或已发布配置的证据。

## 本次交付

1. Vue 编码的经营总览与市级全景，真实行政区 GeoJSON、可选择支行点位、搜索/排序/聚合/缩放、无坐标机构列表、选中机构趋势、返回与键盘关闭。
2. 独立数据适配层与简单字段绑定配置；组件只消费语义数据，不直接拼 SQL。运行时继续消费服务端授权发布绑定身份。
3. 在现有 JSON 字段保存代码模板声明与绑定，不增加表或 DDL；兼容历史画布的读取、发布、权限与回滚。
4. 独立、明确标识的本地演示预览只用于视觉验收；真实模式不得以演示数填补缺失、失败或权限拒绝。

## 公共前端模型（约定给并行工作者）

文件目录 `src/views/screen/panorama/`。UI `PanoramaDashboard.vue` 通过 props `model`, `loading`, `error`, `demo` 接收数据，emit `refresh`, `back`, `configure`，不调用后台。统一 model：

```js
{
 title: '分行经营总览', dataDate: '',
 kpis: [{ key:'deposit', label:'存款余额', value:null, unit:'亿元', change:null }],
 trend: [{ date:'2026-01', deposit:null, loan:null }],
 composition: [{ name:'对公业务', value:null, unit:'亿元' }],
 rankings: [{ orgCode:'', name:'', deposit:null, change:null }],
 attention: [{ label:'', count:null }],
 institutions: [{ orgCode:'', orgName:'', cityCode:'', parentOrgCode:'', lng:null, lat:null,
   coordSys:'', located:false, metrics:{deposit:null,loan:null,customers:null,target:null,rate:null},
   trend:[{date:'',deposit:null,loan:null}], attention:[] }],
 issues: [], citySummaries: { /* cityCode -> {kpis:[], dataDate:''}，无明确汇总则留空 */ }
}
```

缺失用 null，真实零是 0；金额模型输出约定亿元，客户约定万户，rate/change 是百分数（例如 86.5），必须由适配层明确单位换算。不得从名称猜指标含义、猜机构归属或伪造坐标。市级汇总只读 citySummaries，无可信汇总则显示未绑定，不能盲目累加上下级机构。

地图 `PanoramaMap.vue` props `geoJson`, `points`（机构结构）, `selectedOrgCode`, `mode`（province/city）, `selectedRegionCode`; emit `region-select`（{code,name}）, `branch-select`（orgCode）。地图使用真实 GeoJSON 与真实坐标，行政区域选择与机构权限分开；无坐标不绘点。Vue 层负责可访问替代列表。地图工作者同时提供 geography.js 导出 provinceGeo, cityGeoByCode, cityOptions。

## 分工与验证

- 主代理负责方案、契约裁决、集成、文档、运行环境、最终验收。
- Sol 只读审查服务端复用/机构授权路径。
- Luna 分别负责地图、纯展示组件、取数绑定层、服务端契约，文件所有权互斥；各自先失败测试再实现。
- Vitest 覆盖空/零/错误、单位、机构筛选、点位聚合、用户交互与请求竞态。后端纯单元测试覆盖绑定校验、快照、权限失败。
- 官方 playwright-cli 验证页面，记录开发态 mock 与真实链路的不同证据；对照两张参考图做视觉验收。
- 不执行共享 yiti 的数据源/大屏/权限写入，不执行真库测试；涉及数据库操作时先完成仓库要求的隔离库盘点和审批。代码与可审查结果先完成。
