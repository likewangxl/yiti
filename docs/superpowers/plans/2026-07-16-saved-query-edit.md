# 动态查询「我的方案」编辑增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让「我的方案」编辑能改指标/对象/维度/名称（现仅能改名），并修复 `updateSavedQuery` 乐观锁字段名 bug。

**Architecture:** 方案 B（独立编辑弹框）。把 `Dynamic.vue` 内联的对象选择弹框抽成可复用 `SubjectPicker.vue`；新增 `SchemeEditDialog.vue` 复用 `MetricPicker` + `SubjectPicker`；`SchemeListDialog` 的「编辑」改为打开编辑弹框；修 api 字段名。纯前端改动，后端能力已具备、不改。

**Tech Stack:** Vue 3 `<script setup>` + Element Plus + Vitest 1.6 + @vue/test-utils + happy-dom。

## Global Constraints

- 全部中文注释、UTF-8 编码。
- 测试文件放 `src/**/__tests__/*.spec.js`；`vitest run` 触发；`globals:false`→ 必须 `import { describe,it,expect,vi } from 'vitest'`；挂载组件的测试文件首行加 `// @vitest-environment happy-dom`。
- TDD 红-绿-重构：先写失败测试，再最小实现。
- **不改后端**（`report-analytics-center` 能力已具备）。**不改 `SchemeSaveDialog` 的保存校验**（保存仍要求对象≥1；仅"编辑"放开对象可空）。
- **不提交 `xanzc_frontend/vite.config.js`**（本地端口改动）。
- 运行测试：`cd xanzc_frontend && npx vitest run <file>`；构建校验：`npx vite build`。

---

### Task 1: 修 `updateSavedQuery` 字段名 bug（version → expectedVersion）

**Files:**
- Test: `xanzc_frontend/src/api/__tests__/report.savedQuery.spec.js`（新建）
- Modify: `xanzc_frontend/src/api/report.js:183-195`

**Interfaces:**
- Produces: `updateSavedQuery(id, payload)` — payload 支持 `{name?, dim?, metrics?:string[], subjects?:Array, version?, expectedVersion?}`；请求体字段用 **`expectedVersion`**（取 `payload.expectedVersion ?? payload.version`）。

- [ ] **Step 1: 写失败测试**

```javascript
// xanzc_frontend/src/api/__tests__/report.savedQuery.spec.js
// updateSavedQuery 必须把乐观锁字段以后端契约名 expectedVersion 下发(修 version→expectedVersion bug)
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ ok: true }) }));

import { call } from '../http';
import { updateSavedQuery } from '../report';

describe('updateSavedQuery 乐观锁字段名', () => {
  beforeEach(() => call.mockClear());

  it('把 version 以 expectedVersion 字段下发,并序列化 metrics/subjects', () => {
    updateSavedQuery('SQ1', {
      name: '新名', dim: 'EMP', version: 3,
      metrics: ['M0001', 'M0002'],
      subjects: [{ id: 'E1', name: '张三', org: '总行' }]
    });
    const [method, url, config] = call.mock.calls[0];
    expect(method).toBe('put');
    expect(url).toBe('/reports/saved-queries/SQ1');
    expect(config.data.expectedVersion).toBe(3);
    expect(config.data).not.toHaveProperty('version');
    expect(config.data.metricCodes).toBe(JSON.stringify(['M0001', 'M0002']));
    expect(JSON.parse(config.data.subjectIds)).toEqual([{ id: 'E1', name: '张三', org: '总行' }]);
  });

  it('对象为空数组也下发 subjectIds:"[]"(编辑允许对象为空)', () => {
    updateSavedQuery('SQ1', { name: 'x', dim: 'EMP', expectedVersion: 5, metrics: ['M1'], subjects: [] });
    const config = call.mock.calls[0][2];
    expect(config.data.subjectIds).toBe('[]');
    expect(config.data.expectedVersion).toBe(5);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd xanzc_frontend && npx vitest run src/api/__tests__/report.savedQuery.spec.js`
Expected: FAIL（当前下发的是 `version` 而非 `expectedVersion`）。

- [ ] **Step 3: 最小实现**

把 `xanzc_frontend/src/api/report.js` 的 `updateSavedQuery` 里这一行：

```javascript
  if (payload.version != null) body.version = payload.version;
```

改为：

```javascript
  // 后端契约字段是 expectedVersion(乐观锁),兼容老调用方仍传 version
  const ev = payload.expectedVersion != null ? payload.expectedVersion : payload.version;
  if (ev != null) body.expectedVersion = ev;
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd xanzc_frontend && npx vitest run src/api/__tests__/report.savedQuery.spec.js`
Expected: PASS（2 passed）。

- [ ] **Step 5: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add xanzc_frontend/src/api/report.js xanzc_frontend/src/api/__tests__/report.savedQuery.spec.js
git commit -m "fix(report-fe): updateSavedQuery 乐观锁字段名 version→expectedVersion(修方案更新对真后端失效)"
```

---

### Task 2: 抽取纯函数 `filterTreeByCodes` 到 `subjectScope.js`

数据范围裁剪是安全关键（防越权机构混入查询）。先把它抽成纯函数并单测，供 `SubjectPicker` 复用。

**Files:**
- Create: `xanzc_frontend/src/views/report/components/subjectScope.js`
- Test: `xanzc_frontend/src/views/report/components/__tests__/subjectScope.spec.js`

**Interfaces:**
- Produces: `filterTreeByCodes(nodes, codeSet)` — 保留 `code` 命中或有命中后代的节点，返回裁剪后的新树（与 `Dynamic.vue:267-276` 逻辑逐字一致）。

- [ ] **Step 1: 写失败测试**

```javascript
// xanzc_frontend/src/views/report/components/__tests__/subjectScope.spec.js
import { describe, it, expect } from 'vitest';
import { filterTreeByCodes } from '../subjectScope';

const tree = [
  { code: 'A', name: '西安分行', children: [
    { code: 'A1', name: '雁塔支行', children: [] },
    { code: 'A2', name: '碑林支行', children: [] }
  ] },
  { code: 'B', name: '榆林分行', children: [] }
];

describe('filterTreeByCodes', () => {
  it('保留命中节点及其命中路径上的祖先容器', () => {
    const out = filterTreeByCodes(tree, new Set(['A1']));
    expect(out.length).toBe(1);
    expect(out[0].code).toBe('A');
    expect(out[0].children.map(n => n.code)).toEqual(['A1']);
  });
  it('命中集合为空则返回空数组', () => {
    expect(filterTreeByCodes(tree, new Set())).toEqual([]);
  });
  it('祖先本身命中也保留', () => {
    const out = filterTreeByCodes(tree, new Set(['B']));
    expect(out.map(n => n.code)).toEqual(['B']);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd xanzc_frontend && npx vitest run src/views/report/components/__tests__/subjectScope.spec.js`
Expected: FAIL（`subjectScope` 模块不存在）。

- [ ] **Step 3: 最小实现**

```javascript
// xanzc_frontend/src/views/report/components/subjectScope.js
// 动态查询对象选择器的数据范围裁剪(纯函数,便于单测)。
// 从 Dynamic.vue 抽取,逻辑逐字保持:保留 code 命中、或有命中后代的节点(容器)。
export function filterTreeByCodes(nodes, codeSet) {
  const out = [];
  for (const n of nodes || []) {
    const children = filterTreeByCodes(n.children, codeSet);
    if (codeSet.has(n.code) || children.length) {
      out.push({ ...n, children });
    }
  }
  return out;
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd xanzc_frontend && npx vitest run src/views/report/components/__tests__/subjectScope.spec.js`
Expected: PASS（3 passed）。

- [ ] **Step 5: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add xanzc_frontend/src/views/report/components/subjectScope.js xanzc_frontend/src/views/report/components/__tests__/subjectScope.spec.js
git commit -m "refactor(report-fe): 抽取对象数据范围裁剪 filterTreeByCodes 为纯函数+单测"
```

---

### Task 3: 抽取 `SubjectPicker.vue` 并让 `Dynamic.vue` 改用它

把 `Dynamic.vue` 内联的对象选择弹框整体搬进 `SubjectPicker.vue`（**逻辑逐字保持**），做成自包含可复用组件；`Dynamic.vue` 用组件替换内联实现。

**Files:**
- Create: `xanzc_frontend/src/views/report/components/SubjectPicker.vue`
- Modify: `xanzc_frontend/src/views/report/Dynamic.vue`
- Test: `xanzc_frontend/src/views/report/components/__tests__/SubjectPicker.spec.js`

**Interfaces:**
- Produces `SubjectPicker.vue`：
  - Props：`visible: Boolean`（v-model）、`modelValue: Array`（v-model，对象数组 `[{id,name,org}]`）、`dim: String`。
  - Emits：`update:visible`、`update:modelValue`。
  - 打开(visible=true)时用 `props.modelValue` 初始化内部选择；点「确定」`emit('update:modelValue', selected)` + `emit('update:visible', false)`。
  - 自包含：内部自行 `getOrgTree()` + `getPickerScope(props.dim)`（`dim` 变或打开时重取），持有 `orgTreeData`/`pickerScope`，用 `filterTreeByCodes` 裁剪。

**搬移清单（从 `Dynamic.vue` 原样移入 `SubjectPicker.vue`，逻辑不改）：**
- 模板：`<el-dialog v-model="subjectDlg.show" ...>` 整块（`Dynamic.vue:113-180`）。
- 脚本：`subjectTreeRef`、`orgTreeData`、`pickerScope`、`selfOnlyPicker`(改为内部用)、`filterTreeByCodes`(改为 import)、`scopedOrgTree`、`subjectDlg` reactive、`filterOrgNode`、两个 `watch`(treeKw filter / show 打开重置)、`orgUsersCache`、`orgCheckTimer`、`onOrgCheckChange`、`onIncludeSubOrgChange`、`loadCheckedOrgEmployees`、`onEmpSearch`+防抖 watch、`onCustSearch`+防抖 watch、`addSubjectFromSearch`、`confirmSubjects`、`loadPickerScope`（`Dynamic.vue` 约 258-501 行相关块）。
- import：`getOrgTree, listOrgUsers`（from `@/api/orgs`）、`getPickerScope, searchReportEmployees, searchReportCustomers`（from `@/api/report`）、`filterTreeByCodes`（from `./subjectScope`）。

**`SubjectPicker.vue` 关键改写点（相对原内联）：**
- `subjectDlg.show` → 由 `props.visible` 驱动；`confirmSubjects` 不再写 `subjects.value`，改为 `emit('update:modelValue', selected.map(...))` + `emit('update:visible', false)`。
- 打开重置 `watch` 触发条件由 `subjectDlg.show` 改为 `() => props.visible`；初始 `subjectDlg.selected = [...props.modelValue]`；`subjectDlg.orgTree = scopedOrgTree()`。
- 组件内 `onMounted`/`watch(() => props.dim)`：`getOrgTree()` 填 `orgTreeData`、`getPickerScope(props.dim)` 填 `pickerScope`（维度变要重取，对齐原 `loadPickerScope`）。

- [ ] **Step 1: 写失败测试（挂载冒烟：确定即回传选择、取消不回传）**

```javascript
// xanzc_frontend/src/views/report/components/__tests__/SubjectPicker.spec.js
// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

vi.mock('@/api/orgs', () => ({ getOrgTree: vi.fn().mockResolvedValue([]), listOrgUsers: vi.fn().mockResolvedValue([]) }));
vi.mock('@/api/report', () => ({
  getPickerScope: vi.fn().mockResolvedValue({ mode: 'ALL', orgCodes: [] }),
  searchReportEmployees: vi.fn().mockResolvedValue([]),
  searchReportCustomers: vi.fn().mockResolvedValue([])
}));

import SubjectPicker from '../SubjectPicker.vue';

const stubs = {
  'el-dialog': { template: '<div><slot /><slot name="footer" /></div>' },
  'el-tree': true, 'el-input': true, 'el-checkbox': true, 'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>' },
  'el-tag': { template: '<span><slot/></span>' }
};

describe('SubjectPicker.vue', () => {
  beforeEach(() => vi.clearAllMocks());

  it('打开时用 modelValue 初始化已选,确定回传 update:modelValue', async () => {
    const wrapper = mount(SubjectPicker, {
      props: { visible: true, modelValue: [{ id: 'E1', name: '张三', org: '' }], dim: 'EMP' },
      global: { stubs }
    });
    await wrapper.vm.$nextTick();
    // 触发内部确定(通过组件暴露的方法或找到确定按钮)——实现里「确定」按钮 @click="confirmSubjects"
    await wrapper.vm.confirmSubjects();
    const emitted = wrapper.emitted('update:modelValue');
    expect(emitted).toBeTruthy();
    expect(emitted[0][0]).toEqual([{ id: 'E1', name: '张三', org: '' }]);
    expect(wrapper.emitted('update:visible')[0][0]).toBe(false);
  });
});
```

> 注：为让 `confirmSubjects` 可被测试调用，`<script setup>` 末尾加 `defineExpose({ confirmSubjects })`。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd xanzc_frontend && npx vitest run src/views/report/components/__tests__/SubjectPicker.spec.js`
Expected: FAIL（`SubjectPicker.vue` 不存在）。

- [ ] **Step 3: 实现 `SubjectPicker.vue`（按上方"搬移清单"逐字搬入，改写 4 处 props/emit 点，末尾 `defineExpose({ confirmSubjects })`）**

（源码见 `Dynamic.vue:113-180` 模板 + `258-501` 脚本；逻辑不改，仅按"关键改写点"接 props/emit。）

- [ ] **Step 4: 跑测试确认通过**

Run: `cd xanzc_frontend && npx vitest run src/views/report/components/__tests__/SubjectPicker.spec.js`
Expected: PASS。

- [ ] **Step 5: 改 `Dynamic.vue` 用 `<SubjectPicker>` 替换内联弹框**

- 删除内联 `<el-dialog v-model="subjectDlg.show" ...>`（:113-180），替换为：
```html
    <SubjectPicker v-model:visible="subjectDlgVisible" v-model="subjects" :dim="dim" />
```
- 脚本：删除已搬走的对象弹框相关状态/函数；`import SubjectPicker from './components/SubjectPicker.vue';`；③ 区域打开逻辑由 `subjectDlg.show = true` 改为 `subjectDlgVisible.value = true`（新增 `const subjectDlgVisible = ref(false)`）。
- **保留** `Dynamic.vue` 自身的 `pickerScope`/`selfOnlyPicker`/`loadPickerScope` 及 `onMounted` 里的 `getPickerScope`（③ 区域"仅本人"/禁点显示仍需要）；`watch(dim)` 里保留 `loadPickerScope()`；移除 `orgTreeData`/`filterTreeByCodes`/`scopedOrgTree`（已随 picker 迁走，Dynamic 不再直接用）。
- **移除** `Dynamic.vue` `onMounted` 中的 `getOrgTree()`（机构树只被 picker 用，已迁入 SubjectPicker）。

- [ ] **Step 6: 构建校验 + 跑全部前端测试**

Run: `cd xanzc_frontend && npx vite build && npx vitest run`
Expected: build 成功；已有测试仍全绿。

- [ ] **Step 7: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add xanzc_frontend/src/views/report/components/SubjectPicker.vue \
        xanzc_frontend/src/views/report/components/__tests__/SubjectPicker.spec.js \
        xanzc_frontend/src/views/report/Dynamic.vue
git commit -m "refactor(report-fe): 抽取对象选择器 SubjectPicker.vue,Dynamic.vue 改用(行为不变)"
```

---

### Task 4: 新增 `SchemeEditDialog.vue`（编辑方案：维度/名称/指标/对象）

**Files:**
- Create: `xanzc_frontend/src/views/report/components/SchemeEditDialog.vue`
- Test: `xanzc_frontend/src/views/report/components/__tests__/SchemeEditDialog.spec.js`

**Interfaces:**
- Consumes：`MetricPicker`（v-model 指标 code 数组、`:dim`）、`SubjectPicker`（v-model 对象数组、`:dim`）、`updateSavedQuery(id, {name,dim,metrics,subjects,expectedVersion})`。
- Produces `SchemeEditDialog.vue`：
  - Props：`visible: Boolean`(v-model)、`scheme: Object`（`{id,name,dim,metrics:string[],subjects:Array,version:number}`）。
  - Emits：`update:visible`、`saved`。
  - 打开时用 `scheme` 预填 `form.name/dim/metrics/subjects`。
  - 维度切换：若 `metrics.length || subjects.length`，`ElMessageBox.confirm('切换维度会清空已选指标和对象,确定?')`；确认清空指标+对象，取消回退 dim。
  - 保存校验：名称非空、`metrics.length>=1`、**对象可空**；通过则 `await updateSavedQuery(scheme.id, {name,dim,metrics,subjects,expectedVersion: scheme.version})` → `emit('saved')` + 关闭；失败交给 http.js 拦截器（catch 静默）。
  - `defineExpose({ save, onDimChange })` 供单测调用。

- [ ] **Step 1: 写失败测试**

```javascript
// xanzc_frontend/src/views/report/components/__tests__/SchemeEditDialog.spec.js
// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const updateSavedQuery = vi.fn().mockResolvedValue({ ok: true });
vi.mock('@/api/report', () => ({ updateSavedQuery: (...a) => updateSavedQuery(...a) }));
const confirmMock = vi.fn().mockResolvedValue(true);
vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: (...a) => confirmMock(...a) }
}));

import SchemeEditDialog from '../SchemeEditDialog.vue';

const stubs = {
  'el-dialog': { template: '<div><slot/><slot name="footer"/></div>' },
  'el-form': true, 'el-form-item': true, 'el-input': true, 'el-radio-group': true, 'el-radio-button': true,
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>' }, 'el-tag': { template: '<span><slot/></span>' },
  MetricPicker: true, SubjectPicker: true
};
const scheme = { id: 'SQ1', name: '方案一', dim: 'EMP', metrics: ['M1'], subjects: [{ id: 'E1', name: '张三', org: '' }], version: 2 };

describe('SchemeEditDialog.vue', () => {
  beforeEach(() => { updateSavedQuery.mockClear(); confirmMock.mockClear(); });

  it('预填 scheme 到表单', () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme }, global: { stubs } });
    expect(w.vm.form.name).toBe('方案一');
    expect(w.vm.form.dim).toBe('EMP');
    expect(w.vm.form.metrics).toEqual(['M1']);
    expect(w.vm.form.subjects).toEqual([{ id: 'E1', name: '张三', org: '' }]);
  });

  it('保存下发 expectedVersion 并 emit saved', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme }, global: { stubs } });
    await w.vm.save();
    expect(updateSavedQuery).toHaveBeenCalledWith('SQ1', {
      name: '方案一', dim: 'EMP', metrics: ['M1'],
      subjects: [{ id: 'E1', name: '张三', org: '' }], expectedVersion: 2
    });
    expect(w.emitted('saved')).toBeTruthy();
  });

  it('指标为空时拦截,不调用后端', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme: { ...scheme, metrics: [] } }, global: { stubs } });
    await w.vm.save();
    expect(updateSavedQuery).not.toHaveBeenCalled();
  });

  it('对象为空时放行(编辑允许对象为空)', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme: { ...scheme, subjects: [] } }, global: { stubs } });
    await w.vm.save();
    expect(updateSavedQuery).toHaveBeenCalledTimes(1);
    expect(updateSavedQuery.mock.calls[0][1].subjects).toEqual([]);
  });

  it('切换维度确认后清空指标和对象', async () => {
    const w = mount(SchemeEditDialog, { props: { visible: true, scheme }, global: { stubs } });
    await w.vm.onDimChange('ORG');
    expect(confirmMock).toHaveBeenCalled();
    expect(w.vm.form.dim).toBe('ORG');
    expect(w.vm.form.metrics).toEqual([]);
    expect(w.vm.form.subjects).toEqual([]);
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd xanzc_frontend && npx vitest run src/views/report/components/__tests__/SchemeEditDialog.spec.js`
Expected: FAIL（组件不存在）。

- [ ] **Step 3: 实现 `SchemeEditDialog.vue`**

```vue
<!-- 编辑查询方案 —— 改 维度/名称/指标/对象。后端 updateSavedQuery 乐观锁(expectedVersion)。 -->
<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)"
             title="编辑查询方案" width="640px" :close-on-click-modal="false">
    <el-form label-position="top" size="default">
      <el-form-item label="维度">
        <el-radio-group :model-value="form.dim" @update:model-value="onDimChange">
          <el-radio-button v-for="d in DIMS" :key="d.code" :value="d.code">{{ d.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item required>
        <template #label><span class="req">*</span> 方案名称</template>
        <el-input v-model="form.name" maxlength="200" />
      </el-form-item>
      <el-form-item :label="`指标 (已选 ${form.metrics.length})`">
        <div class="tags click" @click="metricPickerVisible = true">
          <el-tag v-for="c in form.metrics" :key="c" closable type="info" effect="plain"
                  @close.stop="form.metrics = form.metrics.filter(x => x !== c)">{{ c }}</el-tag>
          <el-tag class="add" effect="plain">+ 添加</el-tag>
        </div>
      </el-form-item>
      <el-form-item :label="`对象 (已选 ${form.subjects.length}，不选=查全部)`">
        <div class="tags click" @click="subjectPickerVisible = true">
          <el-tag v-for="s in form.subjects" :key="s.id" closable effect="plain"
                  @close.stop="form.subjects = form.subjects.filter(x => x.id !== s.id)">{{ s.name }}</el-tag>
          <el-tag class="add" effect="plain">+ 选择</el-tag>
        </div>
      </el-form-item>
    </el-form>

    <MetricPicker v-model:visible="metricPickerVisible" v-model="form.metrics" :dim="form.dim" />
    <SubjectPicker v-model:visible="subjectPickerVisible" v-model="form.subjects" :dim="form.dim" />

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { updateSavedQuery } from '@/api/report';
import MetricPicker from './MetricPicker.vue';
import SubjectPicker from './SubjectPicker.vue';

const DIMS = [{ code: 'EMP', label: '员工' }, { code: 'ORG', label: '机构' }, { code: 'CUST', label: '客户' }];
const props = defineProps({
  visible: Boolean,
  scheme: { type: Object, default: () => ({ id: '', name: '', dim: 'EMP', metrics: [], subjects: [], version: 0 }) }
});
const emit = defineEmits(['update:visible', 'saved']);

const saving = ref(false);
const metricPickerVisible = ref(false);
const subjectPickerVisible = ref(false);
const form = reactive({ name: '', dim: 'EMP', metrics: [], subjects: [] });

// 打开时用 scheme 预填(深拷贝,避免直接改父数据)
watch(() => props.visible, (v) => {
  if (v && props.scheme) {
    form.name = props.scheme.name || '';
    form.dim = props.scheme.dim || 'EMP';
    form.metrics = Array.isArray(props.scheme.metrics) ? [...props.scheme.metrics] : [];
    form.subjects = Array.isArray(props.scheme.subjects)
      ? props.scheme.subjects.map(s => ({ id: s.id, name: s.name || s.id, org: s.org || '' }))
      : [];
  }
}, { immediate: true });

// 维度是总开关:切换会作废已选指标/对象。有选择时先确认,确认才切并清空,取消则维度不变。
async function onDimChange(next) {
  if (next === form.dim) return;
  if (form.metrics.length || form.subjects.length) {
    try { await ElMessageBox.confirm('切换维度会清空已选指标和对象，确定？', '切换维度', { type: 'warning' }); }
    catch { return; }  // 取消:维度保持不变
  }
  form.dim = next;
  form.metrics = [];
  form.subjects = [];
}

async function save() {
  if (!form.name.trim()) { ElMessage.warning('请填写方案名称'); return; }
  if (!form.metrics.length) { ElMessage.warning('请至少选择 1 个指标'); return; }
  saving.value = true;
  try {
    await updateSavedQuery(props.scheme.id, {
      name: form.name.trim(), dim: form.dim,
      metrics: form.metrics, subjects: form.subjects,
      expectedVersion: props.scheme.version
    });
    ElMessage.success('方案已更新');
    emit('saved');
    emit('update:visible', false);
  } catch (e) {
    // 乐观锁冲突(后端归 RPT-40002 NO_ACCESS)等由 http.js 拦截器统一弹错
  } finally {
    saving.value = false;
  }
}

defineExpose({ save, onDimChange });
</script>

<style lang="scss" scoped>
.req { color: $danger; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; padding: 6px 8px; min-height: 36px;
  border: 1px solid $border-2; border-radius: 4px; align-items: center;
  &.click { cursor: pointer; } .add { cursor: pointer; border-style: dashed; color: $primary-400; } }
</style>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd xanzc_frontend && npx vitest run src/views/report/components/__tests__/SchemeEditDialog.spec.js`
Expected: PASS（5 passed）。

- [ ] **Step 5: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add xanzc_frontend/src/views/report/components/SchemeEditDialog.vue \
        xanzc_frontend/src/views/report/components/__tests__/SchemeEditDialog.spec.js
git commit -m "feat(report-fe): 新增 SchemeEditDialog 编辑方案(维度/名称/指标/对象+乐观锁)"
```

---

### Task 5: `SchemeListDialog` 的「编辑」改为打开编辑弹框

**Files:**
- Modify: `xanzc_frontend/src/views/report/components/SchemeListDialog.vue`
- Test: `xanzc_frontend/src/views/report/components/__tests__/SchemeListDialog.spec.js`

**Interfaces:**
- Consumes：`getSavedQuery(id)`（返回含 `metrics/subjects/version`）、`SchemeEditDialog`。
- 「编辑」handler：`const d = await getSavedQuery(row.id); editScheme.value = d; editVisible.value = true;`；`SchemeEditDialog` 的 `saved` → `refresh()`。
- 移除 `rename()` 的 `ElMessageBox.prompt`（删除 `updateSavedQuery` 直接改名逻辑，改走弹框）。

- [ ] **Step 1: 写失败测试**

```javascript
// xanzc_frontend/src/views/report/components/__tests__/SchemeListDialog.spec.js
// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';

const getSavedQuery = vi.fn().mockResolvedValue({ id: 'SQ1', name: 'A', dim: 'EMP', metrics: ['M1'], subjects: [], version: 1 });
vi.mock('@/api/report', () => ({
  listSavedQueries: vi.fn().mockResolvedValue([{ id: 'SQ1', name: 'A', dim: 'EMP', updatedTime: '2026-07-16T10:00:00' }]),
  deleteSavedQuery: vi.fn(), getSavedQuery: (...a) => getSavedQuery(...a)
}));
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }));

import SchemeListDialog from '../SchemeListDialog.vue';

const stubs = {
  'el-dialog': { template: '<div><slot/><slot name="footer"/></div>' },
  'el-table': { template: '<div><slot :row="rows[0]"/></div>', props: ['data'], computed: { rows() { return this.data || []; } } },
  'el-table-column': { template: '<div><slot :row="$parent.rows[0]"/></div>' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot/></button>' }, 'el-divider': true,
  SchemeEditDialog: true
};

describe('SchemeListDialog 编辑', () => {
  beforeEach(() => getSavedQuery.mockClear());

  it('点编辑先取详情再打开编辑弹框', async () => {
    const w = mount(SchemeListDialog, { props: { visible: true }, global: { stubs } });
    await w.vm.$nextTick();
    await w.vm.openEdit({ id: 'SQ1', name: 'A' });
    expect(getSavedQuery).toHaveBeenCalledWith('SQ1');
    expect(w.vm.editVisible).toBe(true);
    expect(w.vm.editScheme.id).toBe('SQ1');
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd xanzc_frontend && npx vitest run src/views/report/components/__tests__/SchemeListDialog.spec.js`
Expected: FAIL（无 `openEdit`/`editVisible`）。

- [ ] **Step 3: 实现改造**

`SchemeListDialog.vue` 改动：
- template：「编辑」按钮 `@click="rename(row)"` → `@click="openEdit(row)"`；在末尾（`</el-dialog>` 前或后）加 `<SchemeEditDialog v-model:visible="editVisible" :scheme="editScheme" @saved="onEdited" />`。
- script：
```javascript
import SchemeEditDialog from './SchemeEditDialog.vue';
import { listSavedQueries, deleteSavedQuery, getSavedQuery } from '@/api/report'; // 去掉 updateSavedQuery
// ...
const editVisible = ref(false);
const editScheme = ref({ id: '', name: '', dim: 'EMP', metrics: [], subjects: [], version: 0 });

async function openEdit(row) {
  try {
    const d = await getSavedQuery(row.id);
    if (!d) return;
    editScheme.value = { id: d.id, name: d.name, dim: d.dim,
      metrics: Array.isArray(d.metrics) ? d.metrics : [],
      subjects: Array.isArray(d.subjects) ? d.subjects : [],
      version: d.version };
    editVisible.value = true;
  } catch { /* http.js 已弹错 */ }
}
function onEdited() { refresh(); }
defineExpose({ openEdit, editVisible, editScheme });   // 供单测
```
- 删除原 `rename(row)` 函数。

- [ ] **Step 4: 跑测试确认通过 + 全量前端测试**

Run: `cd xanzc_frontend && npx vitest run`
Expected: 全绿（含新测试）。

- [ ] **Step 5: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add xanzc_frontend/src/views/report/components/SchemeListDialog.vue \
        xanzc_frontend/src/views/report/components/__tests__/SchemeListDialog.spec.js
git commit -m "feat(report-fe): 我的方案「编辑」改为打开编辑弹框(可改指标/对象/维度)"
```

---

### Task 6: 构建校验 + 手动端到端回归

**Files:** 无（验证）。

- [ ] **Step 1: 构建 + 全量测试**

Run: `cd xanzc_frontend && npx vite build && npx vitest run`
Expected: build 成功、全部测试绿。

- [ ] **Step 2: 手动回归（前端 8010 已在跑，改动经 vite HMR 生效；必要时重启）**

按 `docs/superpowers/specs/2026-07-16-saved-query-edit-design.md` §7 回归清单：
1. **Dynamic.vue 对象选择不回归**：员工/机构/客户三维度分别——机构树勾选、包含下级、员工/客户搜索、数据范围裁剪（非 ALL 角色越权机构不进查询）、SELF 仅本人。
2. **编辑流程**：我的方案 → 编辑 → 改指标/对象/维度（切维度弹确认并清空）→ 保存 → 列表刷新 → 再「载入」确认改动已落库（重点验证"改名/改指标/改对象"这次是真持久化，非 mock 假成功）。
3. **对象留空保存** → 载入后主页面按"查全部"执行。

- [ ] **Step 3: 记录回归结果**（在本 plan 勾选或附注）。无需提交（纯验证）。

---

## Self-Review 记录

- **Spec 覆盖**：§3.1 五处改动 → Task1(api)/Task2+3(SubjectPicker 抽取)/Task4(SchemeEditDialog)/Task5(SchemeListDialog)；§5 决策(维度可改/对象可空/乐观锁/纯重构) → Task4 测试覆盖维度切换清空、对象可空、expectedVersion；Task3 覆盖抽取；§7 测试 → 各任务测试 + Task6 手动回归。无遗漏。
- **占位扫描**：无 TBD/TODO；抽取任务以 `Dynamic.vue` 精确行号为准搬移（源码即真相）。
- **类型一致**：`updateSavedQuery(id,{...,expectedVersion})`、`SubjectPicker` v-model(对象数组)/`:dim`、`MetricPicker` v-model(code数组)/`:dim`、`SchemeEditDialog` props `{visible,scheme}` emits `{update:visible,saved}` — 跨任务一致。
