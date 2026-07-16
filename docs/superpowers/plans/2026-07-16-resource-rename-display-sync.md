# 资源/菜单改名同步页面显示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让顶部面包屑与页面 H1 标题在运行时从 DB 菜单名（my-menus）解析，改资源名后即时同步，`route.meta.title` 退化为兜底。

**Architecture:** 新增共享 Pinia 菜单 store 缓存 my-menus 树并按 `route.path` 匹配 `resourceUrl` 解析 `menuName`（父节点作分组）。面包屑改读 store；新增全局 `<PageTitle/>` 组件替换各页写死的 `<h1>`；侧边栏改用同一 store；资源改名成功后强制刷新 store 即时生效。纯前端改动，不动后端。

**Tech Stack:** Vue 3 (`<script setup>`) + Pinia（组合式 store）+ vue-router + Element Plus；测试 vitest 1.6 + @vue/test-utils + happy-dom。

## Global Constraints

- 所有注释用中文，文件 UTF-8。
- Pinia store 用组合式写法 `defineStore('name', () => {...})`，与 `src/stores/user.js` 一致。
- 前端测试放 `__tests__/`，首行 `// @vitest-environment happy-dom`（纯逻辑用 node 环境即可），mock 用 `vi.mock`，异步等待用 `flushPromises`/`await`。
- 全局组件用 `app.component()` 注册（项目 vite.config 未启用 unplugin 自动导入）。
- 不新增后端接口 / 字段；`my-menus` 已返回 `{ resourceId, resourceUrl, menuName, children }`。
- 不动 `router/index.js` 的 `meta.title`（保留为兜底）。
- 运行测试：`npm test`（vitest run）。dev 依赖已安装。
- 若派遣 subagent，`model` 必须 ≥ sonnet。

---

## File Structure

- **新增** `src/stores/menu.js` — 菜单树缓存 + `resolve(path)` 名称解析（唯一显示真源）。
- **新增** `src/components/PageTitle.vue` — 动态页面标题 `<h1>`，全局注册，支持 `title` 覆盖 + 默认插槽（副标题）。
- **改** `src/components/AppBreadcrumb.vue` — 用 store 解析，`meta` 兜底。
- **改** `src/components/AppSidebar.vue` — 改消费 store（去本地重复 fetch）。
- **改** `src/views/system/Resources.vue` — 资源增删改成功后 `menuStore.load(true)`。
- **改** `src/main.js` — 全局注册 `PageTitle`。
- **批量改** 约 46 个 `src/views/**/*.vue` — 静态 H1 换 `<PageTitle/>`（含 SUB 用插槽保留副标题）。
- **新增测试** `src/stores/__tests__/menu.spec.js`、`src/components/__tests__/PageTitle.spec.js`、`src/components/__tests__/AppBreadcrumb.spec.js`。

> 全部路径相对 `/home/djdev/lijh/yiti/xanzc_frontend/`，命令在该目录下执行。

---

## Task 1: 菜单 store（stores/menu.js）

**Files:**
- Create: `xanzc_frontend/src/stores/menu.js`
- Test: `xanzc_frontend/src/stores/__tests__/menu.spec.js`

**Interfaces:**
- Consumes: `getMyMenus()` from `@/api/auth` → `Array<{ resourceId, resourceUrl, menuName, children }>`。
- Produces: `useMenuStore()` → `{ tree: Ref<Array>, loaded: Ref<bool>, loading: Ref<bool>, load(force?: boolean): Promise<void>, resolve(path: string): { title: string, group: string|null } | null }`。

- [ ] **Step 1: 写失败测试**

Create `xanzc_frontend/src/stores/__tests__/menu.spec.js`:

```js
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('@/api/auth', () => ({ getMyMenus: vi.fn() }));
import { getMyMenus } from '@/api/auth';
import { useMenuStore } from '../menu';

// 两层树：报表分析 > 数据公式(原自由报表)；工作台为顶层叶子
const TREE = [
  { resourceId: 1, resourceUrl: '/report', menuName: '报表分析', children: [
    { resourceId: 2, resourceUrl: '/report/free', menuName: '数据公式', children: [] }
  ]},
  { resourceId: 9, resourceUrl: '/workspace', menuName: '工作台', children: [] }
];

beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks(); });

describe('menu store', () => {
  it('load 后 resolve 子节点返回 menuName + 父分组', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    expect(s.resolve('/report/free')).toEqual({ title: '数据公式', group: '报表分析' });
  });

  it('顶层叶子 resolve 的 group 为 null', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    expect(s.resolve('/workspace')).toEqual({ title: '工作台', group: null });
  });

  it('未命中路径 resolve 返回 null', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    expect(s.resolve('/nope')).toBeNull();
  });

  it('load 幂等：第二次不再请求，force 才重取', async () => {
    getMyMenus.mockResolvedValue(TREE);
    const s = useMenuStore();
    await s.load();
    await s.load();
    expect(getMyMenus).toHaveBeenCalledTimes(1);
    await s.load(true);
    expect(getMyMenus).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- src/stores/__tests__/menu.spec.js`
Expected: FAIL（`Cannot find module '../menu'` / `useMenuStore is not a function`）

- [ ] **Step 3: 写最简实现**

Create `xanzc_frontend/src/stores/menu.js`:

```js
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { getMyMenus } from '@/api/auth';

/**
 * 菜单 store：应用内缓存 my-menus 菜单树，提供按路由 path 解析显示名。
 * 让侧边栏 / 面包屑 / 页面标题统一以 DB menuName 为显示真源。
 */
export const useMenuStore = defineStore('menu', () => {
  const tree = ref([]);              // 原始菜单树
  const loaded = ref(false);
  const loading = ref(false);
  const byUrl = ref(new Map());      // resourceUrl -> { title, group }

  // 递归遍历构建索引；group 取直接父节点 menuName（顶层为 null）
  function buildIndex(nodes, parentName, map) {
    for (const n of nodes || []) {
      if (n.resourceUrl) {
        map.set(n.resourceUrl, { title: n.menuName || '', group: parentName || null });
      }
      if (n.children && n.children.length) {
        buildIndex(n.children, n.menuName || parentName || null, map);
      }
    }
  }

  // 拉取菜单树并建索引；loaded 后默认跳过，force=true 强制重取（资源改名后即时刷新）
  async function load(force = false) {
    if (loaded.value && !force) return;
    if (loading.value) return;
    loading.value = true;
    try {
      const t = await getMyMenus();
      tree.value = Array.isArray(t) ? t : [];
      const map = new Map();
      buildIndex(tree.value, null, map);
      byUrl.value = map;
      loaded.value = true;
    } catch {
      // 保持原值；首次失败 loaded 仍 false，后续可重试
    } finally {
      loading.value = false;
    }
  }

  // 命中返回 { title, group }，未命中返回 null
  function resolve(path) {
    return byUrl.value.get(path) || null;
  }

  return { tree, loaded, loading, load, resolve };
});
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm test -- src/stores/__tests__/menu.spec.js`
Expected: PASS（4 passed）

- [ ] **Step 5: 提交**

```bash
git add xanzc_frontend/src/stores/menu.js xanzc_frontend/src/stores/__tests__/menu.spec.js
git commit -m "feat(fe): 新增菜单 store 缓存 my-menus + 按路由解析菜单名

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: PageTitle 组件 + 全局注册

**Files:**
- Create: `xanzc_frontend/src/components/PageTitle.vue`
- Modify: `xanzc_frontend/src/main.js`
- Test: `xanzc_frontend/src/components/__tests__/PageTitle.spec.js`

**Interfaces:**
- Consumes: `useMenuStore()`（Task 1）的 `load` / `resolve`；`useRoute()`。
- Produces: 全局组件 `<PageTitle :title?="string">…副标题插槽…</PageTitle>`，渲染根节点 `<h1 class="page-title">`。显示优先级：`props.title` > DB 菜单名 > `route.matched[last].meta.title`。

- [ ] **Step 1: 写失败测试**

Create `xanzc_frontend/src/components/__tests__/PageTitle.spec.js`:

```js
// @vitest-environment happy-dom
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const routeRef = { path: '/report/free', matched: [{ meta: { title: '自由报表' } }] };
vi.mock('vue-router', () => ({ useRoute: () => routeRef }));

let resolveImpl = () => null;
vi.mock('@/stores/menu', () => ({
  useMenuStore: () => ({ load: vi.fn(), resolve: (p) => resolveImpl(p) })
}));

import PageTitle from '../PageTitle.vue';

describe('PageTitle.vue', () => {
  it('显示 DB 菜单名（优先于 meta.title）', () => {
    resolveImpl = () => ({ title: '数据公式', group: '报表分析' });
    const w = mount(PageTitle);
    expect(w.find('h1').text()).toBe('数据公式');
  });

  it('无菜单命中时退回 meta.title', () => {
    resolveImpl = () => null;
    const w = mount(PageTitle);
    expect(w.find('h1').text()).toBe('自由报表');
  });

  it('title 属性覆盖一切', () => {
    resolveImpl = () => ({ title: '数据公式', group: null });
    const w = mount(PageTitle, { props: { title: '报表详情' } });
    expect(w.find('h1').text()).toBe('报表详情');
  });

  it('默认插槽渲染副标题、且保留菜单名', () => {
    resolveImpl = () => ({ title: '菜单管理', group: null });
    const w = mount(PageTitle, { slots: { default: '<span class="sub">副标题</span>' } });
    expect(w.find('h1 .sub').text()).toBe('副标题');
    expect(w.find('h1').text()).toContain('菜单管理');
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- src/components/__tests__/PageTitle.spec.js`
Expected: FAIL（找不到 `../PageTitle.vue`）

- [ ] **Step 3: 写组件实现**

Create `xanzc_frontend/src/components/PageTitle.vue`:

```vue
<template>
  <!-- 根节点为 <h1>：DOM 结构与原页面一致，各页 .page-h h1 的 scoped 样式经父作用域仍作用于此根节点 -->
  <h1 class="page-title">{{ display }}<slot /></h1>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/menu';

// title：页面显式覆盖（动态/记录级标题用）
const props = defineProps({
  title: { type: String, default: '' }
});

const route = useRoute();
const menuStore = useMenuStore();
onMounted(() => menuStore.load());

// 优先级：显式覆盖 > DB 菜单名 > 静态 meta.title
const display = computed(() => {
  if (props.title) return props.title;
  const db = menuStore.resolve(route.path);
  if (db) return db.title;
  const matched = route.matched[route.matched.length - 1];
  return matched?.meta?.title || '';
});
</script>
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm test -- src/components/__tests__/PageTitle.spec.js`
Expected: PASS（4 passed）

- [ ] **Step 5: 全局注册 PageTitle**

Modify `xanzc_frontend/src/main.js` — 加入 import 与注册（在 `app.mount` 前）:

```js
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/dist/locale/zh-cn.mjs';

import App from './App.vue';
import router from './router';
import PageTitle from './components/PageTitle.vue';
import './styles/index.scss';

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.use(ElementPlus, { locale: zhCn });
app.component('PageTitle', PageTitle);
app.mount('#app');
```

- [ ] **Step 6: 跑全量测试确认无回归**

Run: `npm test`
Expected: 全部 PASS（含既有 + 新增用例）

- [ ] **Step 7: 提交**

```bash
git add xanzc_frontend/src/components/PageTitle.vue xanzc_frontend/src/components/__tests__/PageTitle.spec.js xanzc_frontend/src/main.js
git commit -m "feat(fe): 新增 PageTitle 动态标题组件并全局注册

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: 面包屑改为动态解析（AppBreadcrumb.vue）

**Files:**
- Modify: `xanzc_frontend/src/components/AppBreadcrumb.vue:11-21`
- Test: `xanzc_frontend/src/components/__tests__/AppBreadcrumb.spec.js`

**Interfaces:**
- Consumes: `useMenuStore()` 的 `load` / `resolve`；`useRoute()`。
- Produces: 无对外接口变化，模板 `items` 语义不变（命中菜单用 DB 名+分组，否则退回 `meta.title`/`meta.group`）。

- [ ] **Step 1: 写失败测试**

Create `xanzc_frontend/src/components/__tests__/AppBreadcrumb.spec.js`:

```js
// @vitest-environment happy-dom
import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const routeRef = { path: '/report/free', matched: [{ meta: { title: '自由报表', group: '报表分析' } }] };
vi.mock('vue-router', () => ({ useRoute: () => routeRef }));

let resolveImpl = () => null;
vi.mock('@/stores/menu', () => ({
  useMenuStore: () => ({ load: vi.fn(), resolve: (p) => resolveImpl(p) })
}));

import AppBreadcrumb from '../AppBreadcrumb.vue';

describe('AppBreadcrumb.vue', () => {
  it('命中菜单：用 DB 名 + 分组，且不再显示旧静态名', () => {
    resolveImpl = () => ({ title: '数据公式', group: '报表分析' });
    const w = mount(AppBreadcrumb);
    const text = w.text();
    expect(text).toContain('数据公式');
    expect(text).toContain('报表分析');
    expect(text).not.toContain('自由报表');
  });

  it('未命中菜单：退回 meta.title / meta.group', () => {
    resolveImpl = () => null;
    const w = mount(AppBreadcrumb);
    const text = w.text();
    expect(text).toContain('自由报表');
    expect(text).toContain('报表分析');
  });
});
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- src/components/__tests__/AppBreadcrumb.spec.js`
Expected: FAIL（当前组件只读 `meta.title`，第 1 个用例断言 `不含 自由报表` 失败）

- [ ] **Step 3: 改造组件**

Replace the `<script setup>` block of `xanzc_frontend/src/components/AppBreadcrumb.vue`（模板与样式不变，仅替换脚本）:

```js
<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/menu';

const route = useRoute();
const menuStore = useMenuStore();
onMounted(() => menuStore.load());

const items = computed(() => {
  const matched = route.matched[route.matched.length - 1];
  // 命中菜单 → 用 DB 名 + 分组（分组可能为 null）；未命中 → 整体退回静态 meta
  const db = menuStore.resolve(route.path);
  const title = db ? db.title : matched?.meta?.title;
  const group = db ? db.group : matched?.meta?.group;
  if (!title) return [];
  return group ? [group, title] : [title];
});
</script>
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm test -- src/components/__tests__/AppBreadcrumb.spec.js`
Expected: PASS（2 passed）

- [ ] **Step 5: 提交**

```bash
git add xanzc_frontend/src/components/AppBreadcrumb.vue xanzc_frontend/src/components/__tests__/AppBreadcrumb.spec.js
git commit -m "feat(fe): 面包屑改为按 DB 菜单名解析，meta.title 退化为兜底

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 侧边栏改用共享 store（AppSidebar.vue）

**Files:**
- Modify: `xanzc_frontend/src/components/AppSidebar.vue:43-72`（`<script setup>` 部分）

**Interfaces:**
- Consumes: `useMenuStore()` 的 `tree` / `loading` / `load`。
- Produces: 无对外接口变化；模板仍用 `menus` / `loading` / `openMap` / `toggle`，行为不变。

> 目的：三处（侧边栏/面包屑/标题）共用一份菜单树；Task 5 资源改名 `load(true)` 时侧边栏也即时更新。

- [ ] **Step 1: 改造脚本**

Replace the `<script setup>` block of `xanzc_frontend/src/components/AppSidebar.vue`（模板 template 与 style 不变）:

```js
<script setup>
import { reactive, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/menu';

const route = useRoute();
const menuStore = useMenuStore();
// 直接消费共享 store 的菜单树；与面包屑/PageTitle 同源，改名 force 刷新后一并更新
const menus = computed(() => menuStore.tree);
const loading = computed(() => menuStore.loading);
const openMap = reactive({});

// 默认展开全部分组节点（按 resourceId）；tree 变化（首次加载/改名刷新）后重建展开态
function initOpen() {
  for (const m of menus.value) {
    if (m.children && m.children.length) openMap[m.resourceId] = true;
  }
}
onMounted(() => menuStore.load());
watch(() => menuStore.tree, initOpen, { immediate: true });

function toggle(id) { openMap[id] = !openMap[id]; }
</script>
```

- [ ] **Step 2: 跑全量测试确认无回归**

Run: `npm test`
Expected: 全部 PASS

- [ ] **Step 3: 手工冒烟（可选，需起前端）**

若已起 `npm run dev`：登录后确认左侧菜单正常渲染、分组默认展开、当前项高亮不变。无法起服务则以 Step 2 全绿 + Step 4 代码审查为准。

- [ ] **Step 4: 提交**

```bash
git add xanzc_frontend/src/components/AppSidebar.vue
git commit -m "refactor(fe): 侧边栏改用共享菜单 store，去除本地重复 fetch

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 资源改名后即时刷新（Resources.vue）

**Files:**
- Modify: `xanzc_frontend/src/views/system/Resources.vue`（`<script setup>` 顶部加 import + store；`saveDlg`/删除成功处调 `load(true)`）

**Interfaces:**
- Consumes: `useMenuStore()` 的 `load`。
- Produces: 资源创建/更新/删除成功后强制刷新菜单 store → 当前会话侧边栏/面包屑/当前页 H1 即时更新。

> 说明：菜单 store 应用启动只加载一次；不强制刷新时，管理员在同一会话改名后仍看到旧名（复现原问题）。故改名成功须 `load(true)`。

- [ ] **Step 1: 读取定位**

Run: `grep -n "useMenuStore\|import.*stores\|ElMessage.success('已更新')\|已创建\|已删除\|async function saveDlg\|async function del\|removeResource\|deleteResource" xanzc_frontend/src/views/system/Resources.vue`
目的：确认 `saveDlg`（更新/创建成功 `ElMessage.success('已更新'/'已创建')，约 300-308 行）与删除成功（`ElMessage.success('已删除')`，约 320 行）位置。

- [ ] **Step 2: 加 import + store 实例**

在 `Resources.vue` 的 `<script setup>` 内、其它 import 之后加入：

```js
import { useMenuStore } from '@/stores/menu';
```

并在 setup 顶部（其它 `ref`/`reactive` 声明附近）加：

```js
// 资源改名/增删后强制刷新菜单树，使侧边栏/面包屑/页面标题即时同步
const menuStore = useMenuStore();
```

- [ ] **Step 3: 在成功回调追加强制刷新**

在 `saveDlg` 更新成功分支（`ElMessage.success('已更新')` 之后）与创建成功分支（`ElMessage.success('已创建')` 之后）各追加一行；删除成功（`ElMessage.success('已删除')` 之后）同样追加：

```js
      menuStore.load(true);
```

（三处都加；`load(true)` 幂等安全，失败静默不影响主流程。）

- [ ] **Step 4: 跑全量测试确认无回归**

Run: `npm test`
Expected: 全部 PASS

- [ ] **Step 5: 手工验证（可选，需起前端）**

登录为系统管理员 → 系统设置/资源-菜单 → 改某菜单名并保存 → 不刷新页面，观察左侧菜单名即时变化；进入该页面顶部面包屑与 H1 同步为新名。无法起服务则以代码审查 + Task 6 完成后整体验证为准。

- [ ] **Step 6: 提交**

```bash
git add xanzc_frontend/src/views/system/Resources.vue
git commit -m "feat(fe): 资源增删改成功后强制刷新菜单 store，改名即时生效

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 批量替换静态页面 H1 为 <PageTitle/>

**Files（按类别，均为 `xanzc_frontend/src/` 下）:**

**A 类（STAT，简单静态标题）→ `<h1>菜单名</h1>` 替换为 `<PageTitle />`：**
- `views/eval/MyTasks.vue`、`views/eval/Rules.vue`、`views/eval/Tags.vue`、`views/eval/Tasks.vue`、`views/eval/UserTags.vue`
- `views/guarantee/DataImport.vue`、`views/guarantee/Notice.vue`、`views/guarantee/Query.vue`
- `views/history/PerfAdjustQuery.vue`、`views/history/PriceApproval.vue`
- `views/perf/KpiRules.vue`、`views/perf/KpiScoreDetail.vue`、`views/perf/Metrics.vue`、`views/perf/TargetValues.vue`
- `views/report/AmasApprovals.vue`、`views/report/Dashboard.vue`、`views/report/Dynamic.vue`、`views/report/FreeReport.vue`、`views/report/Presets.vue`、`views/report/Sql.vue`
- `views/screen/admin/Datasources.vue`
- `views/system/Announcements.vue`、`views/system/Audit.vue`、`views/system/Config.vue`、`views/system/Dict.vue`、`views/system/Files.vue`、`views/system/FlowEdit.vue`、`views/system/Jobs.vue`、`views/system/Permission.vue`、`views/system/TimeoutRules.vue`
- `views/workspace/AnnouncementList.vue`、`views/workspace/NotificationList.vue`

**B 类（SUB，H1 内含副标题 span）→ `<h1>菜单名 <span class="sub">…</span></h1>` 替换为 `<PageTitle><span class="sub">…</span></PageTitle>`（去掉菜单名字面量，副标题移入插槽）：**
- `views/info/AddressBook.vue`、`views/info/DocCenter.vue`、`views/info/NavHub.vue`、`views/info/ProductLib.vue`
- `views/perf/Adjust.vue`、`views/perf/Compute.vue`、`views/perf/Import.vue`、`views/perf/Targets.vue`、`views/perf/TaskMonitor.vue`
- `views/system/FlowList.vue`、`views/system/Resources.vue`、`views/system/Roles.vue`、`views/system/Users.vue`、`views/system/WorkflowMonitor.vue`

**排除（保持原样，不改）：**
- `views/login/Index.vue` —— 登录页不在应用外壳内、无菜单/面包屑，标题为品牌名。
- 动态/记录级 H1（`<h1>{{ … }}</h1>`）：`views/eval/RewardTask.vue`、`views/report/FreeReportDetail.vue`、`views/system/Calendar.vue`、`views/system/Notifications.vue`。
- 记录级详情页（H1 为记录标签、非菜单项）：`views/history/PriceApprovalDetail.vue`、`views/report/AmasApprovalDetail.vue`、`views/system/AnnouncementDetail.vue`。

**Interfaces:**
- Consumes: 全局组件 `<PageTitle/>`（Task 2，已全局注册，无需在页面 import）。
- Produces: 各页 `.page-h` 内的标题改为运行时解析，DOM 仍是 `<div class="page-h"><h1>…</h1>…</div>`。

- [ ] **Step 1: A 类逐页替换**

对 A 类每个文件，把 `.page-h` 内的静态 `<h1>菜单名</h1>` 整行替换为 `<PageTitle />`。示例 `views/report/FreeReport.vue`：

替换前：
```html
    <div class="page-h">
      <h1>自由报表</h1>
      <div class="actions">
```
替换后：
```html
    <div class="page-h">
      <PageTitle />
      <div class="actions">
```

（`<h1>` 内文字不用管，`<PageTitle/>` 会按当前路由解析菜单名，命中不了则退回该页 `meta.title`——即原文字。）

- [ ] **Step 2: B 类逐页替换（副标题进插槽）**

对 B 类每个文件，保留 `<span class="sub">…</span>` 原文，移入 `<PageTitle>` 默认插槽，去掉前面的菜单名字面量。示例 `views/system/Resources.vue`：

替换前：
```html
      <h1>菜单管理 <span class="sub">PT_RESOURCE.IS_MENU=1 的菜单节点；接口资源在「资源管理」单独维护</span></h1>
```
替换后：
```html
      <PageTitle><span class="sub">PT_RESOURCE.IS_MENU=1 的菜单节点；接口资源在「资源管理」单独维护</span></PageTitle>
```

（其余 B 类同理：菜单名字面量删除，`<span class="sub">…</span>` 原样放进 `<PageTitle>…</PageTitle>`。）

- [ ] **Step 3: 核对无遗漏 / 无误伤**

Run:
```bash
cd xanzc_frontend && grep -rn "<h1" src/views | grep -v node_modules
```
Expected: 仅剩「排除」清单里的 8 个文件（login + 4 个 DYN + 3 个 Detail）仍有 `<h1`；A/B 类文件不再出现 `<h1`（已换成 `<PageTitle`）。若 A/B 类仍有 `<h1` 说明漏改，补改。

- [ ] **Step 4: 跑全量测试确认无回归**

Run: `npm test`
Expected: 全部 PASS（无组件因删除 `<h1>` 报错；`Metrics.spec.js` 等既有用例仍绿）。

- [ ] **Step 5: 视觉抽查（可选，需起前端）**

若可起 `npm run dev`：打开一个 A 类页（如报表分析/自由报表）确认左上角大标题、按钮右对齐正常；打开一个 B 类页（如系统设置/菜单管理）确认标题 + 副标题排版正常。无法起服务则以 Step 3/4 为准（DOM 结构与 scoped 样式不变，风险低）。

- [ ] **Step 6: 提交**

```bash
git add xanzc_frontend/src/views
git commit -m "feat(fe): 全量替换页面静态 H1 为 PageTitle，随 DB 菜单名同步

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 收尾验证

- [ ] **全量测试**：`cd xanzc_frontend && npm test` → 全绿。
- [ ] **端到端手工（若可起服务）**：管理员改「自由报表」→「数据公式」并保存；不刷新即见侧边栏更新；进入该页，面包屑 `报表分析 / 数据公式`、左上角 H1 `数据公式` 均已同步。
- [ ] **回归**：其它页面标题/面包屑显示正常；排除清单页面（登录、详情、动态标题页）行为未变。

## 备注 / 风险

- **scoped 样式**：`<PageTitle>` 以 `<h1>` 为根，Vue 3 会把父组件 scoped 属性作用到子组件根节点，故各页 `.page-h h1`（含 `.page-h > h1`、`.page-h h1 .sub`）样式与 DOM 结构均不变。
- **加载前闪现**：store 未就绪时 PageTitle/面包屑先显示 `meta.title`（=改名前值），加载后仅被改名项切换，短暂且可接受。
- **排除清单**是唯一需要人工判断处；A/B 类替换纯机械。执行时严格按清单，避免误改登录页/详情页导致空标题。
