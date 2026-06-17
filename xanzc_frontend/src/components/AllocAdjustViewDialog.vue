<!--
  查看调整申请（只读弹框，共享组件）
  以「业绩调整 / 已审批 / 查看申请」原生视图为准 1:1 复刻；业绩调整页与业绩分配查询页共用，内容完全一致。
  按 applyId 自调 perf 接口：getAdjustDetail + getAdjustApprovalHistory + getAllocPreview。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="v => emit('update:modelValue', v)"
    title="查看调整申请" width="900px" :close-on-click-modal="false">
    <div v-loading="loading">
      <el-form label-position="top" size="default">
        <!-- 申请信息条 -->
        <el-descriptions class="apply-info-bar" :column="3" size="small" border>
          <el-descriptions-item label="申请单号">
            <code class="mono">{{ applyNo || '-' }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">
            <span>{{ createdByName || createdByUsername || createdBy || '-' }}</span>
            <span v-if="createdByUsername" class="sub-id">（{{ createdByUsername }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="申请机构">{{ createdByOrgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="3">{{ fmt(createdTime) }}</el-descriptions-item>
        </el-descriptions>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="客户类型">
              <el-select v-model="form.custType" disabled style="width:100%">
                <el-option label="对公客户" value="CORP" />
                <el-option label="零售客户" value="RETAIL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户编号">
              <el-input v-model="form.custId" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户名称">
              <el-input v-model="form.custName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分配维度">
              <el-select v-model="form.allocDim" disabled style="width:100%">
                <el-option label="按规则分配" value="RULE" />
                <el-option label="按账户分配" value="ACCOUNT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务类型">
              <el-select v-model="form.bizKind" disabled multiple style="width:100%">
                <el-option v-for="o in bizKindOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="账号">
              <el-input v-model="form.accountNo" disabled />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 余额概览：按业务类型动态显隐 -->
        <template v-if="showDepositBal">
          <div class="bal-sub">存款余额</div>
          <el-row :gutter="16" style="margin-bottom:4px">
            <el-col :span="6"><el-form-item label="当前余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_001) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上日余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_002) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="年均余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_003) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上年均余额"><span class="bal-val">{{ fmtAmt(custIdx.MC_004) }}</span></el-form-item></el-col>
          </el-row>
        </template>
        <template v-if="showLoanBal">
          <div class="bal-sub">贷款余额</div>
          <el-row :gutter="16" style="margin-bottom:4px">
            <el-col :span="6"><el-form-item label="当前余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_005) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上日余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_006) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="年均余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_007) }}</span></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="较上年均余额"><span class="bal-val">{{ fmtAmt(custIdxLoan.MC_008) }}</span></el-form-item></el-col>
          </el-row>
        </template>

        <!-- 原业绩分配 -->
        <div class="preview-section" v-loading="preview.loading">
          <div class="card-h">
            <div class="title">原业绩分配</div>
          </div>
          <el-table v-if="hasOriginalOwners" :data="preview.data.allocList || []" size="small" border style="margin-bottom:12px" empty-text="暂无审批通过的分配记录">
            <el-table-column prop="acctNo" label="账号" min-width="150" show-overflow-tooltip>
              <template #default="{row}">{{ row.acctNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="员工名称" min-width="150" show-overflow-tooltip>
              <template #default="{row}">{{ row.username || '-' }}{{ row.empChnName ? '（' + row.empChnName + '）' : '' }}</template>
            </el-table-column>
            <el-table-column label="所属机构" min-width="180" show-overflow-tooltip>
              <template #default="{row}">{{ row.orgName || '-' }}{{ row.orgCode ? '（' + row.orgCode + '）' : '' }}</template>
            </el-table-column>
            <el-table-column prop="ratio" label="分配比例" width="100">
              <template #default="{row}">{{ row.ratio != null && row.ratio !== '' ? row.ratio + '%' : '-' }}</template>
            </el-table-column>
          </el-table>
          <el-table v-else :data="form.originalItems" size="small" border style="margin-bottom:12px" empty-text="无原业绩分配">
            <el-table-column label="账号" min-width="140">
              <template #default="{row}">{{ row.acctNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="员工名称" min-width="200">
              <template #default="{row}">{{ row.username || row.empId || '-' }}{{ row.empChnName ? '（' + row.empChnName + '）' : '' }}</template>
            </el-table-column>
            <el-table-column label="所属机构" min-width="220">
              <template #default="{row}">{{ row.orgName || '-' }}{{ row.orgCode ? '（' + row.orgCode + '）' : '' }}</template>
            </el-table-column>
            <el-table-column label="分配比例" width="120">
              <template #default="{row}">{{ row.ratio != null && row.ratio !== '' ? row.ratio + '%' : '-' }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card-h">
          <div class="title">分配明细</div>
        </div>
        <el-table :data="form.items" size="default" border>
          <el-table-column label="员工号" min-width="220">
            <template #default="{row}">
              <el-autocomplete v-model="row.empLabel" disabled size="small" style="width:100%" :fetch-suggestions="noFetch" />
            </template>
          </el-table-column>
          <el-table-column label="承担比例 %" width="140">
            <template #default="{row}">
              <el-input-number v-model="row.pct" disabled :min="0" :max="100" :precision="0" :controls="false" size="small" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="240">
            <template #default="{row}">
              <el-input v-model="row.remark" disabled size="small" />
            </template>
          </el-table-column>
        </el-table>

        <el-form-item label="申请原因" style="margin-top:14px">
          <el-input v-model="form.reason" disabled type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>

      <!-- 审批流记录 -->
      <div class="card-h">
        <div class="title">审批流记录</div>
        <span class="sub-tip">按时间倒序 · 最新在上</span>
      </div>
      <div v-loading="approvalLoading" class="approval-wrap">
        <el-empty v-if="!approvalLoading && (!approvalLogs || approvalLogs.length === 0)"
          description="暂无审批记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="(log, idx) in approvalLogs" :key="idx"
            :timestamp="fmt(log.operateTime)" placement="top"
            :type="actionTimelineType(log.action)" :hollow="idx !== 0">
            <div class="approval-line">
              <el-tag :class="actionCls(log.action)" effect="plain" size="small">{{ actionLabel(log.action) }}</el-tag>
              <span class="node">{{ log.nodeName || log.nodeKey || '-' }}</span>
            </div>
            <div class="approval-meta">
              <span class="meta-key">审核人：</span>
              <span>{{ log.operatorName || log.operatorEmpNo || log.operator || '-' }}</span>
              <span v-if="log.operatorEmpNo" class="sub-id">（{{ log.operatorEmpNo }}）</span>
              <span class="meta-sep">·</span>
              <span class="meta-key">机构：</span>
              <span>{{ log.operatorOrgName || '-' }}</span>
            </div>
            <div v-if="log.action !== 'SUBMIT'" class="approval-opinion">意见：{{ log.opinion || '（未填写）' }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { getAdjustDetail, getAdjustApprovalHistory, getAllocPreview } from '@/api/perf';
import { listDictItems } from '@/api/system';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  applyId: { type: String, default: '' },
});
const emit = defineEmits(['update:modelValue']);

const loading = ref(false);
const applyNo = ref('');
const createdBy = ref('');
const createdByName = ref('');
const createdByUsername = ref('');
const createdByOrgName = ref('');
const createdTime = ref(null);
const form = reactive({ custType: '', custId: '', custName: '', allocDim: '', bizKind: [], accountNo: '', reason: '', items: [], originalItems: [] });
const custIdx = reactive({ MC_001: null, MC_002: null, MC_003: null, MC_004: null });
const custIdxLoan = reactive({ MC_005: null, MC_006: null, MC_007: null, MC_008: null });
const preview = reactive({ loading: false, data: null });
const approvalLogs = ref([]);
const approvalLoading = ref(false);
const bizKindOptions = ref([]);

const noFetch = (q, cb) => cb && cb([]);

// ===== 字典/标签 helper（与 Adjust.vue 同口径）=====
const BIZ_KIND_FALLBACK = {
  CORP_DEPOSIT: '对公存款', CORP_LOAN: '对公贷款', CORP_FOREX: '对公外汇',
  CORP_LARGE_CD: '大额存单', FEE_BIZ: '中间业务', PER_DEP: '个人存款', PER_LOAN: '个人贷款',
};
const bizKindMap = computed(() => {
  const m = {};
  for (const o of bizKindOptions.value) m[o.value] = o.label;
  return m;
});
function bizKindLabel(k) { return bizKindMap.value[k] || BIZ_KIND_FALLBACK[k] || k || ''; }
const showDepositBal = computed(() => (form.bizKind || []).some(k => bizKindLabel(k).includes('存')));
const showLoanBal = computed(() => (form.bizKind || []).some(k => bizKindLabel(k).includes('贷')));
const hasOriginalOwners = computed(() => (preview.data && preview.data.allocList && preview.data.allocList.length) > 0);

const ACTION_LABEL = { SUBMIT: '提交', APPROVE: '通过', REJECT: '驳回', CLAIM: '签收', TRANSFER: '转办' };
const actionLabel = (a) => ACTION_LABEL[a] || a || '-';
const actionCls = (a) => ({ APPROVE: 'tag-success', REJECT: 'tag-danger', SUBMIT: 'tag-info', CLAIM: 'tag-warning', TRANSFER: 'tag-warning' }[a] || 'tag-info');
const actionTimelineType = (a) => ({ APPROVE: 'success', REJECT: 'danger', SUBMIT: 'primary', CLAIM: 'warning', TRANSFER: 'warning' }[a] || 'info');

const fmt = (s) => {
  if (!s) return '-';
  const str = String(s).replace('T', ' ');
  return str.length >= 16 ? str.substring(0, 16) : str;
};
function fmtAmt(v) {
  if (v == null) return '-';
  const n = Number(v);
  return isNaN(n) ? v : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function inferCustType(custType, bizKind) {
  if (custType) return custType;
  const bk = typeof bizKind === 'string' ? bizKind : (Array.isArray(bizKind) ? bizKind[0] || '' : '');
  if (bk.startsWith('CORP')) return 'CORP';
  if (bk.startsWith('RETAIL') || bk.startsWith('PER') || bk.startsWith('FEE')) return 'RETAIL';
  return '';
}
function empLabelOf(it) {
  if (it && it.username) return it.username + (it.empChnName ? '（' + it.empChnName + '）' : '');
  return (it && it.empId) || '';
}
function originalItemsFromDetail(d) {
  return (d.items || []).filter(it => (it.itemKind || 'NEW') === 'ORIGIN').map(it => ({
    acctNo: it.acctNo || '', empId: it.empId || '', username: it.username || '', empChnName: it.empChnName || '',
    orgCode: it.orgCode || '', orgName: it.orgName || '', ratio: it.ratio,
  }));
}

async function loadBizKindDict() {
  try {
    const items = await listDictItems('PERF_BIZ_KIND');
    bizKindOptions.value = (Array.isArray(items) ? items : []).map(d => ({ label: d.dictLabel, value: d.dictCode }));
  } catch { bizKindOptions.value = []; }
}
async function loadPreview(statisDt) {
  if (!form.custId) { preview.data = null; return; }
  preview.loading = true;
  try {
    preview.data = await getAllocPreview({
      custType: form.custType, custNo: form.custId, allocDim: form.allocDim,
      accountNo: form.accountNo || undefined, statisDt,
    });
  } catch { preview.data = null; } finally { preview.loading = false; }
}

function resetAll() {
  Object.assign(form, { custType: '', custId: '', custName: '', allocDim: '', bizKind: [], accountNo: '', reason: '', items: [], originalItems: [] });
  Object.assign(custIdx, { MC_001: null, MC_002: null, MC_003: null, MC_004: null });
  Object.assign(custIdxLoan, { MC_005: null, MC_006: null, MC_007: null, MC_008: null });
  preview.data = null;
  approvalLogs.value = [];
  applyNo.value = ''; createdBy.value = ''; createdByName.value = ''; createdByUsername.value = ''; createdByOrgName.value = ''; createdTime.value = null;
}

async function load(id) {
  resetAll();
  if (!id) return;
  loadBizKindDict();
  loading.value = true;
  try {
    const d = (await getAdjustDetail(id)) || {};
    Object.assign(form, {
      custType: inferCustType(d.custType, d.bizKind),
      custId: d.custId || '', custName: d.custName || '', allocDim: d.allocDim || '',
      bizKind: d.bizKind ? (typeof d.bizKind === 'string' ? d.bizKind.split(',') : d.bizKind) : [],
      accountNo: d.accountNo || '', reason: d.reason || d.remark || '',
      items: (d.items || []).filter(it => (it.itemKind || 'NEW') === 'NEW').map(it => ({ empLabel: empLabelOf(it), pct: it.pct ?? it.ratio, remark: it.remark || '' })),
      originalItems: originalItemsFromDetail(d),
    });
    Object.assign(custIdx, { MC_001: d.currBal ?? null, MC_002: d.mAvgBal ?? null, MC_003: d.qAvgBal ?? null, MC_004: d.yAvgBal ?? null });
    Object.assign(custIdxLoan, { MC_005: d.loanCurrBal ?? null, MC_006: d.loanMAvgBal ?? null, MC_007: d.loanQAvgBal ?? null, MC_008: d.loanYAvgBal ?? null });
    applyNo.value = d.applyNo || '';
    createdBy.value = d.createdBy || '';
    createdByName.value = d.createdByName || '';
    createdByUsername.value = d.createdByUsername || '';
    createdByOrgName.value = d.createdByOrgName || '';
    createdTime.value = d.createdTime || null;
    // 原业绩分配预览：申请日期 - 1
    let dt;
    if (d.createdTime) { const ad = new Date(d.createdTime); ad.setDate(ad.getDate() - 1); dt = ad.toISOString().slice(0, 10); }
    loadPreview(dt);
  } finally {
    loading.value = false;
  }
  approvalLoading.value = true;
  try {
    const list = await getAdjustApprovalHistory(id);
    approvalLogs.value = Array.isArray(list) ? list : [];
  } catch { approvalLogs.value = []; } finally { approvalLoading.value = false; }
}

watch(() => [props.modelValue, props.applyId], ([show, id]) => {
  if (show && id) load(id);
}, { immediate: true });
</script>

<style lang="scss" scoped>
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.sub-id { font-size: 12px; color: $text-3; margin-left: 4px; }
.bal-sub { font-size: 13px; font-weight: 600; color: $text-2; margin: 4px 0 6px; }
.bal-val { display: inline-block; font-size: 14px; font-weight: 600; color: $text-1; line-height: 32px; }
.preview-section { margin-bottom: 12px; }
.card-h {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 0 10px; border-bottom: 1px solid $border-1; margin: 8px 0 14px;
  .title { font-size: 14px; font-weight: 600; color: $text-1; flex: 1; }
  .sub-tip { font-size: 12px; color: $text-3; }
}
.apply-info-bar {
  margin: 4px 0 16px;
  :deep(.el-descriptions__label) { width: 90px; }
  code.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
}
.approval-wrap {
  padding: 4px 0 4px 6px; min-height: 80px;
  .approval-line { display: flex; align-items: center; gap: 8px; font-size: 13px;
    .node { font-weight: 600; color: $text-1; } }
  .approval-meta { margin-top: 4px; font-size: 12px; color: $text-2;
    .meta-key { color: $text-3; } .meta-sep { margin: 0 8px; color: $text-3; } }
  .approval-opinion { margin-top: 4px; font-size: 12px; color: $text-2; background: $bg-soft; padding: 6px 8px; border-radius: 4px; word-break: break-all; }
}
</style>
