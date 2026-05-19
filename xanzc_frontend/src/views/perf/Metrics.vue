<template>
  <div>
    <div class="page-h">
      <h1>指标库</h1>
      <span class="desc">三级层级树 · SQL/Groovy 计算配置 · 试运行 · 版本</span>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button @click="onImport">📥 导入指标</el-button>
        <el-button type="primary" @click="openCreate">+ 新增指标</el-button>
      </div>
    </div>

    <div class="layout">
      <!-- 左：分类树 -->
      <div class="card-section tree-col">
        <div class="card-h-mini">指标层级</div>
        <el-tree
          ref="treeRef"
          :data="treeData"
          node-key="id"
          :default-expand-all="true"
          :expand-on-click-node="false"
          :highlight-current="true"
          :current-node-key="picked"
          @node-click="onTreeClick"
          empty-text="暂无指标"
        >
          <template #default="{ node, data }">
            <span class="tree-node">
              <span class="ico">{{ data.isMetric ? '📊' : '📁' }}</span>
              <span :class="{ 'tree-leaf': data.isMetric }">{{ node.label }}</span>
              <el-tag v-if="data.isMetric" :class="statusCls(data.status)" effect="plain" size="small" class="tree-tag">
                {{ statusLabel(data.status) }}
              </el-tag>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- 右：详情 + 同分类 -->
      <div class="card-section detail-col">
        <div v-if="!detail.metricCode" class="empty-pane">← 在左侧选择任一指标查看详情</div>
        <template v-else>
          <div class="card-h">
            <div class="title">指标详情 · {{ detail.metricName }}</div>
            <el-tag :class="statusCls(detail.status)" effect="plain">{{ statusLabel(detail.status) }}</el-tag>
            <span class="version">{{ detail.version || `v${detail.metricLevel || 1}` }}</span>
          </div>

          <table class="meta-table">
            <tr>
              <td class="lab">编码</td><td class="val"><code class="mono">{{ detail.metricCode }}</code></td>
              <td class="lab">分类</td><td class="val">{{ resolveCategory(detail) }}</td>
            </tr>
            <tr>
              <td class="lab">计算方式</td>
              <td class="val">
                <el-tag :class="logicCls(detail.calcLogicType)" effect="plain">{{ detail.calcLogicType || '-' }}</el-tag>
              </td>
              <td class="lab">数据源</td><td class="val">{{ detail.dataSource || guessDataSource(detail) }}</td>
            </tr>
          </table>

          <!-- 计算公式：SQL / EXPR / SUMMARY 三选一；若后端字段为空，显示占位块（不让模板看起来缺一栏） -->
          <template v-if="detail.calcLogicType === 'SQL'">
            <div class="block-h">SQL 表达式</div>
            <pre class="code">{{ detail.sqlText || '-- 暂未配置 SQL，可点【编辑】补充' }}</pre>
          </template>
          <template v-else-if="detail.calcLogicType === 'EXPR'">
            <div class="block-h">EXPR / Groovy 表达式</div>
            <pre class="code">{{ detail.exprText || '// 暂未配置表达式，可点【编辑】补充' }}</pre>
          </template>
          <template v-else-if="detail.calcLogicType === 'SUMMARY'">
            <div class="block-h">SUMMARY 汇总规则</div>
            <pre class="code">{{ detail.summaryRule || '/* 暂未配置汇总规则 */' }}</pre>
          </template>
          <template v-else>
            <div class="block-h">计算逻辑</div>
            <pre class="code">{{ '/* 计算方式未指定 */' }}</pre>
          </template>

          <div class="block-h">槽位 (Slot) 声明</div>
          <el-table :data="resolveSlots(detail)" size="default" border empty-text="该指标未声明槽位">
            <el-table-column label="槽位名" prop="name" width="180">
              <template #default="{row}"><code class="mono">{{ row.name }}</code></template>
            </el-table-column>
            <el-table-column label="类型" prop="type" width="120" />
            <el-table-column label="是否必填" width="100" align="center">
              <template #default="{row}">
                <span v-if="row.required" class="req">是</span>
                <span v-else class="opt">否</span>
              </template>
            </el-table-column>
            <el-table-column label="说明" prop="desc" min-width="200" show-overflow-tooltip />
          </el-table>

          <div class="acts">
            <el-button type="primary" :disabled="isDisabled" @click="openEdit(detail)">编辑</el-button>
            <el-button :disabled="isDisabled" @click="onTrialRun">▶ 试运行</el-button>
            <el-button :disabled="isDisabled" @click="onExecute">⚡立即执行</el-button>
            <el-button @click="onShowVersions">查看版本历史</el-button>
            <el-button @click="onViewAudit">📋 查看审计</el-button>
            <el-button v-if="!isDisabled" type="warning" plain @click="onChangeStatus('DISABLED')">停用</el-button>
            <el-button v-else type="success" plain @click="onChangeStatus('ACTIVE')">启用</el-button>
            <el-button type="danger" plain @click="onDelete(detail)">删除</el-button>
          </div>
          <div class="audit-hint">
            ⚠ 试运行 / 立即执行 / 编辑 / 删除 / 状态变更 均属高危动作，会自动写入「系统设置 → 审计日志」
          </div>

          <div class="block-h">同分类指标</div>
          <el-table :data="sameCategory" size="default" border empty-text="—">
            <el-table-column label="编码" prop="metricCode" width="100">
              <template #default="{row}"><code class="mono">{{ row.metricCode }}</code></template>
            </el-table-column>
            <el-table-column label="名称" prop="metricName" min-width="160" show-overflow-tooltip />
            <el-table-column label="计算" width="90">
              <template #default="{row}"><el-tag :class="logicCls(row.calcLogicType)" effect="plain" size="small">{{ row.calcLogicType }}</el-tag></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{row}"><el-tag :class="statusCls(row.status)" effect="plain" size="small">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="版本" width="60" align="center">
              <template #default>v1</template>
            </el-table-column>
            <el-table-column label="更新" width="110" align="center">
              <template #default>{{ today }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{row}">
                <el-button link type="primary" size="small" @click="onPick(row.metricCode)">查看</el-button>
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </div>

    <!-- 编辑/新增 弹框 -->
    <el-dialog v-model="dlg.show" :title="dlg.editing ? '编辑指标 · ' + dlg.form.metricName : '新增指标'"
      width="780px" top="5vh" @closed="dlg.editing = null">
      <el-form ref="formRef" :model="dlg.form" :rules="formRules" label-position="top" size="default">
        <div class="form-grid">
          <el-form-item label="编码" prop="metricCode" required>
            <el-input v-model="dlg.form.metricCode" :disabled="!!dlg.editing" placeholder="如 M00xx" maxlength="20" />
          </el-form-item>
          <el-form-item label="名称" prop="metricName" required>
            <el-input v-model="dlg.form.metricName" maxlength="100" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="dlg.form._category" style="width:100%">
              <el-option v-for="c in CATEGORY_OPTIONS" :key="c" :value="c" :label="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="计算方式" prop="calcLogicType">
            <el-radio-group v-model="dlg.form.calcLogicType">
              <el-radio value="SQL">SQL</el-radio>
              <el-radio value="EXPR">Groovy</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>

        <el-form-item :label="dlg.form.calcLogicType === 'EXPR' ? 'Groovy 表达式' : 'SQL 表达式 (支持 #{slot} 占位符)'">
          <el-input
            v-if="dlg.form.calcLogicType === 'EXPR'"
            v-model="dlg.form.exprText" type="textarea" :rows="6"
            placeholder="如 M0001 + M0002"
          />
          <el-input
            v-else
            v-model="dlg.form.sqlText" type="textarea" :rows="6"
            placeholder="SELECT cust_id, AVG(bal) FROM t_xxx WHERE dt=#{datadate}"
          />
        </el-form-item>

        <el-form-item label="槽位声明">
          <el-table :data="dlg.slots" size="small" border empty-text="尚未声明槽位">
            <el-table-column label="槽位名" min-width="140">
              <template #default="{row}"><el-input v-model="row.name" size="small" placeholder="period_start" /></template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{row}">
                <el-select v-model="row.type" size="small">
                  <el-option v-for="t in SLOT_TYPES" :key="t" :value="t" :label="t" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="必填" width="70" align="center">
              <template #default="{row}">
                <el-checkbox v-model="row.required" />
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="180">
              <template #default="{row}"><el-input v-model="row.desc" size="small" /></template>
            </el-table-column>
            <el-table-column label="" width="60" align="center" fixed="right">
              <template #default="{$index}">
                <el-button link type="danger" size="small" @click="dlg.slots.splice($index,1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" plain @click="addSlot" style="margin-top:8px">+ 添加槽位</el-button>
        </el-form-item>

        <el-form-item label="试运行">
          <div class="trial-row">
            <el-date-picker v-model="dlg.trialRange" type="daterange" value-format="YYYY-MM-DD"
              range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期" style="flex:1; min-width:300px" />
            <el-button type="primary" @click="onTrialFromDialog" :loading="dlg.trialing">▶ 试运行</el-button>
            <el-tag v-if="dlg.trial.status === 'SUCCESS'" class="tag-success" effect="plain">
              成功 · {{ dlg.trial.totalRows ?? dlg.trial.rows.length }} 行 · {{ ((dlg.trial.cost || 0) / 1000).toFixed(1) }}s
            </el-tag>
            <el-tag v-else-if="dlg.trial.status === 'FAILED'" class="tag-warning" effect="plain">
              失败 · {{ ((dlg.trial.cost || 0) / 1000).toFixed(1) }}s
            </el-tag>
          </div>

          <!-- 试运行结果表 -->
          <el-table v-if="dlg.trial.rows.length" :data="dlg.trial.rows" size="small" border style="margin-top: 12px">
            <el-table-column v-for="col in dlg.trial.cols" :key="col" :prop="col" :label="col" min-width="140" show-overflow-tooltip />
          </el-table>
          <div v-else-if="dlg.trial.status === 'FAILED'" class="trial-error">
            ✗ {{ dlg.trial.errorMsg || '试运行失败，请检查 SQL 是否合法' }}
          </div>
        </el-form-item>

        <!-- 隐含字段（不让用户暴露太多复杂度） -->
        <input type="hidden" :value="dlg.form.baseDim" />
      </el-form>
      <template #footer>
        <el-button @click="dlg.show = false">取消</el-button>
        <el-button :loading="dlg.saving" @click="onSave('DRAFT')">保存为草稿</el-button>
        <el-button type="primary" :loading="dlg.saving" @click="onSave('ACTIVE')">发布</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史（复用审计日志） -->
    <el-dialog v-model="versionDlg.show" title="版本历史" width="720px">
      <el-table :data="versionDlg.list" size="default" empty-text="暂无变更记录" v-loading="versionDlg.loading">
        <el-table-column label="版本" prop="version" width="70" align="center" />
        <el-table-column label="动作" width="120">
          <template #default="{row}">
            <el-tag :class="bizActionCls(row.bizAction)" effect="plain">{{ bizActionLabel(row.bizAction) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更人" min-width="120">
          <template #default="{row}">{{ row.empName || row.empId || '—' }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="180" show-overflow-tooltip>
          <template #default="{row}">{{ row.reason || '—' }}</template>
        </el-table-column>
        <el-table-column label="变更时间" prop="createdTime" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listMetrics, getMetricDetail,
  createMetric, updateMetric, deleteMetric,
  changeMetricStatus, trialRunMetric, executeMetric
} from '@/api/perf';
import { listAuditLogs } from '@/api/system';

const router = useRouter();

// === 常量 ===
const CATEGORY_OPTIONS = ['业务/存款类', '业务/贷款类', '业务/中收类', '客户指标', '风险指标', '综合指标'];
const SLOT_TYPES = ['DATE', 'STRING', 'STRING[]', 'INTEGER', 'DECIMAL'];
const today = new Date().toISOString().slice(0, 10);

// 后端真实枚举：ACTIVE / DRAFT / DISABLED；INACTIVE 是历史遗留兼容
const statusCls = (s) => ({ ACTIVE: 'tag-success', DRAFT: 'tag-info', DISABLED: 'tag-warning', INACTIVE: 'tag-warning' }[s] || 'tag-info');
const statusLabel = (s) => ({ ACTIVE: '已发布', DRAFT: '草稿', DISABLED: '停用', INACTIVE: '停用' }[s] || s || '-');
const logicCls = (t) => ({ SQL: 'tag-info', EXPR: 'tag-warning', SUMMARY: 'tag-success' }[t] || 'tag-info');
const bizActionCls = (a) => {
  if (a === 'DELETE' || a === 'METRIC_EXECUTE') return 'tag-danger';
  if (a === 'STATUS_CHANGE' || a === 'METRIC_TRIAL_RUN') return 'tag-warning';
  if (a === 'CREATE' || a === 'UPDATE') return 'tag-success';
  return 'tag-info';
};
// V1.6 版本历史动作中文化
const BIZ_ACTION_LABEL = {
  CREATE:           '新增',
  UPDATE:           '编辑',
  DELETE:           '删除',
  STATUS_CHANGE:    '状态变更',
  METRIC_TRIAL_RUN: '试运行',
  METRIC_EXECUTE:   '立即执行',
  SLOT_RELEASE:     '释放槽位',
  PUBLISH:          '发布',
  READ:             '查看',
  LIST:             '查询',
  EXPORT:           '导出',
  IMPORT:           '导入',
  CONFIG:           '配置',
  RECALC:           '重算',
  PERMISSION_CHANGE: '权限变更',
  EXECUTE_SQL:      'SQL 执行',
  TRANSFER:         '转移',
  APPROVE:          '审批通过',
  REJECT:           '审批驳回',
  EXECUTE:          '执行'
};
const bizActionLabel = (a) => BIZ_ACTION_LABEL[a] || a || '—';

// === 数据加载 ===
const allMetrics = ref([]);
async function reload() {
  try {
    const r = await listMetrics({ pageSize: 100 });
    // 停用项保留在树里（按用户反馈），通过按钮 disable 限制操作即可
    if (Array.isArray(r)) allMetrics.value = r;
    // 默认选中第一个指标
    if (allMetrics.value.length && !picked.value) onPick(allMetrics.value[0].metricCode);
  } catch {}
}

// === 分类规则：优先 metricDesc(JSON)._category，其次按名称关键词推断 ===
function categoryOf(m) {
  const meta = parseMeta(m);
  if (meta && meta._category) {
    const [g, sub] = meta._category.split('/');
    return [g, sub || null];
  }
  const n = (m.metricName || '') + ' ' + (m.metricDesc || '');
  if (/不良|风险/.test(n)) return ['风险指标', '风险类'];
  if (/中收|手续费|fee/i.test(n) || m.metricCode === 'M_FEE') return ['业务指标', '中收类'];
  if (/存款/.test(n)) return ['业务指标', '存款类'];
  if (/贷款/.test(n)) return ['业务指标', '贷款类'];
  if (/客户|cust/i.test(n) || m.baseDim === 'CUST') return ['客户指标', null];
  return ['综合指标', null];
}
function resolveCategory(m) {
  const [g, sub] = categoryOf(m);
  return sub ? `${g}/${sub}` : g;
}

// === 树结构（前端按分类聚合） ===
const treeData = computed(() => {
  const root = new Map();
  for (const m of allMetrics.value) {
    const [g, sub] = categoryOf(m);
    const groupNode = root.get(g) || { id: 'g-' + g, label: g, children: [] };
    root.set(g, groupNode);
    const leaf = {
      id: m.metricCode, label: m.metricName,
      isMetric: true, status: m.status, raw: m
    };
    if (sub) {
      let subNode = groupNode.children.find(c => c.id === 'sub-' + g + sub);
      if (!subNode) {
        subNode = { id: 'sub-' + g + sub, label: sub, children: [] };
        groupNode.children.push(subNode);
      }
      subNode.children.push(leaf);
    } else {
      groupNode.children.push(leaf);
    }
  }
  return Array.from(root.values());
});

// === 详情 ===
const picked = ref('');
const detail = ref({});
// V1.6 修复 Bug3：停用态下"编辑/试运行/立即执行"按钮 disabled，前端先拦截，后端兜底校验
const isDisabled = computed(() => detail.value.status === 'DISABLED' || detail.value.status === 'INACTIVE');
const sameCategory = computed(() => {
  if (!detail.value.metricCode) return [];
  const [g] = categoryOf(detail.value);
  return allMetrics.value.filter(m => categoryOf(m)[0] === g && m.metricCode !== detail.value.metricCode).slice(0, 10);
});

async function onPick(code) {
  if (!code) return;
  picked.value = code;
  try {
    const r = await getMetricDetail(code);
    if (r) detail.value = r;
  } catch {}
}
function onTreeClick(node) {
  if (node.isMetric) onPick(node.id);
}

// === 槽位声明 / 分类元数据：从 metricDesc(JSON) 解析，否则按 SQL 文本兜底 ===
function parseMeta(m) {
  const txt = m.metricDesc || m.description || '';
  if (typeof txt === 'string' && txt.trim().startsWith('{')) {
    try { return JSON.parse(txt); } catch {}
  }
  return null;
}
function resolveSlots(m) {
  const meta = parseMeta(m);
  if (meta && Array.isArray(meta._slots)) return meta._slots;
  // 兼容：旧 description 直接存数组的情况
  const txt = m.metricDesc || m.description || '';
  if (typeof txt === 'string' && txt.trim().startsWith('[')) {
    try { return JSON.parse(txt); } catch {}
  }
  // 默认槽位（DAY 类 SQL 指标常见）
  if (m.calcLogicType === 'SQL' && /#\{datadate\}|#\{period_/i.test(m.sqlText || '')) {
    return [
      { name: 'period_start', type: 'DATE',     required: true,  desc: '起始日期' },
      { name: 'period_end',   type: 'DATE',     required: true,  desc: '截止日期' },
      { name: 'org_scope',    type: 'STRING[]', required: false, desc: '机构范围 · 缺省全行' }
    ];
  }
  return [];
}
function guessDataSource(m) {
  if (m.calcLogicType === 'SUMMARY') return '指标汇总（' + (m.summaryRule || '?') + '）';
  if (m.calcLogicType === 'EXPR')    return '复合指标（' + (m.refMetricCodes || '?') + '）';
  return 'EDW · 业务表';
}

// === 编辑/新增 弹框 ===
const formRef = ref(null);
const dlg = reactive({
  show: false, editing: null, saving: false,
  trialRange: null, trialing: false,
  trial: { status: '', cost: 0, totalRows: 0, errorMsg: '', rows: [], cols: [] },
  slots: [],
  form: {
    metricCode: '', metricName: '', baseDim: 'EMP', metricLevel: 1,
    calcFreq: 'DAY', calcLogicType: 'SQL', calcMode: 'AUTO',
    sqlText: '', exprText: '', summaryRule: '',
    unit: '', decimalPlaces: 2, valSlot: 1, description: '',
    _category: '业务/存款类'
  }
});
function resetTrial() {
  dlg.trial = { status: '', cost: 0, totalRows: 0, errorMsg: '', rows: [], cols: [] };
}
const formRules = {
  metricCode: [{ required: true, message: '编码必填' }],
  metricName: [{ required: true, message: '名称必填' }],
  calcLogicType: [{ required: true, message: '计算方式必选' }]
};

function defaultForm() {
  return {
    metricCode: '', metricName: '', baseDim: 'EMP', metricLevel: 1,
    calcFreq: 'DAY', calcLogicType: 'SQL', calcMode: 'AUTO',
    sqlText: '', exprText: '', summaryRule: '',
    unit: '', decimalPlaces: 2, valSlot: 1, description: '',
    _category: '业务/存款类'
  };
}
function openCreate() {
  dlg.editing = null;
  Object.assign(dlg.form, defaultForm());
  dlg.slots = [
    { name: 'period_start', type: 'DATE', required: true, desc: '起始日期' },
    { name: 'period_end',   type: 'DATE', required: true, desc: '截止日期' }
  ];
  dlg.trialRange = null; resetTrial();
  dlg.show = true;
}
function openEdit(row) {
  dlg.editing = row.metricCode;
  Object.assign(dlg.form, {
    metricCode: row.metricCode, metricName: row.metricName,
    baseDim: row.baseDim, metricLevel: row.metricLevel,
    calcFreq: row.calcFreq, calcLogicType: row.calcLogicType, calcMode: row.calcMode,
    sqlText: row.sqlText || '', exprText: row.exprText || '', summaryRule: row.summaryRule || '',
    unit: row.unit || '', decimalPlaces: row.decimalPlaces ?? 2, valSlot: row.valSlot ?? 1,
    description: row.description || row.metricDesc || '',
    _category: resolveCategory(row)
  });
  dlg.slots = resolveSlots(row);
  dlg.trialRange = null; resetTrial();
  dlg.show = true;
}
function addSlot() {
  dlg.slots.push({ name: '', type: 'DATE', required: false, desc: '' });
}

async function onSave(targetStatus) {
  try { await formRef.value.validate(); } catch { return; }
  dlg.saving = true;
  // V1.6 修复 Bug5：保存失败（如后端返回 400 / 业务错）时**绝不**关闭弹框，
  // 让用户能继续修正字段。错误消息由 http.js 拦截器统一 ElMessage 抛出。
  // 后端 DTO 严格白名单：unit/decimalPlaces/valSlot/status/description 都不接受。
  // 把这些前端附加字段（含分类、槽位声明）打包写入 metricDesc 里，避免 Jackson 报 Unrecognized field。
  const meta = {
    _slots: dlg.slots, _unit: dlg.form.unit,
    _decimal: dlg.form.decimalPlaces, _category: dlg.form._category
  };
  const basePayload = {
    metricCode: dlg.form.metricCode,
    metricName: dlg.form.metricName,
    metricDesc: JSON.stringify(meta),
    // V1.9 metric_category 独立列（前端分类下拉/树聚合靠它，不能只塞 metricDesc JSON）
    metricCategory: dlg.form._category || null,
    calcFreq: dlg.form.calcFreq,
    calcMode: dlg.form.calcMode,
    calcLogicType: dlg.form.calcLogicType,
    sqlText: dlg.form.calcLogicType === 'SQL' ? (dlg.form.sqlText || null) : null,
    exprText: dlg.form.calcLogicType === 'EXPR' ? (dlg.form.exprText || null) : null,
    summaryRule: dlg.form.calcLogicType === 'SUMMARY' ? (dlg.form.summaryRule || null) : null
  };
  try {
    if (dlg.editing) {
      // Update DTO 不含 baseDim/metricLevel/preferredSlot/status
      await updateMetric(dlg.editing, basePayload);
      if (targetStatus !== detail.value.status) {
        await changeMetricStatus(dlg.editing, targetStatus, '编辑保存');
      }
      ElMessage.success(targetStatus === 'ACTIVE' ? '已发布' : '已保存为草稿');
    } else {
      // Create DTO 额外接 baseDim / metricLevel / status；
      // preferredSlot 故意不传——已有指标占满 slot=1 必触发 METRIC_SLOT_CONFLICT，
      // 后端 MetricSlotService.allocSlot 在 preferredSlot=null 时自动找下一个空闲槽位。
      await createMetric({
        ...basePayload,
        baseDim: dlg.form.baseDim,
        metricLevel: dlg.form.metricLevel,
        status: targetStatus   // 一步直接落 ACTIVE / DRAFT，避免两步切换状态机抖动
      });
      ElMessage.success(targetStatus === 'ACTIVE' ? '已新增并发布' : '已保存为草稿');
    }
    dlg.show = false;
    await reload();
    onPick(dlg.editing || dlg.form.metricCode);
  } catch {} finally { dlg.saving = false; }
}

async function onTrialFromDialog() {
  if (!dlg.editing) return ElMessage.warning('请先保存指标后再试运行');
  const yesterday = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
  const dataDate = dlg.trialRange?.[1] || yesterday;
  const params = dlg.trialRange ? { period_start: dlg.trialRange[0], period_end: dlg.trialRange[1] } : {};
  resetTrial();
  dlg.trialing = true;
  const t0 = Date.now();
  try {
    const r = await trialRunMetric(dlg.editing, { dataDate, sampleSize: 10, params });
    // 后端 MetricTrialRespDTO: { status, totalRows, executionMillis, errorMsg, sampleRows }
    const rows = r?.sampleRows || [];
    const cols = rows.length ? Object.keys(rows[0]) : ['cust_id', 'org_id', 'metric_value'];
    dlg.trial = {
      status: r?.status || (rows.length ? 'SUCCESS' : 'FAILED'),
      cost: r?.executionMillis ?? (Date.now() - t0),
      totalRows: r?.totalRows ?? rows.length,
      errorMsg: r?.errorMsg || '',
      rows, cols
    };
  } catch (err) {
    // 业务错误（PERF-42201 SQL 语法错等）：显示失败 tag + 错误文本，方便排查
    dlg.trial = {
      status: 'FAILED', cost: Date.now() - t0, totalRows: 0,
      errorMsg: err?.message || '试运行失败（SQL/EXPR 执行异常）',
      rows: [], cols: []
    };
  } finally { dlg.trialing = false; }
}

// === 详情侧动作 ===
async function onTrialRun() {
  // dataDate 默认昨天（T-1 是 perf 模块习惯）
  const yesterday = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
  try {
    await trialRunMetric(detail.value.metricCode, { dataDate: yesterday, sampleSize: 10 });
    ElMessage.success('试运行已提交，已写入审计日志');
  } catch {}
}

// 立即执行：写宽表 + 写 run_task，必须填原因（高危）
async function onExecute() {
  const yesterday = new Date(Date.now() - 86400_000).toISOString().slice(0, 10);
  let reason;
  try {
    const r = await ElMessageBox.prompt(
      `立即执行 ${detail.value.metricCode}（数据日期 ${yesterday}）？将写入宽表 + run_task，请填写执行原因`,
      '高危：立即执行',
      { type: 'warning', inputPattern: /\S+/, inputErrorMessage: '原因必填' }
    );
    reason = r.value;
  } catch { return; }
  try {
    await executeMetric(detail.value.metricCode, { dataDate: yesterday, cascade: true, async: true, reason });
    ElMessage.success('已触发执行（异步任务），已写入审计日志');
  } catch {}
}

// 跳到审计日志页面，预填筛选 = 本指标的操作流水
function onViewAudit() {
  router.push({
    path: '/system/audit',
    query: {
      bizType: 'PERF_CONFIG',
      keyword: detail.value.metricCode
    }
  });
}
async function onChangeStatus(status) {
  let reason;
  try {
    const r = await ElMessageBox.prompt(`确认将 ${detail.value.metricCode} 切换为 ${status}？请填写原因`, '提示',
      { inputPattern: /\S+/, inputErrorMessage: '原因必填' });
    reason = r.value;
  } catch { return; }
  try {
    await changeMetricStatus(detail.value.metricCode, status, reason);
    ElMessage.success('状态已更新');
    onPick(detail.value.metricCode); reload();
  } catch {}
}
async function onDelete(row) {
  let reason;
  try {
    const r = await ElMessageBox.prompt(`确认删除 ${row.metricCode} (${row.metricName})？请填写删除原因`, '高危',
      { type: 'warning', inputPattern: /\S+/, inputErrorMessage: '原因必填' });
    reason = r.value;
  } catch { return; }
  try {
    await deleteMetric(row.metricCode, reason);
    ElMessage.success('已删除');
    detail.value = {}; picked.value = '';
    reload();
  } catch {}
}

// === 版本历史：复用审计日志，按 metricCode 拉本指标的所有变更（CREATE/UPDATE/STATUS_CHANGE/DELETE） ===
const versionDlg = reactive({ show: false, loading: false, list: [] });
async function onShowVersions() {
  versionDlg.show = true;
  versionDlg.loading = true;
  versionDlg.list = [];
  try {
    const r = await listAuditLogs({
      bizType: 'PERF_CONFIG',
      keyword: detail.value.metricCode,
      pageSize: 50
    });
    if (Array.isArray(r)) {
      // 倒序 + 过滤掉非本指标的命中（keyword 是 LIKE 模糊匹配）
      versionDlg.list = r
        .filter(x => (x.resourceUrl || '').includes(detail.value.metricCode))
        .map((x, i, arr) => ({ ...x, version: 'v' + (arr.length - i) }));
    }
  } catch {} finally { versionDlg.loading = false; }
}

// === 导入指标（占位提示） ===
function onImport() {
  ElMessageBox.alert('指标批量导入请使用左侧菜单【数据导入】模块，选择"指标定义导入"模板。', '导入指标', { type: 'info' });
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 12px;
}
.tree-col {
  padding: 16px;
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.card-h-mini {
  font-size: 14px; font-weight: 600;
  padding: 0 0 12px;
  border-bottom: 1px solid $border-1;
  margin-bottom: 10px;
  color: $text-1;
}
.tree-node {
  display: flex; align-items: center; gap: 6px;
  flex: 1; min-width: 0;
  .ico { font-size: 13px; }
  .tree-leaf { color: $text-1; }
  .tree-tag { margin-left: auto; }
}
.detail-col {
  padding: 18px 22px;
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.empty-pane { padding: 80px 20px; text-align: center; color: $text-3; font-size: 13px; }
.card-h {
  display: flex; align-items: center; gap: 10px;
  padding: 0 0 14px; border-bottom: 1px solid $border-1; margin-bottom: 16px;
  .title { font-size: 16px; font-weight: 600; flex: 1; }
  .version { font-size: 12px; color: $text-3; }
}
.meta-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-bottom: 8px;
  td {
    padding: 10px 14px;
    border: 1px solid $border-1;
  }
  .lab { background: $bg-soft; color: $text-2; width: 110px; }
  .val { color: $text-1; }
}
.block-h {
  font-size: 13px; font-weight: 600; margin: 22px 0 10px; color: $text-1;
}
.code {
  background: #1e293b; color: #f1f5f9; border-radius: 4px; padding: 12px 14px;
  font-family: ui-monospace, monospace; font-size: 12.5px; line-height: 1.6;
  white-space: pre-wrap; overflow: auto; margin: 0;
}
.acts { margin: 22px 0 8px; display: flex; gap: 8px; flex-wrap: wrap; }
.audit-hint {
  margin-top: 6px; padding: 8px 12px;
  background: #fffbeb; border: 1px solid #fed7aa; border-radius: 4px;
  color: #92400e; font-size: 12.5px; line-height: 1.6;
}
.req { color: #16A34A; font-weight: 600; }
.opt { color: $text-3; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }

.form-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 0 18px;
}
.trial-row {
  display: flex; gap: 10px; align-items: center; flex-wrap: wrap;
}
.trial-error {
  margin-top: 12px; padding: 10px 14px;
  background: #fef2f2; border: 1px solid #fecaca; border-radius: 4px;
  color: $danger; font-size: 13px; line-height: 1.6;
}
</style>
