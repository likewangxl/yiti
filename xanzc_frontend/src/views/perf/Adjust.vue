<template>
  <div>
    <div class="page-h">
      <h1>业绩调整 <span class="sub">比例之和 = 100% · 单行 ≥ 1% · 同一员工不重复</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openCreate">+ 新建调整申请</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false"
      title="线下调整后将触发 KPI 历史回算。下表显示在途/已完成的调整申请。"
      style="margin-bottom:12px" />

    <!-- 在途列表 -->
    <div class="card-section table">
      <el-table :data="rows" size="default" empty-text="暂无调整申请" v-loading="loading">
        <el-table-column label="申请编号" width="170">
          <template #default="{row}"><code class="mono">{{ row.id || row.applyNo }}</code></template>
        </el-table-column>
        <el-table-column label="客户" min-width="160">
          <template #default="{row}">{{ row.custName || row.custId || '-' }}</template>
        </el-table-column>
        <el-table-column label="维度" width="100">
          <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ row.allocDim || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="业务类型" width="120">
          <template #default="{row}">{{ row.bizKind || '-' }}</template>
        </el-table-column>
        <el-table-column label="归属机构" width="120">
          <template #default="{row}">{{ row.ownerOrgId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="160">
          <template #default="{row}">{{ row.createdTime || row.time || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openView(row)">查看</el-button>
            <el-popconfirm
              v-if="canWithdraw(row.status)"
              :title="`确认撤回申请 ${row.id || row.applyNo}？`"
              @confirm="onWithdraw(row)">
              <template #reference>
                <el-button link type="danger" size="small">撤回</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建/查看 弹框 -->
    <el-dialog v-model="dlg.show" :title="dlgTitle" width="900px" :close-on-click-modal="false" @closed="onDlgClosed">
      <el-form ref="dlgFormRef" :model="dlg.form" :rules="dlgRules" label-position="top" size="default">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户编号" prop="custId" required>
              <el-input v-model="dlg.form.custId" :disabled="dlg.readOnly" placeholder="如 C20260001" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分配维度" prop="allocDim" required>
              <!-- 后端 ALLOWED_ALLOC_DIMS = Set.of("RULE","ACCOUNT") -->
              <el-select v-model="dlg.form.allocDim" :disabled="dlg.readOnly" style="width:100%">
                <el-option label="按规则分配（RULE）"  value="RULE" />
                <el-option label="按账户分配（ACCOUNT）" value="ACCOUNT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务类型" prop="bizKind" required>
              <!-- 后端 bizKind 字典：CORP_LOAN / CORP_DEPOSIT / CORP_FOREX 等 CORP_* 走对公审批 -->
              <el-select v-model="dlg.form.bizKind" :disabled="dlg.readOnly" style="width:100%">
                <el-option label="对公存款（CORP_DEPOSIT）" value="CORP_DEPOSIT" />
                <el-option label="对公贷款（CORP_LOAN）"    value="CORP_LOAN" />
                <el-option label="对公外汇（CORP_FOREX）"   value="CORP_FOREX" />
                <el-option label="个人存款（PER_DEP）"      value="PER_DEP" />
                <el-option label="个人贷款（PER_LOAN）"     value="PER_LOAN" />
                <el-option label="中间业务（FEE_BIZ）"      value="FEE_BIZ" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="账号">
              <el-input v-model="dlg.form.accountNo" :disabled="dlg.readOnly" placeholder="可空" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="归属机构" prop="ownerOrgId" required>
              <el-input v-model="dlg.form.ownerOrgId" :disabled="dlg.readOnly" placeholder="如 NS001" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="card-h">
          <div class="title">分配明细</div>
          <span class="weight-sum" :class="{ ok: totalPct === 100 }">
            合计：{{ totalPct }}% {{ totalPct === 100 ? '✓' : '' }}
          </span>
        </div>
        <el-table :data="dlg.form.items" size="default" border>
          <el-table-column label="员工号" width="160">
            <template #default="{row}">
              <el-input v-model="row.empId" :disabled="dlg.readOnly" size="small" placeholder="如 E001" />
            </template>
          </el-table-column>
          <el-table-column label="承担比例 %" width="140">
            <template #default="{row}">
              <el-input-number v-model="row.pct" :disabled="dlg.readOnly" :min="0" :max="100" :precision="0" :controls="false" size="small" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="240">
            <template #default="{row}">
              <el-input v-model="row.remark" :disabled="dlg.readOnly" size="small" />
            </template>
          </el-table-column>
          <el-table-column v-if="!dlg.readOnly" label="操作" width="80" align="center">
            <template #default="{$index}">
              <el-button link type="danger" size="small" @click="dlg.form.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button v-if="!dlg.readOnly" plain @click="addItemRow" style="margin-top:10px">+ 添加分配人</el-button>

        <el-form-item label="申请原因" prop="reason" required style="margin-top:14px">
          <el-input v-model="dlg.form.reason" :disabled="dlg.readOnly" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="请说明本次调整原因（必填，将记入审批日志）" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dlg.show = false">{{ dlg.readOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!dlg.readOnly" type="primary" :loading="dlg.saving" @click="onSubmit">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  listAdjusts, submitAdjust, withdrawAdjust, getAdjustDetail
} from '@/api/perf';

const STATUS_LABEL = {
  DRAFT: '草稿', IN_APPROVAL: '审批中', APPROVED: '已通过',
  REJECTED: '已驳回', WITHDRAWN: '已撤回'
};
const statusLabel = (s) => STATUS_LABEL[s] || s || '-';
const statusCls = (s) => ({
  APPROVED: 'tag-success',
  IN_APPROVAL: 'tag-warning',
  DRAFT: 'tag-info',
  REJECTED: 'tag-danger',
  WITHDRAWN: 'tag-info'
}[s] || 'tag-info');
const canWithdraw = (s) => s === 'IN_APPROVAL' || s === 'DRAFT';

// === 列表 ===
const rows = ref([]);
const loading = ref(false);
async function reload() {
  loading.value = true;
  try {
    const r = await listAdjusts({ pageSize: 50 });
    if (Array.isArray(r)) rows.value = r;
  } catch {} finally { loading.value = false; }
}

// === 弹框 ===
const dlgFormRef = ref(null);
const dlg = reactive({
  show: false, readOnly: false, saving: false, viewingId: null,
  form: {
    custId: '', allocDim: 'RULE', bizKind: 'CORP_DEPOSIT',
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '' }]
  }
});
const dlgTitle = computed(() => dlg.readOnly ? '查看调整申请' : '新建调整申请');
const totalPct = computed(() => dlg.form.items.reduce((s, x) => s + (Number(x.pct) || 0), 0));
const dlgRules = {
  custId:     [{ required: true, message: '请填写客户编号' }],
  allocDim:   [{ required: true, message: '请选择分配维度' }],
  bizKind:    [{ required: true, message: '请选择业务类型' }],
  ownerOrgId: [{ required: true, message: '请填写归属机构编码' }],
  reason:     [
    { required: true, message: '请填写申请原因（必填，将记入审批日志）' },
    { max: 500, message: '申请原因不超过 500 字' }
  ]
};

function defaultItem() { return { empId: '', pct: 0, remark: '' }; }
function addItemRow() { dlg.form.items.push(defaultItem()); }
function onDlgClosed() { dlg.viewingId = null; dlg.readOnly = false; }

function openCreate() {
  dlg.readOnly = false;
  dlg.viewingId = null;
  Object.assign(dlg.form, {
    custId: '', allocDim: 'RULE', bizKind: 'CORP_DEPOSIT',
    accountNo: '', ownerOrgId: '', reason: '',
    items: [{ empId: '', pct: 100, remark: '' }]
  });
  dlg.show = true;
}
async function openView(row) {
  dlg.readOnly = true;
  dlg.viewingId = row.id || row.applyNo;
  // 优先用列表数据回显，详情接口异步覆盖
  Object.assign(dlg.form, {
    custId: row.custId || '',
    allocDim: row.allocDim || 'RULE',
    bizKind: row.bizKind || 'CORP_DEPOSIT',
    accountNo: row.accountNo || '',
    ownerOrgId: row.ownerOrgId || '',
    reason: row.reason || '',
    items: row.items?.length ? [...row.items] : [{ empId: '', pct: 100, remark: '' }]
  });
  dlg.show = true;
  try {
    const d = await getAdjustDetail(dlg.viewingId);
    if (d?.id) Object.assign(dlg.form, {
      custId: d.custId, allocDim: d.allocDim, bizKind: d.bizKind,
      accountNo: d.accountNo, ownerOrgId: d.ownerOrgId, reason: d.reason,
      items: (d.items || []).map(it => ({ empId: it.empId, pct: it.pct ?? it.shareRatio, remark: it.remark }))
    });
  } catch {}
}

async function onSubmit() {
  try { await dlgFormRef.value.validate(); } catch { return; }
  // 业务级校验（消息全中文）
  if (!dlg.form.items.length) return ElMessage.warning('至少添加 1 条分配明细');
  for (let i = 0; i < dlg.form.items.length; i++) {
    const it = dlg.form.items[i];
    if (!it.empId) return ElMessage.warning(`第 ${i + 1} 行：请填写员工号`);
    if (!(it.pct >= 1)) return ElMessage.warning(`第 ${i + 1} 行：承担比例须 ≥ 1%`);
  }
  // 同一员工不重复
  const seen = new Set();
  for (const it of dlg.form.items) {
    if (seen.has(it.empId)) return ElMessage.warning(`员工 ${it.empId} 出现多次，请合并`);
    seen.add(it.empId);
  }
  if (totalPct.value !== 100) return ElMessage.warning(`分配比例合计须为 100%，当前 ${totalPct.value}%`);

  dlg.saving = true;
  try {
    await submitAdjust({
      custId:     dlg.form.custId,
      allocDim:   dlg.form.allocDim,
      bizKind:    dlg.form.bizKind,
      accountNo:  dlg.form.accountNo || undefined,
      ownerOrgId: dlg.form.ownerOrgId,
      reason:     dlg.form.reason,
      // 后端 AllocAdjustCreateReqDTO.Item 仅识别 2 字段：empId + ratio
      // remark 不在 DTO 中（之前误带 shareRatio + remark 都会被 Jackson 拒绝）
      items: dlg.form.items.map(it => ({
        empId: it.empId,
        ratio: Number(it.pct)
      }))
    });
    ElMessage.success('已提交审批');
    dlg.show = false;
    reload();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '提交失败');
  } finally { dlg.saving = false; }
}

async function onWithdraw(row) {
  let reason;
  try {
    const r = await ElMessageBox.prompt('请填写撤回原因', '撤回申请', {
      type: 'warning', inputPattern: /\S+/, inputErrorMessage: '撤回原因必填'
    });
    reason = r.value;
  } catch { return; }
  try {
    await withdrawAdjust(row.id || row.applyNo, reason);
    ElMessage.success('已撤回');
    reload();
  } catch (err) {
    ElMessage.error(err?.bizMsg || err?.message || '撤回失败');
  }
}

onMounted(reload);
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.table { padding: 0; padding-bottom: 12px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }

.card-h {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0 10px;
  border-bottom: 1px solid $border-1;
  margin: 8px 0 14px;
  .title { font-size: 14px; font-weight: 600; color: $text-1; flex: 1; }
  .weight-sum {
    font-size: 13px; color: $text-3; font-weight: 500;
    &.ok { color: $success; font-weight: 700; }
  }
}
</style>
