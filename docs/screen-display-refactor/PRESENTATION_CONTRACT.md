# S02 经营大屏展示子协议

状态：`VERIFIED`；协议版本：`displaySchemaVersion=1`；起点提交：`40aa91ea`。

本协议只描述代码化经营大屏如何展示现有受控数据。根渲染包继续使用 `schemaVersion=2`，运行取数继续使用 `runtimeSchemaVersion=2` 和 `screenCode + blockId`。没有 `displaySchemaVersion` 的旧发布包不进入新协议；未知版本必须拒绝，不能按旧包猜测。

前端权威入口为 `xanzc_frontend/src/views/screen/presentation/contract/displayContract.js`，后端类型位于 `report-analytics-center/.../dto/req/presentation/`。S02只冻结类型和跨字段规则，S03才把它接入画布保存、发布、回读和回滚。

## 1. 根结构

```json
{
  "schemaVersion": 2,
  "canvasStyle": {
    "presentation": {
      "type": "CODE",
      "template": "branch-overview-v1",
      "displaySchemaVersion": 1,
      "institutionRules": {
        "allowedOperatingLevels": ["PRIMARY"],
        "allowedOrgNatures": ["SECONDARY_BRANCH"]
      },
      "display": {
        "components": []
      }
    }
  }
}
```

`institutionRules` 是新展示协议的服务端机构过滤契约。两个字段都必须是非空、无重复的字符串数组；运行时按精确集合与服务端已授权的 ACTIVE 画像求交，缺失或空规则保持 fail-close，不按机构名称、编码长度或前端角色猜测。三个经营模板迁移使用已核验的 `PRIMARY` / `SECONDARY_BRANCH` 集合；发布响应会在 `ScreenRenderRespDTO.institutionRules` 原样返回同一规则。画像坐标优先使用已核验 GCJ-02；缺失时仅可复用当前授权 `orgCode` 的 `RPT_SCREEN_MAP_POINT` ACTIVE 点位并标记 `locationSource=LEGACY_MAP_POINT`，不造点、不扩大机构范围。

- `display.components` 是有序数组，数组顺序和每项 `order` 均保留；渲染时按模板区域、order、原数组顺序稳定排序。
- `componentId` 在当前屏展示配置内唯一且稳定。复制组件必须生成新ID；标题和组件类型不能作为身份。
- 一个展示组件可以引用多个已存在的 `blockId`；同一组件不能重复引用同一个block。不同展示组件允许共享一个block，删除展示组件不会删除共享数据源。
- block是否属于当前屏、是否存在、是否符合条线和权限，由S03服务端校验；浏览器提交的来源元数据只是候选快照，不能替代服务端事实。

## 2. 七类组件

| componentType | 用途 | content最低要求 |
| --- | --- | --- |
| `METRIC_CARD` | 单值指标卡 | `mainField` |
| `COMPLETION` | 完成情况/水位 | `mainField`；公式来自来源说明，不在前端新算业务口径 |
| `TREND` | 一个或多个时序系列 | `series`至少一项 |
| `COMPOSITION_TABS` | 存款/贷款/收入等公司零售结构 | `tabs`至少一项 |
| `RANKING` | 可切换指标的机构完整榜单 | `rankingMetrics`至少一项 |
| `MAP` | 省/市/机构地图着色 | `mainField` |
| `DETAIL_TABLE` | 机构或指标明细 | `columns`至少一项 |

组件通用字段：

- `componentId`：`[A-Za-z][A-Za-z0-9_-]{1,63}`。
- `layoutRegion`：`HEADER/LEFT/CENTER/RIGHT/BOTTOM/OVERLAY`，表示受控模板区域，不是自由坐标。
- `order`：非负整数；`visible`必须显式为布尔值，`false`是合法值。
- `text`：标题模式、标题、副标题和说明。
- `format`：展示单位、小数位0～8、千分位、负数样式和空值文案；小数位0不得被默认值覆盖。
- `content`：类型化主字段、副字段、series、columns、tabs和rankingMetrics；数组顺序是配置语义。
- `interaction`：白名单动作和业务目标。
- `dataRefs`：至少一个当前屏block引用及指标身份快照。

## 3. 标题与清空语义

标题解析顺序：

1. `titleMode=CUSTOM` 且非空的组件标题；
2. 当前发布数据引用中的指标名称快照；
3. 迁移自旧 `metricLabels` 的明确覆盖；
4. 模板默认标题；
5. 空字符串，由页面显示安全空态，不用组件类型冒充业务标题。

`CUSTOM` 的空白标题非法；`AUTO` 的空标题表示恢复继承。副标题和说明为空字符串表示明确清空，字段缺失表示使用协议默认值。S03序列化不得通过忽略空值把“清空”变回旧值。

更换数据引用时，已有CUSTOM标题可以保留，但配置界面必须提示重新核对；AUTO标题随新的指标名称快照变化。改标题不会改变metricCode、blockId或计算口径。

## 4. 内容结构

### 4.1 series

每项包含稳定 `seriesKey`、返回字段 `field`、显示 `label` 和原始 `unit`。不支持用数组下标作为长期身份。多系列可以共享一个block，也可以分别引用不同block；S03需证明字段属于引用结果。

### 4.2 columns

每项包含 `columnKey/field/label/unit/visible`。列标题可覆盖，字段身份不变；隐藏列仍保留配置顺序。未知列、重复columnKey和未声明字段在S03/S06继续校验。

### 4.3 tabs

每项包含 `tabKey/label/corporateField/retailField/totalField/unit`。`totalField`可空，表示没有核定总量；运行时不能因此把公司与零售强制归一为100%。

### 4.4 rankingMetrics

每项包含 `metricKey/field/label/unit/direction`，方向只能ASC/DESC。缺数机构处理、完整机构数和并列规则由S10实现；协议不允许用标题推断排序方向。

## 5. 数据引用和单位

`dataRefs`的类型化字段为：

- `blockId`：正整数，持久化后由服务端管理。
- `role`：`PRIMARY/SECONDARY/DIMENSION`。
- `metricCode/metricName`：业务身份和名称快照，可空但不能互相替代。
- `unit`：来源原始单位，必填。
- `dimension`：`ORG/EMP/CUST/COMMON`。
- `formula`：只读口径说明，不能作为脚本执行。

单位白名单为 `AUTO/YUAN/TEN_THOUSAND/HUNDRED_MILLION/COUNT/TEN_THOUSAND_COUNT/PERCENT/RATIO`。AUTO只表示展示不强制缩放，不能作为未知原始单位的替代。

金额、计数、比例是不同单位类型。指标卡、完成情况、结构和排名的显式展示单位必须与来源单位同类型；例如来源YUAN不能直接配置PERCENT。RATIO表示0～1比例、PERCENT表示百分数，转换只能执行一次。趋势和明细表可以有多单位字段，每个series/column必须分别声明单位，不能用组件单一单位覆盖全部字段。

合法0值、`visible=false`和`decimals=0`均保留。null表示缺失，不能归一为0。

## 6. 交互动作

允许动作：

- `NONE`
- `OPEN_BUSINESS_LINE`，target只能是 `CORP/RETAIL/COMMON`
- `OPEN_CITY`
- `OPEN_INSTITUTION`
- `OPEN_METRIC_DETAIL`

target若存在必须是大写业务身份 `[A-Z][A-Z0-9_]{0,63}`，不是URL。协议不接受任意路径、JavaScript或外部链接；实际目标屏权限及机构范围由S13重新校验。

## 7. 严格性与兼容性

- 旧presentation未声明新版本时，前端规范化结果为null、校验无错误，继续使用旧展示逻辑。
- 新版本的未知根字段、组件字段及嵌套配置字段均非法。前端S02已经检查；S03后端接线必须使用严格JSON解析并调用后端跨字段校验，不能依赖Spring默认忽略未知字段。
- 未知版本、未知组件、重复componentId、单组件重复blockId、非法单位和非法动作均拒绝。
- 后端DTO不使用Object或Map承载业务配置。当前类只承载请求类型；S03可增加响应DTO，但不能改变本协议字段含义。
- 跨屏block引用、字段是否存在、来源角色、条线、机构范围、草稿/发布版本和权限不是纯DTO能证明的内容，留给S03/S04服务层校验。

## 8. Golden语义与S02验证

前端fixture覆盖：

- 旧presentation：不启用新协议；
- 合法新配置：七类组件、`visible=false`、`decimals=0`、多趋势系列；
- 非法配置：重复组件ID、单组件重复blockId、非法动作、未知字段和单位类型冲突。

后端纯测试覆盖：旧配置兼容、共享block允许、重复身份拒绝、未知版本、空白CUSTOM标题、非法目标、嵌套系列身份和单位冲突。S02测试不连接数据库，也不证明保存发布链路已完成。
