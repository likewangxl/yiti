# 手机端业绩调整「业务类型」改为查询 PERF_BIZ_KIND 字典 — 设计

- 日期：2026-06-11
- 模块：soap-gateway-center（callpu 网关）+ 手机端前端 `front/xazc_transfer_front`
- 目标：把手机端业绩调整的「业务类型」从**硬编码中文**改为**实时查询 `SYS_DICT` 字典 `PERF_BIZ_KIND`（status=ACTIVE）**，前后端口径与 PC 管理端（`xanzc_frontend/Adjust.vue` + perf `submitAdjust`）完全一致。

## 1. 问题（已核实）

手机端三处都在用硬编码，且与字典不符：

1. **`applyAdd.vue:269`** 硬编码 `options1: ["存款","贷款","中收","结构性存款","大额存单"]`，并把**中文当值**提交。
2. **后端 `CallPuDispatchService.handlePerfSave`** 把中文经 `BUSINESS_TYPE_TO_BIZ_SUFFIX` + `custType` 前缀翻译成 `CORP_NCD`/`CORP_STRUCT_DEPOSIT` 等**字典里不存在的码**（中收→`CORP_INTERMEDIATE`、大额存单→`CORP_NCD`、结构性存款→`CORP_STRUCT_DEPOSIT`），与 PC 落库的真实码不一致。
3. **`applyInfo.vue` `bizKindText`** 用硬编码 `suffixMap` 反显，对真实数据显示错误（`CORP_LARGE_CD` 显示成「大额存单」实为「结构性存款」；`CORP_FOREX` 显示成「外汇」实为「大额存单」）。

### 真实字典数据（`SYS_DICT`，`dict_type='PERF_BIZ_KIND'`，status=ACTIVE，按 sort_order）

| sort | dict_code | dict_label |
|---|---|---|
| 1 | `CORP_DEPOSIT` | 存款 |
| 2 | `CORP_LOAN` | 贷款 |
| 3 | `FEE_BIZ` | 中收 |
| 4 | `CORP_LARGE_CD` | 结构性存款 |
| 5 | `CORP_FOREX` | 大额存单 |

> `SYS_DICT_ITEM` 该类型为空；`dict_value` 仅为 1–5 无业务含义。故 **label=dict_label 用于显示，value=dict_code 用于提交/存储**（与 PC 一致）。

## 2. PC 端做法（参照基准，已核实）

- `Adjust.vue` `onMounted → loadBizKindDict()` 调 `listDictItems('PERF_BIZ_KIND')`，`bizKindOptions = items.map(d => ({ label: d.dictLabel, value: d.dictCode }))`。
- 表单 `dlg.form.bizKind` 存**字典码数组**；提交时 `bizKind: dlg.form.bizKind.join(',')` → 逗号拼接字典码（如 `CORP_DEPOSIT,CORP_LOAN`），`submitAdjust` 直接落 perf `biz_kind`，**后端不翻译**。
- 反显 `d.bizKind.split(',')`；展示 `fmtBizKind` 用 `bizKindMap`（码→label，带 fallback）。

**结论**：本方案是把手机端 1:1 拉齐到 PC 口径，不新增业务约定。

## 3. 方案

### 3.1 后端 — 新增通用查字典 callpu RuleName `SYS_DICT_ITEMS`

- `CallPuRequest.Parm` 新增字段 `dictType`（`@JsonProperty("dictType")`）。
- 新增响应 DTO `DictItemData`：`List<Item> items`，`Item = { String dictCode; String dictLabel; }`。
- `CallPuDispatchService`：
  - 注入 `DictApi`（system-governance-center）。
  - `dispatch` 增加分支 `"SYS_DICT_ITEMS" -> handleDictItems(parm)`。
  - `handleDictItems`：取 `parm.dictType`，为空时返回失败信封「字典类型不能为空」；调 `dictApi.getDictItems(dictType)`（已是 ACTIVE + sort_order 排序），映射为 `DictItemData`，`CallPuResponse.ok`。
- `soap-gateway-center/pom.xml` 显式增加 `system-governance-center` 依赖（当前仅经 performance 传递依赖，显式声明更稳）。
- 更新模块 `CLAUDE.md`「已支持 RuleName」清单。

### 3.2 后端 — `handlePerfSave` 改为以字典码直落（对齐 PC）

- 删除 `BUSINESS_TYPE_TO_BIZ_SUFFIX` 常量与 `toBizKindCsv(custType, businessType)` 方法。
- 新增 `validateBizKindCsv(String businessType)`：按逗号拆分、trim、跳过空项；逐项 `dictApi.isValidDictValue("PERF_BIZ_KIND", code)` 校验；任一非法 → 抛 `IllegalArgumentException("业务类型不合法: " + code)`（由 `dispatch` 兜底为失败信封）；返回去空后原样逗号拼接的码串。
- `handlePerfSave` 中 `bizKind = validateBizKindCsv(parm.getBusinessType())`；`custType` 仍由 `APPLY_TYPE_TO_CUST_TYPE.get(parm.getApplyType())` 推导（不变）。
- `PERF_BIZ_KIND` 常量字符串提取为类常量 `DICT_PERF_BIZ_KIND = "PERF_BIZ_KIND"`，供 3.1/3.2 复用。

### 3.3 前端 `applyAdd.vue`

- 新增 `loadBizKindDict()`：用 `buildParam("SYS_DICT_ITEMS", { dictType: "PERF_BIZ_KIND" }, "get")` 调网关；成功时 `options1 = (RspMsg.items||[]).map(d => ({ label: d.dictLabel, value: d.dictCode }))`；失败兜底 `options1 = []`。
- `created()` 调 `loadBizKindDict()`。
- `data.options1` 初始改为 `[]`（不再硬编码）。
- 模板 radio/checkbox：`v-for="item in options1"`，显示 `{{ item.label }}`，`:name="item.value"`（提交存码）。
- `businessType1()` / `submitCust()` 逻辑不变（`demo6` 现在存的是码数组，`join(',')` 即码串）。
- `getInfo()`（编辑回填，当前入口已禁用）中 `this.demo6 = businessType.split(",")` 天然对齐码。

### 3.4 前端 `applyInfo.vue`（待审批/已审批点入的详情页）

- 新增 `bizKindMap`（data，`{}`）+ `loadBizKindDict()`：同样调 `SYS_DICT_ITEMS`，`bizKindMap[d.dictCode] = d.dictLabel`。
- `created()` 调 `loadBizKindDict()`。
- `computed.bizKindText` 改为：`raw.split(',').map(c => bizKindMap[c.trim()] || FALLBACK[...] || c).join('、')`；保留一份精简硬编码 fallback 兜底历史脏码，删除原易错的 `suffixMap` 前缀截取逻辑。

## 4. 数据流

```
applyAdd 进入 → callpu SYS_DICT_ITEMS(dictType=PERF_BIZ_KIND) → DictApi.getDictItems → [{dictCode,dictLabel}]
  选中 → demo6=[码] → 提交 businessType="CORP_DEPOSIT,FEE_BIZ"
    → callpu PERF_SAVE → handlePerfSave → validateBizKindCsv(逐项 isValidDictValue) → 原样存 perf biz_kind
待审批/已审批 → applyInfo → PERF_INFO 返回 biz_kind 码串 + SYS_DICT_ITEMS 建 map → bizKindText 码→中文
```

## 5. 错误处理

- 网关查字典：`dictType` 空 → 失败信封；`getDictItems` 返回空 → `items: []`（前端 options 为空，不报错）。
- 前端拉字典失败：兜底空数组 / 空 map，页面不崩（与现有 `fetchOrigAlloc` 容错风格一致）。
- 提交校验：任一业务类型码非法 → `PERF_SAVE` 返回失败信封 `ReturnCd=99`，前端 toast。

## 6. 测试（后端 TDD，红-绿-重构）

`CallPuDispatchServiceTest`（mock `DictApi`）：
1. `SYS_DICT_ITEMS` 正常：返回 ACTIVE 字典项映射为 `{dictCode,dictLabel}`。
2. `SYS_DICT_ITEMS` `dictType` 为空：失败信封。
3. `handlePerfSave` 合法码串（`CORP_DEPOSIT,FEE_BIZ`，`isValidDictValue` 全 true）：透传存入 `cmd.bizKind`，不再翻译中文。
4. `handlePerfSave` 含非法码（某项 `isValidDictValue=false`）：失败信封「业务类型不合法」。
5. 既有 PERF_SAVE/PERF_LIST 等用例回归不破。

> 前端无单测框架，手工验证三页。

## 7. 范围与非目标

- **不**改 perf 模块及其 `biz_kind` 存储/审批路由（PC 已证明吃字典码）。
- **不**改 PC 端。
- **不**为 `applyType`（公司/零售）做选项过滤（字典扁平 5 项，与 PC 一致全展示）。
- 通用 `SYS_DICT_ITEMS` 仅按需用于 `PERF_BIZ_KIND`，不预建其它字典消费方。
