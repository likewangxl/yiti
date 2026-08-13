<!--
  对象选择器（从 Dynamic.vue 抽取的可复用组件）——「维度 → 机构树/搜索 → 已选对象」
  自包含：内部自行拉取机构树 getOrgTree() + 数据范围 getPickerScope(dim)，按范围裁剪机构树。
  v-model:visible 控制显隐；v-model 为已选对象数组 [{id,name,org}]；:dim 为 EMP/ORG/CUST。
  逻辑与原 Dynamic.vue 内联弹框逐字一致（数据范围裁剪/包含下级/分批加载/SELF仅本人/员工·客户搜索）。
-->
<template>
  <el-dialog
    class="bp-crud-dialog"
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="dim === 'EMP' ? '选择员工' : dim === 'CUST' ? '选择客户' : '选择机构'"
    width="720px" :close-on-click-modal="false" aria-label="选择报表查询对象"
  >
    <div class="subject-picker">
      <!-- 客户维度不用机构树，只用右侧搜索框 -->
      <div class="picker-left" v-if="dim !== 'CUST'">
        <div class="picker-title">机构树（勾选{{ dim === 'EMP' ? '机构可选该机构下全部员工' : '机构' }}）</div>
        <el-checkbox v-if="dim !== 'CUST'" v-model="subjectDlg.includeSubOrg" size="small" style="margin-bottom:8px"
                     @change="onIncludeSubOrgChange">包含下级机构</el-checkbox>
        <el-input v-model="subjectDlg.treeKw" placeholder="搜索机构名称" size="small" clearable style="margin-bottom:8px" />
        <el-tree
          ref="subjectTreeRef"
          :key="subjectDlg.openSeq"
          :data="subjectDlg.orgTree"
          show-checkbox
          :check-strictly="dim !== 'CUST'"
          node-key="code"
          default-expand-all
          :filter-node-method="filterOrgNode"
          :props="{ label: 'name', children: 'children' }"
          @check-change="onOrgCheckChange"
          style="max-height:360px;overflow:auto"
        />
        <p v-if="treeLoading" class="search-empty" role="status">正在加载机构树</p>
        <p v-else-if="treeError" class="search-empty error-text" role="alert">{{ treeError }}</p>
      </div>
      <div class="picker-right">
        <template v-if="dim === 'EMP'">
          <div class="picker-title">精确搜索员工</div>
          <el-input v-model="subjectDlg.empKw" placeholder="输入姓名或工号搜索" size="small" clearable
                    @keyup.enter="onEmpSearch" style="margin-bottom:8px">
            <template #append><el-button @click="onEmpSearch">搜索</el-button></template>
          </el-input>
          <div class="emp-results">
            <button v-for="e in subjectDlg.empSearchResults" :key="e.id" type="button" class="emp-row" :aria-label="`选择员工 ${e.name}`" @click="addSubjectFromSearch(e)">
              <span>{{ e.name }}</span>
              <span class="muted">{{ e.org }}</span>
            </button>
            <p v-if="subjectDlg.empKw && !subjectDlg.empSearchResults.length" class="search-empty">未找到匹配员工</p>
          </div>
        </template>
        <template v-else-if="dim === 'CUST'">
          <div class="picker-title">搜索客户</div>
          <el-input v-model="subjectDlg.custKw" placeholder="输入客户名或客户号搜索" size="small" clearable
                    @keyup.enter="onCustSearch" style="margin-bottom:8px">
            <template #append><el-button @click="onCustSearch">搜索</el-button></template>
          </el-input>
          <div class="emp-results">
            <button v-for="c in subjectDlg.custSearchResults" :key="c.id" type="button" class="emp-row" :aria-label="`选择客户 ${c.name}`" @click="addSubjectFromSearch(c)">
              <span>{{ c.name }}</span>
              <span class="muted">{{ c.org }}</span>
            </button>
            <p v-if="subjectDlg.custKw && !subjectDlg.custSearchResults.length" class="search-empty">未找到匹配客户</p>
          </div>
        </template>
        <div class="picker-title" style="margin-top:12px">已选 ({{ subjectDlg.selected.length }})</div>
        <div class="selected-list">
          <el-tag v-for="s in subjectDlg.selected" :key="s.id" closable effect="plain" size="small"
                  @close="subjectDlg.selected = subjectDlg.selected.filter(x => x.id !== s.id)"
                  style="margin:2px">
            {{ s.name }}
          </el-tag>
          <div v-if="!subjectDlg.selected.length" class="obj-empty">暂未选择</div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="confirmSubjects">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue';
import { getOrgTree, listOrgUsers } from '@/api/orgs';
import { getPickerScope, searchReportEmployees, searchReportCustomers } from '@/api/report';
import { filterTreeByCodes } from './subjectScope';

const props = defineProps({
  visible: Boolean,
  modelValue: { type: Array, default: () => [] },   // 已选对象 [{id,name,org}]
  dim: { type: String, default: 'EMP' }
});
const emit = defineEmits(['update:visible', 'update:modelValue']);

const subjectTreeRef = ref(null);
const orgTreeData = ref([]);
// 对象选择数据范围（按角色）：ALL 不限 / ORG_SUBTREE 本机构子树 / SELF 仅本人
const pickerScope = ref({ mode: 'ALL', selfEmpId: '', selfName: '', orgCodes: [] });
const treeLoading = ref(false);
const treeError = ref('');

function scopedOrgTree() {
  const sc = pickerScope.value;
  if (!sc || sc.mode === 'ALL') return orgTreeData.value;
  const set = new Set(sc.orgCodes || []);
  return filterTreeByCodes(orgTreeData.value, set);
}

const subjectDlg = reactive({
  orgTree: [],
  treeKw: '',
  empKw: '',
  empSearchResults: [],
  custKw: '',
  custSearchResults: [],
  selected: [],
  includeSubOrg: false,   // ORG 维度：勾选机构时是否连同其全部下级机构一并纳入
  openSeq: 0,   // 每次打开递增，给 el-tree 当 :key 强制重建，避免上次勾选残留
});

function filterOrgNode(value, data) {
  if (!value) return true;
  return (data.name || '').includes(value);
}

watch(() => subjectDlg.treeKw, (val) => {
  subjectTreeRef.value?.filter(val);
});

// 打开时用 modelValue 初始化已选 + 按范围裁剪机构树 + 清空搜索/开关残留
watch(() => props.visible, (visible) => {
  if (visible) {
    subjectDlg.selected = props.modelValue.map(s => ({ id: s.id, name: s.name, org: s.org || '' }));
    subjectDlg.orgTree = scopedOrgTree();
    subjectDlg.treeKw = '';
    subjectDlg.empKw = '';
    subjectDlg.empSearchResults = [];
    subjectDlg.custKw = '';
    subjectDlg.custSearchResults = [];
    subjectDlg.includeSubOrg = false;   // 每次打开默认「仅本级」，避免上次开关状态残留
    subjectDlg.openSeq++;   // 强制 el-tree 重建：清掉上次的机构勾选残留
  }
}, { immediate: true });

// 机构 → 员工 缓存：成功查过的机构存起来，重复勾选/取消不再重复请求（失败不缓存，下次会重试）
const orgUsersCache = new Map();
let orgCheckTimer = null;

// 防抖：勾父机构时 el-tree 级联勾全部子节点，@check-change 会对每个节点各触发一次，
// 若每次都跑一遍查询 → O(N²) 请求风暴。合并成"安静 150ms 后只跑一次"。
function onOrgCheckChange() {
  clearTimeout(orgCheckTimer);
  orgCheckTimer = setTimeout(loadCheckedOrgEmployees, 150);
}

// 「包含下级机构」开关：开启时把已勾机构的全部下级也勾到树上；关闭时收起到最上层。
function onIncludeSubOrgChange(val) {
  const tree = subjectTreeRef.value;
  if (tree) {
    const keys = new Set(tree.getCheckedKeys());
    if (val) {
      const addDesc = (n) => (n.children || []).forEach(c => { keys.add(c.code); addDesc(c); });
      tree.getCheckedNodes().forEach(addDesc);
    } else {
      const walk = (n, ancestorChecked) => {
        const checked = keys.has(n.code);
        if (checked && ancestorChecked) keys.delete(n.code);
        (n.children || []).forEach(c => walk(c, ancestorChecked || checked));
      };
      (subjectDlg.orgTree || []).forEach(n => walk(n, false));
    }
    tree.setCheckedKeys([...keys]);
  }
  clearTimeout(orgCheckTimer);
  orgCheckTimer = setTimeout(loadCheckedOrgEmployees, 150);
}

async function loadCheckedOrgEmployees() {
  const checkedNodes = subjectTreeRef.value?.getCheckedNodes() || [];
  if (props.dim === 'ORG') {
    // 上层容器机构的 code 不在数据范围 orgCodes 内，勾上也不算选中，否则把越权机构带进查询。ALL 不裁剪。
    const sc = pickerScope.value;
    const allow = (sc && sc.mode !== 'ALL') ? new Set(sc.orgCodes || []) : null;
    let nodes = checkedNodes;
    if (subjectDlg.includeSubOrg) {
      const acc = new Map();
      const collect = (n) => {
        if (!acc.has(n.code)) acc.set(n.code, { code: n.code, name: n.name });
        (n.children || []).forEach(collect);
      };
      checkedNodes.forEach(collect);
      nodes = [...acc.values()];
    }
    const picked = allow ? nodes.filter(n => allow.has(n.code)) : nodes;
    subjectDlg.selected = picked.map(n => ({ id: n.code, name: n.name, org: '' }));
    return;
  }
  // SELF（支行员工）：只能选自己——勾任意机构都只加入本人，不加载同机构同事
  if (pickerScope.value.mode === 'SELF') {
    const sc = pickerScope.value;
    const base = subjectDlg.selected.filter(s => s._fromSearch);
    if (checkedNodes.length && sc.selfEmpId && !base.some(s => s.id === sc.selfEmpId)) {
      base.push({ id: sc.selfEmpId, name: sc.selfName || sc.selfEmpId, org: '' });
    }
    subjectDlg.selected = base;
    return;
  }
  // EMP 模式：勾机构（含级联子机构）→ 加载其下全部员工。分批并发(每批 8)+逐批刷新。
  // 数据范围过滤：勾子机构时 el-tree 级联会把容器机构（如西安分行）也勾成全选，
  // 直接加载其员工会把越权用户带出 → 按数据范围 orgCodes 过滤（ALL 不限）。
  const empSc = pickerScope.value;
  const empAllow = (empSc && empSc.mode !== 'ALL') ? new Set(empSc.orgCodes || []) : null;
  let empNodes = checkedNodes;
  if (subjectDlg.includeSubOrg) {
    const acc = new Map();
    const collect = (n) => {
      if (!acc.has(n.code)) acc.set(n.code, { code: n.code, name: n.name, children: n.children });
      (n.children || []).forEach(collect);
    };
    checkedNodes.forEach(collect);
    empNodes = [...acc.values()];
  }
  const orgNodes = empAllow ? empNodes.filter(n => empAllow.has(n.code)) : empNodes;
  const newSelected = [...subjectDlg.selected.filter(s => s._fromSearch)];
  const seen = new Set(newSelected.map(s => s.id));
  const BATCH = 8;
  for (let i = 0; i < orgNodes.length; i += BATCH) {
    const batch = orgNodes.slice(i, i + BATCH);
    const results = await Promise.all(batch.map(async (node) => {
      let list = orgUsersCache.get(node.code);
      if (list === undefined) {
        try {
          const users = await listOrgUsers(node.code, { pageSize: 100 });
          list = Array.isArray(users) ? users : (users?.records || []);
          orgUsersCache.set(node.code, list);   // 仅成功才缓存；失败不缓存，下次重试
        } catch { list = []; }
      }
      return { node, list };
    }));
    for (const { node, list } of results) {
      for (const u of list) {
        const id = u.empId || u.userId;
        if (id && !seen.has(id)) {
          seen.add(id);
          newSelected.push({ id, name: u.displayName || u.empName || u.userchnname || u.username, org: node.name });
        }
      }
    }
    subjectDlg.selected = [...newSelected];   // 逐批刷新，全选时能看到员工陆续出现
  }
}

async function onEmpSearch() {
  const kw = subjectDlg.empKw?.trim();
  if (!kw) { subjectDlg.empSearchResults = []; return; }
  try {
    subjectDlg.empSearchResults = await searchReportEmployees(kw, 20);
  } catch { subjectDlg.empSearchResults = []; }
}

let empSearchTimer = null;
watch(() => subjectDlg.empKw, () => {
  clearTimeout(empSearchTimer);
  if (!subjectDlg.empKw?.trim()) { subjectDlg.empSearchResults = []; return; }
  empSearchTimer = setTimeout(onEmpSearch, 300);
});

async function onCustSearch() {
  const kw = subjectDlg.custKw?.trim();
  if (!kw) { subjectDlg.custSearchResults = []; return; }
  try {
    subjectDlg.custSearchResults = await searchReportCustomers(kw, 20);
  } catch { subjectDlg.custSearchResults = []; }
}

let custSearchTimer = null;
watch(() => subjectDlg.custKw, () => {
  clearTimeout(custSearchTimer);
  if (!subjectDlg.custKw?.trim()) { subjectDlg.custSearchResults = []; return; }
  custSearchTimer = setTimeout(onCustSearch, 300);
});

function addSubjectFromSearch(emp) {
  if (subjectDlg.selected.some(s => s.id === emp.id)) return;
  subjectDlg.selected.push({ ...emp, _fromSearch: true });
}

function confirmSubjects() {
  emit('update:modelValue', subjectDlg.selected.map(s => ({ id: s.id, name: s.name, org: s.org || '' })));
  emit('update:visible', false);
}

// 按当前维度拉取「对象选择」数据范围：员工维度走 REPORT_DYN_EMP、机构维度走 REPORT_DYN_ORG。
async function loadPickerScope() {
  try {
    const sc = await getPickerScope(props.dim);
    if (sc && sc.mode) pickerScope.value = sc;
  } catch { /* 失败保持上一次范围，兜底 ALL */ }
}

// 维度变化：重新拉取该维度的数据范围（机构树 dim 无关，无需重取）
watch(() => props.dim, () => { loadPickerScope(); });

onMounted(async () => {
  treeLoading.value = true;
  await Promise.all([
    getOrgTree().then(tree => { orgTreeData.value = tree; }).catch((e) => { treeError.value = e?.message || '机构树加载失败'; }),
    loadPickerScope()
  ]);
  treeLoading.value = false;
});

defineExpose({ confirmSubjects });
</script>

<style lang="scss" scoped>
.subject-picker {
  display: flex; gap: var(--space-4); min-height: 400px;
  .picker-left { border-right: 1px solid var(--color-border); flex: 1; padding-right: var(--space-4); overflow: auto; }
  .picker-right { flex: 1; overflow: auto; }
  .picker-title { color: var(--color-text-strong); font-size: 14px; font-weight: 600; margin-bottom: var(--space-2); }
  .emp-results {
    border: 1px solid var(--color-border); border-radius: var(--radius-control); max-height: 180px; overflow: auto;
    .emp-row {
      background: transparent; border: 0; color: var(--color-text-strong); cursor: pointer; display: flex; font-size: 13px; justify-content: space-between; padding: 6px 10px; text-align: left; width: 100%;
      &:hover { background: var(--color-brand-100); }
      .muted { color: var(--color-text-muted); font-size: 12px; }
    }
  }
  .search-empty { color: var(--color-text-muted); font-size: 12px; padding: var(--space-3); text-align: center; }
  .error-text { color: var(--color-danger-fg); }
  .selected-list {
    border: 1px solid var(--color-border); border-radius: var(--radius-control); max-height: 200px; overflow: auto; padding: 6px;
  }
  .obj-empty { color: var(--color-text-muted); font-size: 12px; padding: 12px; text-align: center; }
}
</style>
