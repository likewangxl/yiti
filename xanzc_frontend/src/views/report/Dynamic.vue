<!--
  动态指标查询 ——「维度 → 指标 → 对象 → 日期 → 查询」
  对应 HTML 中 RptDyn 组件（Reports v2: dynamic query with metric tree + saved schemes）

  接入 yiti API（路径以 src/api/*.js 为准，注意切勿与历史/mock 路径混淆）：
    GET   /api/reports/query-dimensions?dim={EMP|ORG|CUST}     —— getQueryDimensions
                                                                  （单维度指标分组树；维度本身硬编码 3 项）
    POST  /api/reports/dynamic-query                           —— queryDynamic
    POST  /api/reports/dynamic-query/export                    —— exportDynamic
    GET   /api/reports/saved-queries[/{id}]                    —— listSavedQueries / getSavedQuery
    GET   /api/employees                                       —— listEmployees   (EMP 维度对象池)
    GET   /api/orgs/tree                                       —— getOrgTree      (ORG 维度对象池)
    GET   /api/customers                                       —— listCustomers   (CUST 维度对象池)
    GET   /api/perf/metrics?status=ACTIVE&pageNo=1&pageSize=100 —— listMetrics    (指标 code→name 映射 + 默认选中)
-->
<template>
  <div class="rpt-dyn">
    <div class="page-h">
      <h1>动态指标查询</h1>
      <span class="desc">维度 → 指标 → 对象 → 日期 · 支持保存方案 / 部门共享</span>
      <div class="actions">
        <el-button :icon="Folder"   @click="schemeListVisible = true">我的方案</el-button>
        <el-button :icon="Plus"     @click="openSaveScheme">保存为方案</el-button>
        <el-button :icon="Download" @click="onExport" :loading="exporting">导出</el-button>
      </div>
    </div>

    <!-- 查询条件卡 -->
    <div class="card-section query">
      <div class="row">
        <div class="col">
          <div class="lab">① 维度</div>
          <el-radio-group v-model="dim" size="default">
            <el-radio-button v-for="d in dimensions" :key="d.code" :value="d.code">{{ d.label }}</el-radio-button>
          </el-radio-group>
        </div>

        <div class="col grow">
          <div class="lab">② 指标 (已选 {{ pickedMetrics.length }} 个)</div>
          <div class="tags click" @click="pickerVisible = true">
            <el-tag v-for="c in pickedMetrics" :key="c" closable type="info" effect="plain" @close.stop="removeMetric(c)">
              {{ metricLabel(c) }}
            </el-tag>
            <el-tag class="add" effect="plain">+ 添加</el-tag>
          </div>
        </div>

        <div class="col grow">
          <div class="lab">③ 对象 (已选 {{ subjects.length }}，不选=查全部可见对象)</div>
          <div class="tags" :class="{ click: !selfOnlyPicker }"
               @click="!selfOnlyPicker && (subjectDlg.show = true)">
            <el-tag v-for="s in subjects" :key="s.id" closable effect="plain" @close.stop="subjects = subjects.filter(x => x !== s)">
              {{ subjectLabel(s) }}
            </el-tag>
            <!-- SELF 范围只能看本人：不让选对象，默认查本人。客户维度不限范围，不受此限 -->
            <el-tag v-if="selfOnlyPicker" type="info" effect="plain">仅本人</el-tag>
            <el-tag v-else-if="!subjects.length" class="add" effect="plain">+ 选择（默认全部）</el-tag>
            <el-tag v-else class="add" effect="plain">+ 选择</el-tag>
          </div>
        </div>

        <div class="col">
          <div class="lab">④ 数据日期</div>
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" style="width:200px" />
        </div>
      </div>

      <div class="row-actions">
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" :loading="querying" @click="doQuery">查询</el-button>
      </div>
    </div>

    <!-- 结果卡：始终展示，未查询时给空表框 + 提示，避免维度下方空荡荡 -->
    <div class="card-section result">
      <div class="card-h">
        <div class="title">查询结果（{{ rows.length }} 行 × {{ pickedMetrics.length }} 指标）</div>
        <div class="chart-tabs">
          <el-button :type="view === 'table' ? 'primary' : ''" :icon="Grid"        circle size="small" @click="view='table'" />
          <el-button :type="view === 'bar'   ? 'primary' : ''" :icon="Histogram"   circle size="small" @click="view='bar'"   />
          <el-button :type="view === 'line'  ? 'primary' : ''" :icon="TrendCharts" circle size="small" @click="view='line'"  />
          <el-button :type="view === 'pie'   ? 'primary' : ''" :icon="PieChart"    circle size="small" @click="view='pie'"   />
        </div>
      </div>

      <el-table v-if="view === 'table'" :data="rows" size="default" stripe
                :empty-text="hasResult ? '无符合条件的数据' : '请选择指标和对象后点击「查询」'">
        <el-table-column prop="subject" :label="dimLabel" width="160" />
        <el-table-column v-if="dim === 'EMP'" prop="empName" label="姓名" width="120" />
        <el-table-column
          v-for="c in pickedMetrics" :key="c" :prop="c"
          :label="metricLabel(c)" align="right">
          <template #default="{ row }">{{ formatNum(row[c]) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="view === 'table'" class="pager">
        <el-pagination
          v-model:current-page="resultPageNo"
          v-model:page-size="resultPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="resultTotal"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>

      <v-chart v-else class="chart" :option="chartOption" autoresize />
    </div>

    <!-- 对象选择弹框：机构树 + 员工搜索 -->
    <el-dialog v-model="subjectDlg.show" :title="dim === 'EMP' ? '选择员工' : dim === 'CUST' ? '选择客户' : '选择机构'" width="720px" :close-on-click-modal="false">
      <div class="subject-picker">
        <!-- 客户维度不用机构树，只用右侧搜索框 -->
        <div class="picker-left" v-if="dim !== 'CUST'">
          <div class="picker-title">机构树（勾选{{ dim === 'EMP' ? '机构可选该机构下全部员工' : '机构' }}）</div>
          <!-- 机构/员工维度：独立勾选，可选是否把勾选机构连同其下级一起纳入 -->
          <el-checkbox v-if="dim !== 'CUST'" v-model="subjectDlg.includeSubOrg" size="small" style="margin-bottom:8px"
                       @change="onIncludeSubOrgChange">包含下级机构</el-checkbox>
          <el-input v-model="subjectDlg.treeKw" placeholder="搜索机构名称" size="small" clearable style="margin-bottom:8px" />
          <!-- 机构/员工维度均 check-strictly 父子独立：勾哪个算哪个，不级联误勾上级容器机构；
               是否带下级由「包含下级机构」开关控制 -->
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
        </div>
        <div class="picker-right">
          <template v-if="dim === 'EMP'">
            <div class="picker-title">精确搜索员工</div>
            <el-input v-model="subjectDlg.empKw" placeholder="输入姓名或工号搜索" size="small" clearable
                      @keyup.enter="onEmpSearch" style="margin-bottom:8px">
              <template #append><el-button @click="onEmpSearch">搜索</el-button></template>
            </el-input>
            <div class="emp-results">
              <div v-for="e in subjectDlg.empSearchResults" :key="e.id" class="emp-row" @click="addSubjectFromSearch(e)">
                <span>{{ e.name }}</span>
                <span class="muted">{{ e.org }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="dim === 'CUST'">
            <div class="picker-title">搜索客户</div>
            <el-input v-model="subjectDlg.custKw" placeholder="输入客户名或客户号搜索" size="small" clearable
                      @keyup.enter="onCustSearch" style="margin-bottom:8px">
              <template #append><el-button @click="onCustSearch">搜索</el-button></template>
            </el-input>
            <div class="emp-results">
              <div v-for="c in subjectDlg.custSearchResults" :key="c.id" class="emp-row" @click="addSubjectFromSearch(c)">
                <span>{{ c.name }}</span>
                <span class="muted">{{ c.org }}</span>
              </div>
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
        <el-button @click="subjectDlg.show = false">取消</el-button>
        <el-button type="primary" @click="confirmSubjects">确定</el-button>
      </template>
    </el-dialog>

    <MetricPicker
      v-model:visible="pickerVisible"
      v-model="pickedMetrics"
      :dim="dim"
    />
    <SchemeSaveDialog
      v-model:visible="saveSchemeVisible"
      :context="saveContext"
      @saved="onSchemeSaved"
    />
    <SchemeListDialog
      v-model:visible="schemeListVisible"
      @load="onSchemeLoad"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import { Grid, Histogram, TrendCharts, PieChart, Folder, Plus, Download } from '@element-plus/icons-vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart, LineChart, PieChart as EPie } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { queryDynamic, exportDynamicFile, searchReportEmployees, searchReportCustomers, getPickerScope, getQueryDimensions, getSavedQuery } from '@/api/report';
import { getOrgTree, listOrgUsers } from '@/api/orgs';
import { listMetrics } from '@/api/metrics';
import MetricPicker from './components/MetricPicker.vue';
import SchemeSaveDialog from './components/SchemeSaveDialog.vue';
import SchemeListDialog from './components/SchemeListDialog.vue';

use([CanvasRenderer, BarChart, LineChart, EPie, GridComponent, TooltipComponent, LegendComponent]);

const dim = ref('EMP');
const dimensions = ref([{ code: 'EMP', label: '员工' }, { code: 'ORG', label: '机构' }, { code: 'CUST', label: '客户' }]);
const metricsList = ref([]);
const pickedMetrics = ref([]);
const subjects = ref([]);
// 数据日期默认取当天（本地时区 YYYY-MM-DD）
const LATEST_DATA_DATE = (() => {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
})();
const date = ref(LATEST_DATA_DATE);
const view = ref('table');
const querying = ref(false);
const exporting = ref(false);
const hasResult = ref(true);

// 结果数据 —— 初始为空，点查询后由后端返回填充；查询失败一律清空（不回退假数据）
const rows = ref([]);
const resultPageNo = ref(1);
const resultPageSize = ref(20);
// 服务端分页：rows 仅当前页，resultTotal 为符合条件的对象总数（后端返回）
const resultTotal = ref(0);
const dimLabel = computed(() => dimensions.value.find(d => d.code === dim.value)?.label || '对象');
// metricMap：tolerant 同时消化两种字段命名（后端 MetricDefRespDTO.metricCode/metricName 与 mock metricsFlat.code/label）
const metricMap = computed(() => Object.fromEntries(
  metricsList.value.map(m => [m.metricCode || m.code, m.metricName || m.label])
));
const metricLabel = (c) => metricMap.value[c] || c;
const formatNum = (v) => v == null ? '-' : (typeof v === 'number' ? v.toLocaleString() : v);

function removeMetric(c) { pickedMetrics.value = pickedMetrics.value.filter(x => x !== c); }
function reset() {
  dim.value = 'EMP';
  // 不预选指标，由用户自行挑
  pickedMetrics.value = [];
  subjects.value = [];
  date.value = LATEST_DATA_DATE;
  hasResult.value = false;
}

// ============ 对象选择 dialog ============
const subjectTreeRef = ref(null);
const orgTreeData = ref([]);
// 对象选择数据范围（按角色）：ALL 不限 / ORG_SUBTREE 本机构子树 / SELF 仅本人
const pickerScope = ref({ mode: 'ALL', selfEmpId: '', selfName: '', orgCodes: [] });
// 仅本人选择器：SELF 范围默认只能看自己、不让选；客户维度不限数据范围，不受此限
const selfOnlyPicker = computed(() => pickerScope.value.mode === 'SELF' && dim.value !== 'CUST');

// 按允许的机构编码裁剪机构树：保留 code 命中或有命中后代的节点（ALL 不裁剪）
function filterTreeByCodes(nodes, codeSet) {
  const out = [];
  for (const n of nodes || []) {
    const children = filterTreeByCodes(n.children, codeSet);
    if (codeSet.has(n.code) || children.length) {
      out.push({ ...n, children });
    }
  }
  return out;
}
function scopedOrgTree() {
  const sc = pickerScope.value;
  if (!sc || sc.mode === 'ALL') return orgTreeData.value;
  const set = new Set(sc.orgCodes || []);
  return filterTreeByCodes(orgTreeData.value, set);
}
const subjectDlg = reactive({
  show: false,
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

watch(() => subjectDlg.show, (visible) => {
  if (visible) {
    subjectDlg.selected = [...subjects.value];
    subjectDlg.orgTree = scopedOrgTree();
    subjectDlg.treeKw = '';
    subjectDlg.empKw = '';
    subjectDlg.empSearchResults = [];
    subjectDlg.custKw = '';
    subjectDlg.custSearchResults = [];
    subjectDlg.includeSubOrg = false;   // 每次打开默认「仅本级」，避免上次开关状态残留
    subjectDlg.openSeq++;   // 强制 el-tree 重建：清掉上次的机构勾选残留，避免"勾着却没加载员工"
  }
});

// 机构 → 员工 缓存：成功查过的机构存起来，重复勾选/取消不再重复请求（失败不缓存，下次会重试）
const orgUsersCache = new Map();
let orgCheckTimer = null;

// 防抖：勾父机构时 el-tree 级联勾全部子节点，@check-change 会对每个节点各触发一次，
// 若每次都跑一遍查询 → O(N²) 请求风暴、疯狂报错。
// 这里把这一连串触发合并成"安静 150ms 后只跑一次"，既消除风暴又保留能加载数据的事件。
function onOrgCheckChange() {
  clearTimeout(orgCheckTimer);
  orgCheckTimer = setTimeout(loadCheckedOrgEmployees, 150);
}

// 「包含下级机构」开关：开启时把已勾机构的全部下级也勾到树上（可见反馈）；
// 关闭时收起到最上层（祖先已勾的子节点取消勾选）。改完再重算选中对象。
function onIncludeSubOrgChange(val) {
  const tree = subjectTreeRef.value;
  if (tree) {
    const keys = new Set(tree.getCheckedKeys());
    if (val) {
      // 开：已勾节点的所有后代都勾上
      const addDesc = (n) => (n.children || []).forEach(c => { keys.add(c.code); addDesc(c); });
      tree.getCheckedNodes().forEach(addDesc);
    } else {
      // 关：祖先也被勾的节点取消，只保留最上层勾选
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
  if (dim.value === 'ORG') {
    // 西安分行/榆林总等上层机构只是树形结构的容器节点（filterTreeByCodes 为展示层级而保留），
    // 它们的 code 并不在数据范围 orgCodes 内。勾上也不算选中，否则会把越权机构带进查询，
    // 导致后端整单 RPT-40005「对象不在数据范围内」失败。ALL 不裁剪。
    const sc = pickerScope.value;
    const allow = (sc && sc.mode !== 'ALL') ? new Set(sc.orgCodes || []) : null;
    // 「包含下级机构」开启：把每个勾选机构展开成它+全部下级；关闭：只取勾选的本级
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
  // EMP 模式：勾机构（含级联子机构）→ 加载其下全部员工。
  // 全选会勾上百个机构：用「分批并发(每批 8 个)+ 逐批刷新界面」，既快又能看到员工逐步出现，
  // 且并发有上限不会变回请求风暴。
  // 数据范围过滤：勾子机构时 el-tree 级联会把「仅作容器展示、不在数据范围内」的上级机构（如西安分行）
  // 也勾成全选，若直接加载其员工会把越权用户（如一级机构行长）带出 → 后端 RPT-40005 整单失败。
  // 故与 ORG 维度一致，按数据范围 orgCodes 过滤掉越权机构再加载（ALL 不限）。
  const empSc = pickerScope.value;
  const empAllow = (empSc && empSc.mode !== 'ALL') ? new Set(empSc.orgCodes || []) : null;
  // 独立勾选：默认只取勾中的机构；「包含下级机构」开启时展开为它+全部下级
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
          // OrgUserDTO 的姓名在 displayName 字段（无 empName/userchnname）；缺失才退化工号 username
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
    // 搜 PT_USER（报表专用接口，按工号/姓名匹配，REPORT 权限）；返回 [{id,name,org}]
    subjectDlg.empSearchResults = await searchReportEmployees(kw, 20);
  } catch { subjectDlg.empSearchResults = []; }
}

// 员工搜索：边输边搜（防抖 300ms），无需点按钮；清空则清结果
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
    // 搜客户（按客户名/客户号，不限范围）；返回 [{id,name,org}]
    subjectDlg.custSearchResults = await searchReportCustomers(kw, 20);
  } catch { subjectDlg.custSearchResults = []; }
}

// 客户搜索：边输边搜（防抖 300ms），无需点按钮；清空则清结果
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
  subjects.value = subjectDlg.selected.map(s => ({ id: s.id, name: s.name, org: s.org || '' }));
  subjectDlg.show = false;
}

function subjectLabel(s) {
  // 统一显示名称：员工=姓名（不再加"员工"前缀）、机构=机构名、客户=客户名
  return s.name;
}

function addSubject(o) {
  if (subjects.value.some(s => s.id === o.id)) return;
  subjects.value.push(o);
  objKw.value = '';
}

// 按当前维度拉取「对象选择」数据范围：员工维度走 REPORT_DYN_EMP、机构维度走 REPORT_DYN_ORG，
// 两维度可在权限配置页独立设置，所以维度一变就要重新取一次。
async function loadPickerScope() {
  try {
    const sc = await getPickerScope(dim.value);
    if (sc && sc.mode) pickerScope.value = sc;
  } catch { /* 失败保持上一次范围，picker 兜底 ALL */ }
}

// 维度切换：清空已选对象 + 已选指标，避免跨维度脏数据 + 重新拉取该维度的数据范围
// （指标按维度过滤后，残留的另一维度指标既不可见又会被带进查询）
watch(dim, (cur, prev) => {
  if (cur === prev) return;
  subjects.value = [];
  pickedMetrics.value = [];
  hasResult.value = false;
  rows.value = [];          // 切维度清空结果表，避免遗留上一维度数据
  resultPageNo.value = 1;
  loadPickerScope();
});

// 点「查询」按钮：回到第 1 页再查
function doQuery() {
  resultPageNo.value = 1;
  return runQuery();
}
// 翻页 / 改每页条数：向后端请求对应页
function onPageChange(p) { resultPageNo.value = p; runQuery(); }
function onPageSizeChange(s) { resultPageSize.value = s; resultPageNo.value = 1; runQuery(); }

// 实际发起查询（服务端分页）。不选对象=按数据范围查"能看到的全部对象"，故不再强制选对象。
async function runQuery() {
  if (!pickedMetrics.value.length) { ElMessage.warning('请至少选择 1 个指标'); return; }
  querying.value = true;
  try {
    const r = await queryDynamic({
      dim: dim.value,
      metrics: pickedMetrics.value,
      subjects: subjects.value.map(s => s.id),   // 空数组=不选对象，后端按范围查全部
      date: date.value,
      pageNo: resultPageNo.value,
      pageSize: resultPageSize.value
    });
    if (Array.isArray(r?.rows)) rows.value = r.rows;
    else if (r?.result) rows.value = r.result;
    else rows.value = [];
    resultTotal.value = (r?.total != null) ? r.total : rows.value.length;
    hasResult.value = true;
    ElMessage.success(`查询成功：共 ${resultTotal.value} 个对象`);
  } catch (e) {
    // 查询失败（如对象越权 RPT-40005「对象不在数据范围内」）：必须清空结果，
    // 绝不能回退假数据冒充查询成功——否则后端已拦截，前端却照样展示一桌假数字。
    // 拦截器（http.js）已弹出后端真实错误提示，这里不再重复弹。
    rows.value = [];
    resultTotal.value = 0;
    hasResult.value = false;
  } finally {
    querying.value = false;
  }
}

async function onExport() {
  if (!pickedMetrics.value.length) { ElMessage.warning('请至少选择 1 个指标'); return; }
  // 不选对象=导出数据范围内全部对象（导出不分页，后端返回全量）
  exporting.value = true;
  try {
    await exportDynamicFile({
      dim: dim.value,
      metrics: pickedMetrics.value,
      subjects: subjects.value.map(s => s.id),
      date: date.value
    });
    ElMessage.success('导出成功，文件已开始下载');
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || e));
  } finally {
    exporting.value = false;
  }
}

// 方案保存 / 我的方案
const saveSchemeVisible = ref(false);
const schemeListVisible = ref(false);
const pickerVisible = ref(false);
const saveContext = ref({ dim: 'EMP', metrics: 0, objects: 0, payload: {} });

function openSaveScheme() {
  saveContext.value = {
    dim: dim.value,
    metrics: pickedMetrics.value.length,
    objects: subjects.value.length,
    payload: {
      dim: dim.value,
      metrics: pickedMetrics.value,           // 传字符串 code 数组（saveQuery 内部 stringify）
      // 传完整对象 {id,name,org}，载入方案时可直接反显名称
      subjects: subjects.value.map(s => ({ id: s.id, name: s.name, org: s.org || '' }))
    }
  };
  saveSchemeVisible.value = true;
}
function onSchemeSaved(/* scheme */) { /* 不强制刷新，列表 dialog 打开时会再拉 */ }

/**
 * 载入方案 —— 从列表项 → 调详情接口 → 切维度 → 等候选数据加载完 → ID 数组反查为对象 → 触发查询。
 * 列表项 SavedQuerySummaryDTO 不含 metricCodes/subjectIds，必须先调 getSavedQuery 拿详情。
 */
async function onSchemeLoad(scheme) {
  if (!scheme?.id) return;
  let detail;
  try { detail = await getSavedQuery(scheme.id); }
  catch { ElMessage.error('载入方案失败'); return; }
  if (!detail) return;

  // 1. 切维度 —— watch(dim) 会清空 subjects/metrics，必须等它跑完(nextTick)再反显，否则反被清掉
  if (detail.dim && detail.dim !== dim.value) {
    dim.value = detail.dim;
    await nextTick();
  }

  // 2. 反显指标（数组形态：['M0001', ...]）
  if (Array.isArray(detail.metrics)) pickedMetrics.value = [...detail.metrics];

  // 3. 反显对象 —— 新方案存的是 {id,name,org} 对象，老方案是纯 ID 字符串，两者都兼容
  if (Array.isArray(detail.subjects)) {
    subjects.value = detail.subjects.map(s =>
      typeof s === 'string'
        ? { id: s, name: s, org: '' }
        : { id: s.id, name: s.name || s.id, org: s.org || '' }
    );
  }
  ElMessage.success(`已载入方案：${detail.name}`);
  doQuery();
}

// 图表 option
const chartOption = computed(() => {
  // 图表类目用「姓名」：员工维度取 empName(姓名)，机构/客户维度 subject 本就是机构名/客户名
  const labelOf = (r) => (dim.value === 'EMP' ? (r.empName || r.subject) : r.subject);
  const xs = rows.value.map(labelOf);
  const series = pickedMetrics.value.map(c => ({
    name: metricLabel(c),
    type: view.value === 'bar' ? 'bar' : view.value === 'line' ? 'line' : 'pie',
    data: view.value === 'pie'
      ? rows.value.map(r => ({ name: labelOf(r), value: r[c] || 0 }))
      : rows.value.map(r => r[c] || 0),
    smooth: view.value === 'line',
    radius: view.value === 'pie' ? '60%' : undefined
  }));
  if (view.value === 'pie') {
    // 饼图只展示第一个指标
    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [series[0] || { type: 'pie', data: [] }]
    };
  }
  // 对象（x 轴类目）多/名称长时：全部显示不自动隐藏 + 按数量旋转 + 长名截断 + containLabel 防裁剪，
  // 超过 12 个再挂一条横向缩放条，避免挤成一团（解决「选超过 4 个柱状/折线图错乱」）。
  const count = xs.length;
  const rotate = count > 8 ? 45 : count > 4 ? 30 : 0;
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0 },
    grid: { top: 36, left: 60, right: 20, bottom: count > 12 ? 56 : 30, containLabel: true },
    xAxis: {
      type: 'category',
      data: xs,
      axisLabel: {
        interval: 0,               // 全部显示，不让 ECharts 自动隐藏类目
        rotate,
        hideOverlap: true,
        formatter: (v) => (typeof v === 'string' && v.length > 10 ? v.slice(0, 10) + '…' : v)
      }
    },
    yAxis: { type: 'value' },
    // 对象很多时给个横向缩放条
    dataZoom: count > 12
      ? [{ type: 'slider', start: 0, end: Math.max(20, Math.round(1200 / count)), bottom: 8, height: 16 }]
      : undefined,
    series
  };
});

onMounted(async () => {
  await Promise.all([
    getOrgTree().then(tree => { orgTreeData.value = tree; }).catch(() => {}),
    getPickerScope(dim.value).then(sc => { if (sc && sc.mode) pickerScope.value = sc; }).catch(() => {}),
    listMetrics({ status: 'ACTIVE' })
      .then(list => { if (Array.isArray(list)) metricsList.value = list; })
      .catch(() => { metricsList.value = []; })
  ]);
  // 不再默认预选指标，由用户自行从"选择指标"里挑
});
</script>

<style lang="scss" scoped>
.rpt-dyn {
  .page-h { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
    h1 { font-size: 18px; font-weight: 600; color: $text-1; }
    .desc { color: $text-3; font-size: 12px; }
    .actions { margin-left: auto; display: flex; gap: 8px; }
  }
  .query .row { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
  .query .col { display: flex; flex-direction: column; gap: 6px; min-width: 180px; }
  .query .col.grow { flex: 1; min-width: 240px; }
  .query .lab { font-size: 12px; color: $text-3; }
  .query .tags { display: flex; flex-wrap: wrap; gap: 6px; padding: 6px 8px; min-height: 36px; border: 1px solid $border-2; border-radius: 4px; align-items: center;
    &.click { cursor: pointer; }
    .add { cursor: pointer; border-style: dashed; color: $primary-400; }
  }
  .row-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }

  .result { padding: 16px 20px; }
  .card-h { display: flex; align-items: center; padding: 0 0 12px; border-bottom: 1px solid $border-1; margin-bottom: 14px;
    .title { font-size: 14px; font-weight: 600; }
    .chart-tabs { margin-left: auto; display: flex; gap: 4px; }
  }
  .chart { height: 360px; }
  .obj-pool { max-height: 240px; overflow: auto; margin-top: 8px;
    .obj-row { display: flex; padding: 6px 8px; cursor: pointer; border-radius: 4px; font-size: 13px;
      &:hover { background: $bg-soft; }
      .muted { margin-left: auto; color: $text-4; font-size: 12px; }
    }
  }

  .subject-picker {
    display: flex; gap: 16px; min-height: 400px;
    .picker-left { flex: 1; border-right: 1px solid $border-2; padding-right: 16px; overflow: auto; }
    .picker-right { flex: 1; overflow: auto; }
    .picker-title { font-size: 13px; font-weight: 600; color: $text-2; margin-bottom: 8px; }
    .emp-results {
      max-height: 180px; overflow: auto; border: 1px solid $border-3; border-radius: 4px;
      .emp-row {
        display: flex; justify-content: space-between; padding: 6px 10px; cursor: pointer; font-size: 13px;
        &:hover { background: $primary-50; }
        .muted { color: $text-4; font-size: 12px; }
      }
    }
    .selected-list {
      max-height: 200px; overflow: auto; padding: 6px; border: 1px solid $border-3; border-radius: 4px;
    }
    .obj-empty { color: $text-4; font-size: 12px; padding: 12px; text-align: center; }
  }
}
.pager { display: flex; justify-content: flex-end; padding: 12px 0; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
</style>
