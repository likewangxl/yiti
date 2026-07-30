# 业务标签机构多选与运行版本对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将业务标签“新增成员”的机构条件改为按名称搜索的多选下拉，并通过全量安装和重启让已有的机构名称导入、正确模板及员工姓名回显进入当前运行环境。

**Architecture:** 前端复用 `getOrgTree()`，把机构树按 `deptNo` 扁平化、过滤空编号并去重；下拉展示名称但继续向现有新增成员接口提交 `orgDeptNos`，因此后端录入契约和数据库存储不变。完成前端改动后，从仓库根目录安装全部 Maven 模块并重启 `bootstrap`，消除当前运行环境加载 2026-07-24 旧 JAR 的问题。

**Tech Stack:** Vue 3、Element Plus、Vitest、Spring Boot 3.2.3、Maven、JDK 17

## Global Constraints

- 不修改 `PERSON_TAG_REL` 表结构，不冗余保存机构名称。
- 不改变新增成员接口的 `orgDeptNos` 请求字段。
- 不改变员工成员仍按工号批量输入的规则。
- 不改变“修改机构”弹窗及其 `orgDeptNo` 更新接口。
- 不新增机构搜索接口；复用 `GET /api/orgs/tree`。
- 没有 `deptNo` 的机构不进入候选项；候选项按 `deptNo` 保序去重。
- 机构列表仅在新增弹窗首次打开时按需加载；失败不缓存，后续打开可重试。
- 只有全量安装成功后才重启当前本地后端。

---

### Task 1: 新增成员机构名称多选下拉

**Files:**
- Modify: `xanzc_frontend/src/views/system/PersonTags.vue:154-178`
- Modify: `xanzc_frontend/src/views/system/PersonTags.vue:252-263`
- Modify: `xanzc_frontend/src/views/system/PersonTags.vue:493-521`
- Test: `xanzc_frontend/src/views/system/__tests__/PersonTags.spec.js`

**Interfaces:**
- Consumes: `getOrgTree(): Promise<Array<{code: string, name: string, deptNo?: string, children?: Array}>>` from `xanzc_frontend/src/api/orgs.js`
- Produces: `memberOrgOptions: Ref<Array<{deptNo: string, name: string, code: string}>>`
- Produces: `memberAdd.orgDeptNos: string[]`
- Preserves: `addPersonTagMembers(tagId, { usernames: string[], orgDeptNos: string[] }): Promise<number>`

- [ ] **Step 1: 更新机构 API mock，并写“按需加载、扁平化、过滤、去重和缓存成功结果”的失败测试**

在 `PersonTags.spec.js` 的模块 mock 区域加入完整机构树 fixture：

```js
vi.mock('@/api/orgs', () => ({
  getOrgTree: vi.fn().mockResolvedValue([
    {
      code: 'ROOT',
      name: '总行',
      children: [
        { code: 'ORG_A', name: '城东支行', deptNo: '0101' },
        { code: 'ORG_A_DUP', name: '城东支行重复记录', deptNo: '0101' },
        { code: 'ORG_B', name: '城西支行', deptNo: '0102' },
        { code: 'ORG_EMPTY', name: '未配置机构号' }
      ]
    }
  ])
}));
```

在 import 区域加入：

```js
import { ElMessage, ElMessageBox } from 'element-plus';
import { getOrgTree } from '@/api/orgs';
```

将现有 `element-plus` mock 中的 `ElMessage` 通过上述 import 取出，并在 `stubs` 中加入：

```js
'el-select': {
  name: 'ElSelect',
  props: ['modelValue', 'multiple', 'filterable', 'loading', 'placeholder'],
  template: '<div class="select-stub"><slot /></div>'
},
'el-option': {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<div />'
},
```

新增测试：

```js
it('新增成员：首次打开时按需加载机构名称选项，过滤空 deptNo、按 deptNo 去重并缓存成功结果', async () => {
  const wrapper = mountPage();
  await flushPromises();

  wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
  await flushPromises();
  wrapper.vm.openMemberAdd();
  await flushPromises();

  expect(getOrgTree).toHaveBeenCalledTimes(1);
  expect(wrapper.vm.memberOrgOptions).toEqual([
    { deptNo: '0101', name: '城东支行', code: 'ORG_A' },
    { deptNo: '0102', name: '城西支行', code: 'ORG_B' }
  ]);
  const select = wrapper.findComponent({ name: 'ElSelect' });
  expect(select.props()).toMatchObject({
    multiple: true,
    filterable: true,
    placeholder: '按机构名称搜索并选择（可留空）'
  });
  expect(wrapper.findAllComponents({ name: 'ElOption' }).map(option => ({
    label: option.props('label'),
    value: option.props('value')
  }))).toEqual([
    { label: '城东支行（0101）', value: '0101' },
    { label: '城西支行（0102）', value: '0102' }
  ]);

  wrapper.vm.memberAdd.visible = false;
  wrapper.vm.openMemberAdd();
  await flushPromises();
  expect(getOrgTree).toHaveBeenCalledTimes(1);
});
```

该测试防止以下生产缺陷：仍显示编号文本框、机构树未加载、父子节点未展开、空 `deptNo` 被提交、同一 `deptNo` 出现重复选项，以及每次打开弹窗重复请求。

- [ ] **Step 2: 运行新增测试并确认 RED**

Run:

```bash
cd xanzc_frontend
npx vitest run src/views/system/__tests__/PersonTags.spec.js
```

Expected: FAIL，原因是 `PersonTags.vue` 尚未导入/调用 `getOrgTree`，且没有 `memberOrgOptions`。

- [ ] **Step 3: 写“多选值继续按 orgDeptNos 提交”的失败测试**

用下面的测试替换原“机构编号两个输入框”用例：

```js
it('新增成员：员工工号与机构名称多选按 { usernames, orgDeptNos } 提交', async () => {
  const wrapper = mountPage();
  await flushPromises();

  wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
  await flushPromises();
  wrapper.vm.openMemberAdd();
  await flushPromises();
  wrapper.vm.memberAdd.empText = ' 100001 ，100002\n100003 ';
  wrapper.vm.memberAdd.orgDeptNos = ['0101', '0102'];

  await wrapper.vm.saveMemberAdd();
  await flushPromises();

  expect(addPersonTagMembers).toHaveBeenCalledWith(7, {
    usernames: ['100001', '100002', '100003'],
    orgDeptNos: ['0101', '0102']
  });
});
```

同时把空值测试的机构赋值改为：

```js
wrapper.vm.memberAdd.orgDeptNos = [];
```

该测试防止界面虽然展示名称，但错误地把机构名称或旧 `orgText` 发送给后端。

- [ ] **Step 4: 再次运行测试并确认 RED**

Run:

```bash
cd xanzc_frontend
npx vitest run src/views/system/__tests__/PersonTags.spec.js
```

Expected: FAIL，现有 `saveMemberAdd()` 仍读取 `memberAdd.orgText`。

- [ ] **Step 5: 写机构选项加载与缓存的最小实现**

在 `PersonTags.vue` 的 import 区域加入：

```js
import { getOrgTree } from '@/api/orgs';
```

将成员新增状态和加载函数改为：

```js
const memberAdd = reactive({
  visible: false,
  empText: '',
  orgDeptNos: [],
  orgLoading: false,
  saving: false
});
const memberOrgOptions = ref([]);
const memberOrgLoaded = ref(false);

function flattenMemberOrgOptions(nodes) {
  const options = [];
  const seenDeptNos = new Set();
  const walk = (items) => {
    for (const node of items || []) {
      const deptNo = node?.deptNo == null ? '' : String(node.deptNo).trim();
      if (deptNo && !seenDeptNos.has(deptNo)) {
        seenDeptNos.add(deptNo);
        options.push({
          deptNo,
          name: node.name || deptNo,
          code: node.code || ''
        });
      }
      if (node?.children?.length) walk(node.children);
    }
  };
  walk(nodes);
  return options;
}

async function ensureMemberOrgOptions() {
  if (memberOrgLoaded.value || memberAdd.orgLoading) return;
  memberAdd.orgLoading = true;
  try {
    memberOrgOptions.value = flattenMemberOrgOptions(await getOrgTree());
    memberOrgLoaded.value = true;
  } catch {
    memberOrgOptions.value = [];
    ElMessage.warning('机构列表加载失败，请重试');
  } finally {
    memberAdd.orgLoading = false;
  }
}

function openMemberAdd() {
  memberAdd.empText = '';
  memberAdd.orgDeptNos = [];
  memberAdd.visible = true;
  ensureMemberOrgOptions();
}
```

- [ ] **Step 6: 将机构编号文本框替换为机构名称多选下拉**

用以下模板替换原机构编号 `el-input`：

```vue
<el-form-item label="机构名称">
  <el-select
    v-model="memberAdd.orgDeptNos"
    multiple
    filterable
    clearable
    collapse-tags
    collapse-tags-tooltip
    :loading="memberAdd.orgLoading"
    :disabled="memberAdd.orgLoading"
    placeholder="按机构名称搜索并选择（可留空）"
    style="width: 100%">
    <el-option
      v-for="org in memberOrgOptions"
      :key="org.deptNo"
      :label="`${org.name}（${org.deptNo}）`"
      :value="org.deptNo" />
  </el-select>
</el-form-item>
```

将说明文案改为：

```vue
工号须存在于用户管理；机构请按名称搜索选择。已在标签下的同维度成员自动跳过，员工与机构至少填写一类。
```

- [ ] **Step 7: 修改保存逻辑，直接提交多选的 deptNo**

将 `saveMemberAdd()` 中的机构值读取与空值文案改为：

```js
const usernames = splitIds(memberAdd.empText);
const orgDeptNos = [...memberAdd.orgDeptNos];
if (!usernames.length && !orgDeptNos.length) {
  ElMessage.warning('请至少输入一个员工工号或选择一个机构');
  return;
}
```

保留现有 `addPersonTagMembers(detail.tag.tagId, { usernames, orgDeptNos })`、成功计数、刷新和异常处理逻辑。

- [ ] **Step 8: 写加载失败可重试且不阻止员工单独提交的失败保护测试**

在 `PersonTags.spec.js` 增加：

```js
it('新增成员：机构列表加载失败可重试，且仍可只提交员工', async () => {
  getOrgTree
    .mockRejectedValueOnce(new Error('load failed'))
    .mockResolvedValueOnce([
      { code: 'ORG_A', name: '城东支行', deptNo: '0101' }
    ]);
  const wrapper = mountPage();
  await flushPromises();

  wrapper.vm.openDetail({ tagId: 7, tagName: '骨干', memberCount: 1 });
  await flushPromises();
  wrapper.vm.openMemberAdd();
  await flushPromises();

  expect(ElMessage.warning).toHaveBeenCalledWith('机构列表加载失败，请重试');
  wrapper.vm.memberAdd.empText = '100001';
  await wrapper.vm.saveMemberAdd();
  expect(addPersonTagMembers).toHaveBeenCalledWith(7, {
    usernames: ['100001'],
    orgDeptNos: []
  });

  wrapper.vm.memberAdd.visible = false;
  wrapper.vm.openMemberAdd();
  await flushPromises();
  expect(getOrgTree).toHaveBeenCalledTimes(2);
  expect(wrapper.vm.memberOrgOptions).toEqual([
    { deptNo: '0101', name: '城东支行', code: 'ORG_A' }
  ]);
});
```

该测试防止失败请求被错误缓存，以及机构依赖异常阻断员工成员新增。

- [ ] **Step 9: 运行前端测试并确认 GREEN**

Run:

```bash
cd xanzc_frontend
npx vitest run src/views/system/__tests__/PersonTags.spec.js
npm run build
```

Expected: `PersonTags.spec.js` 全部 PASS，Vite 构建成功。

- [ ] **Step 10: 检查差异并提交前端改动**

Run:

```bash
git diff --check
git diff -- xanzc_frontend/src/views/system/PersonTags.vue \
  xanzc_frontend/src/views/system/__tests__/PersonTags.spec.js
git add xanzc_frontend/src/views/system/PersonTags.vue \
  xanzc_frontend/src/views/system/__tests__/PersonTags.spec.js
git commit -m "feat(fe): 业务标签新增成员按机构名称多选"
```

Expected: 只提交组件和对应测试，后端 DTO、Service、Mapper 均无改动。

### Task 2: 验证已有后端修复并刷新当前运行环境

**Files:**
- Verify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AdminPersonTagController.java`
- Verify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/PersonTagImportService.java`
- Verify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/PersonTagService.java`
- Verify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/facade/UserFacade.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/controller/AdminPersonTagControllerTest.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/PersonTagImportServiceTest.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/PersonTagServiceTest.java`
- Test: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/facade/UserFacadeBatchQueryNPlusOneTest.java`

**Interfaces:**
- Verifies: ORG Excel rows expose `orgName` and resolve through `OrgApi.getOrgsByNames(Collection<String>)`
- Verifies: employee member responses expose `displayName`
- Produces: updated local Maven artifacts consumed by `bootstrap`
- Preserves: `POST /api/admin/sys/person-tags/{tagId}/members` with `orgDeptNos`

- [ ] **Step 1: 运行当前源码的后端回归测试**

Run from repository root:

```bash
mvn -pl system-governance-center -am test \
  -DskipTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=AdminPersonTagControllerTest,PersonTagImportServiceTest,PersonTagServiceTest,UserFacadeBatchQueryNPlusOneTest
```

Expected: 聚合 53 项 PASS，其中 governance 48 项、auth 5 项；覆盖机构名称模板、按名称唯一解析、员工姓名批量补全和 JSON 字段。

- [ ] **Step 2: 全量安装最新模块到本地 Maven 仓库**

Run:

```bash
mvn clean install -DskipTests
```

Expected: Reactor `BUILD SUCCESS`，`auth-permission-center` 和 `system-governance-center` 的 `1.0.0-SNAPSHOT.jar` 时间更新到当前时间。

- [ ] **Step 3: 检查安装后的 JAR 确实包含新契约**

Run:

```bash
javap -classpath "/home/djdev/.m2/repository/com/bank/branch/platform/system-governance-center/1.0.0-SNAPSHOT/system-governance-center-1.0.0-SNAPSHOT.jar" \
  com.bank.branch.platform.governance.api.dto.PersonTagOrgImportRow \
  com.bank.branch.platform.governance.api.dto.PersonTagOrgMemberImportRow \
  com.bank.branch.platform.governance.api.dto.PersonTagMemberRespDTO
```

Expected:

```text
PersonTagOrgImportRow: getOrgName()/setOrgName(...)
PersonTagOrgMemberImportRow: getOrgName()/setOrgName(...)
PersonTagMemberRespDTO: getDisplayName()/setDisplayName(...)
```

- [ ] **Step 4: 重启当前本地 `bootstrap` 后端**

先确认当前 tmux 会话：

```bash
tmux has-session -t yiti-backend
```

Expected: exit code `0`。

重启：

```bash
tmux kill-session -t yiti-backend
tmux new-session -d -s yiti-backend -c /home/djdev/lijh/yiti/bootstrap \
  'mvn spring-boot:run 2>&1 | tee /tmp/yiti-backend.log'
```

- [ ] **Step 5: 等待服务启动并检查新进程加载的 JAR**

Run:

```bash
for i in $(seq 1 60); do
  if rg -q 'Started BranchPlatformApplication' /tmp/yiti-backend.log; then
    break
  fi
  sleep 1
done
rg -n 'Started BranchPlatformApplication|APPLICATION FAILED TO START' /tmp/yiti-backend.log
ss -ltnp | rg ':18080'
```

Expected: 日志包含 `Started BranchPlatformApplication`，不包含 `APPLICATION FAILED TO START`，端口 `18080` 有新的 Java 进程监听。

- [ ] **Step 6: 最终验证并确认没有额外源码变更**

Run:

```bash
cd xanzc_frontend
npx vitest run src/views/system/__tests__/PersonTags.spec.js
cd ..
git diff --check
git status --short --branch
```

Expected: 前端目标测试 PASS；工作区干净；分支只领先设计文档、实施计划和前端实现提交。运行版本刷新不生成额外 Git 提交。
