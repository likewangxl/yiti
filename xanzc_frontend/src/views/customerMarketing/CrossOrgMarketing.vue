<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div>
        <PageTitle />
        <span>审批通过只获得触达权限，不改变主办/绩效关系，不会迁移主办人、主办机构或绩效归属。</span>
      </div>
      <el-button type="primary" @click="formVisible = !formVisible">
        {{ formVisible ? '收起申请' : '发起申请' }}
      </el-button>
    </header>

    <el-card v-show="formVisible" shadow="never" class="apply-card">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="客户号" required>
          <div class="lookup">
            <el-input
              v-model="customerKey"
              placeholder="请输入客户号"
              clearable
              @keyup.enter="runValidate"
            />
            <el-button type="primary" :loading="validating" @click="runValidate">校验申请条件</el-button>
          </div>
        </el-form-item>

        <template v-if="validation">
          <div class="customer-summary">
            <div><small>客户名称</small><strong>{{ customerName(validation) }}</strong></div>
            <div><small>客户号</small><strong>{{ validation.custNo || validation.customerNo || '-' }}</strong></div>
            <div><small>主办人</small><strong>{{ managerName(validation) }}</strong></div>
            <div><small>主办机构</small><strong>{{ orgName(validation) }}</strong></div>
            <div class="performance"><small>业绩关系</small><strong>{{ performanceSummary(validation) }}</strong></div>
          </div>

          <div class="checks" aria-label="跨机构营销四项规则">
            <article
              v-for="item in checksOf(validation)"
              :key="item.ruleCode || item.ruleName"
              :class="checkClass(item)"
            >
              <span class="check-mark">{{ checkStateLabel(item) }}</span>
              <div>
                <strong>{{ item.ruleName }}</strong>
                <small>{{ checkDescription(item) }}</small>
              </div>
            </article>
          </div>
        </template>

        <el-form-item label="申请原因与联合营销计划" required>
          <el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
        <div class="actions">
          <el-button
            type="primary"
            :disabled="!validation?.valid || !reason.trim()"
            :loading="submitting"
            @click="submit"
          >提交审批</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="list-head">
        <div>
          <strong>跨机构营销申请</strong>
          <span>审批通过只授予申请人触达权限，不改变主办/绩效关系。</span>
        </div>
        <el-radio-group v-model="status" @change="changeStatus">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="IN_APPROVAL">待审批</el-radio-button>
          <el-radio-button value="APPROVED">已通过</el-radio-button>
          <el-radio-button value="REJECTED">已退回</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="applyNo" label="申请编号" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户名称" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ customerName(row) }}</template>
        </el-table-column>
        <el-table-column label="客户号" min-width="155" show-overflow-tooltip>
          <template #default="{ row }">{{ row.custNo || row.customerNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="申请人 / 申请机构" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stack">
              <strong>{{ applicantName(row) }}</strong>
              <small>{{ applicantOrgName(row) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="主办人 / 主办机构" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stack">
              <strong>{{ managerName(row) }}</strong>
              <small>{{ orgName(row) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="业绩关系" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">{{ performanceSummary(row) }}</template>
        </el-table-column>
        <el-table-column prop="applyReason" label="申请原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="申请时间" min-width="165">
          <template #default="{ row }">{{ formatTime(row.createdTime || row.applyTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" class-name="operation-cell">
          <template #default="{ row }">
            <template v-if="row.status === 'IN_APPROVAL' && row.canReview === true">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">退回</el-button>
            </template>
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50]"
          @change="load"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="跨机构营销申请详情" size="600px">
      <div v-if="detailLoading" class="detail-loading">正在加载详情…</div>
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item label="客户">
          {{ customerName(selected) }}（{{ selected.custNo || selected.customerNo || '-' }}）
        </el-descriptions-item>
        <el-descriptions-item label="主办人 / 主办机构">
          {{ managerName(selected) }} · {{ orgName(selected) }}
        </el-descriptions-item>
        <el-descriptions-item label="业绩关系">{{ performanceSummary(selected) }}</el-descriptions-item>
        <el-descriptions-item label="四项冻结快照">
          <div class="snapshot-list">
            <div v-for="item in checksOf(selected)" :key="item.ruleCode || item.ruleName" :class="checkClass(item)">
              <span>{{ checkStateLabel(item) }}</span>
              <strong>{{ item.ruleName }}</strong>
              <small>{{ checkDescription(item) }}</small>
            </div>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="申请原因">{{ selected.applyReason || selected.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见">{{ approvalOpinion(selected) }}</el-descriptions-item>
        <el-descriptions-item label="退回原因">{{ selected.rejectReason || selected.returnReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生成触达任务">{{ selected.generatedTouchTaskId || selected.generatedTouchTaskNo || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  approveCrossOrgMarketing,
  createCrossOrgMarketing,
  getCrossOrgMarketing,
  listCrossOrgMarketing,
  rejectCrossOrgMarketing,
  validateCrossOrgMarketing,
} from '@/api/customerMarketing';

const formVisible = ref(true);
const customerKey = ref('');
const reason = ref('');
const validation = ref(null);
const validating = ref(false);
const submitting = ref(false);
const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const status = ref('');
const selected = ref(null);
const detailVisible = ref(false);
const detailLoading = ref(false);

const CHECK_RULES = [
  {
    ruleCode: 'APPLICANT_NOT_MAIN_MANAGER',
    ruleName: '申请人不是主办客户经理',
    keys: ['applicantNotMainCheck', 'applicantIsNotMainManager', 'applicantNotMainManager'],
  },
  {
    ruleCode: 'MAIN_ORG_DIFFERENT',
    ruleName: '主办机构与申请机构不同',
    keys: ['mainOrgDifferentCheck', 'ownerCrossOrgCheck', 'applicantOrgDifferent'],
  },
  {
    ruleCode: 'APPLICANT_NO_PERFORMANCE',
    ruleName: '申请人无该客户业绩归属',
    keys: ['applicantNoPerformanceCheck', 'performanceRelationCheck', 'applicantHasNoPerformance'],
  },
  {
    ruleCode: 'APPLICANT_ORG_NO_PERFORMANCE',
    ruleName: '申请机构无该客户业绩归属',
    keys: ['applicantOrgNoPerformanceCheck', 'applicantOrgPerformanceRelationCheck'],
  },
];

function pageOf(result) {
  if (Array.isArray(result)) return { records: result, total: result.length };
  const candidates = [result, result?.page, result?.data, result?.data?.page, result?.result];
  const page = candidates.find(item => Array.isArray(item?.records)
    || Array.isArray(item?.list)
    || Array.isArray(item?.content)
    || Array.isArray(item?.rows));
  if (!page) return { records: [], total: 0 };
  const records = page.records || page.list || page.content || page.rows;
  return { records, total: Number(page.total ?? page.totalCount ?? records.length) || 0 };
}

async function load() {
  loading.value = true;
  try {
    const result = await listCrossOrgMarketing({
      status: status.value || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    });
    const page = pageOf(result);
    rows.value = page.records;
    total.value = page.total;
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function changeStatus() {
  pageNo.value = 1;
  load();
}

async function runValidate() {
  const custNo = customerKey.value.trim();
  if (!custNo) {
    ElMessage.warning('请输入客户号');
    return;
  }
  validating.value = true;
  validation.value = null;
  try {
    validation.value = await validateCrossOrgMarketing(custNo);
  } catch (error) {
    ElMessage.error(`客户号校验失败：${error?.message || '请稍后重试'}`);
  } finally {
    validating.value = false;
  }
}

async function submit() {
  if (!validation.value?.valid || !reason.value.trim()) return;
  if (!validation.value.custId) {
    ElMessage.error('校验结果缺少客户标识，请重新校验客户号');
    return;
  }
  submitting.value = true;
  try {
    await createCrossOrgMarketing({
      custId: validation.value.custId,
      reason: reason.value.trim(),
    });
    ElMessage.success('申请已提交审批');
    reason.value = '';
    validation.value = null;
    customerKey.value = '';
    await load();
  } catch (error) {
    ElMessage.error(`提交审批失败：${error?.message || '请稍后重试'}`);
  } finally {
    submitting.value = false;
  }
}

async function openDetail(row) {
  selected.value = row;
  detailVisible.value = true;
  detailLoading.value = true;
  const id = row?.id || row?.applyId;
  if (!id) {
    detailLoading.value = false;
    return;
  }
  try {
    const detail = await getCrossOrgMarketing(id);
    if (detail) selected.value = { ...row, ...detail };
  } catch (error) {
    ElMessage.error(`详情加载失败：${error?.message || '请稍后重试'}`);
  } finally {
    detailLoading.value = false;
  }
}

async function approve(row) {
  if (row?.canReview !== true) {
    ElMessage.warning('当前用户无审批权限');
    return;
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入审批意见',
      '跨机构营销审批',
      {
        inputType: 'textarea',
        inputPlaceholder: '请输入审批意见（必填）',
        inputAttrs: { maxlength: 500 },
        inputValidator: value => !!value?.trim() || '审批意见不能为空',
        inputErrorMessage: '审批意见不能为空',
      },
    );
    const opinion = typeof value === 'string' ? value.trim() : '';
    if (!opinion) {
      ElMessage.warning('审批意见不能为空');
      return;
    }
    await approveCrossOrgMarketing(row.id || row.applyId, opinion);
    ElMessage.success('审批通过，已获得触达权限');
    await load();
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(`审批通过失败：${error?.message || '请稍后重试'}`);
  }
}

async function reject(row) {
  if (row?.canReview !== true) {
    ElMessage.warning('当前用户无审批权限');
    return;
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入退回原因',
      '退回申请',
      {
        inputType: 'textarea',
        inputAttrs: { maxlength: 500 },
        inputValidator: value => !!value?.trim() || '退回原因不能为空',
        inputErrorMessage: '退回原因不能为空',
      },
    );
    const rejectReason = typeof value === 'string' ? value.trim() : '';
    if (!rejectReason) {
      ElMessage.warning('退回原因不能为空');
      return;
    }
    await rejectCrossOrgMarketing(row.id || row.applyId, rejectReason);
    ElMessage.success('申请已退回');
    await load();
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(`退回申请失败：${error?.message || '请稍后重试'}`);
  }
}

function customerName(row) {
  return row?.custName || row?.customerName || row?.customerDisplayName || (row?.custNo || row?.customerNo ? '客户名称已脱敏' : '-');
}

function managerName(row) {
  return row?.mainManagerName || row?.ownerManagerName || row?.mainManagerDisplayName || row?.managerName || '主办人已脱敏';
}

function orgName(row) {
  return row?.mainOrgName || row?.ownerOrgName || row?.mainOrgDisplayName || row?.orgName || '主办机构已脱敏';
}

function applicantName(row) {
  return row?.applicantName || row?.applicantDisplayName || '申请人已脱敏';
}

function applicantOrgName(row) {
  return row?.applicantOrgName || row?.applicantOrgDisplayName || '申请机构已脱敏';
}

function performanceSummary(row) {
  const value = row?.performanceRelationSummary || row?.performanceSummary || row?.performanceRelations;
  if (Array.isArray(value)) {
    return value.map(item => typeof item === 'string' ? item : item?.displayName || item?.name || item?.empName || item?.orgName).filter(Boolean).join('、') || '暂无绩效相关人员';
  }
  if (value != null && value !== '') return String(value);
  const summary = [];
  if (typeof row?.hasApplicantPerformance === 'boolean') {
    summary.push(`申请人${row.hasApplicantPerformance ? '有' : '无'}业绩归属`);
  } else if (row?.applicantNoPerformanceCheck != null) {
    summary.push(`申请人${Number(row.applicantNoPerformanceCheck) === 1 ? '无' : '有'}业绩归属`);
  }
  if (typeof row?.hasApplicantOrgPerformance === 'boolean') {
    summary.push(`申请机构${row.hasApplicantOrgPerformance ? '有' : '无'}业绩归属`);
  } else if (row?.applicantOrgNoPerformanceCheck != null) {
    summary.push(`申请机构${Number(row.applicantOrgNoPerformanceCheck) === 1 ? '无' : '有'}业绩归属`);
  }
  return summary.join('；') || '暂无绩效相关人员';
}

function normalizePassed(value) {
  if (value === true || value === 1 || value === '1' || value === 'true' || value === 'PASS' || value === 'PASSED') return true;
  if (value === false || value === 0 || value === '0' || value === 'false' || value === 'FAIL' || value === 'FAILED') return false;
  return null;
}

function normalizeCheck(item, fallback) {
  if (!item || typeof item !== 'object') return { ...fallback, passed: normalizePassed(item) };
  return {
    ...fallback,
    ...item,
    ruleCode: item.ruleCode || item.code || fallback.ruleCode,
    ruleName: item.ruleName || item.name || fallback.ruleName,
    passed: normalizePassed(item.passed ?? item.pass ?? item.ok ?? item.result),
    message: item.message || item.remark || item.reason || '',
    dataSource: item.dataSource || item.source || '',
  };
}

function checksOf(source) {
  if (!source) return [];
  const raw = source.checkSnapshot ?? source.checks ?? source.ruleChecks ?? source.frozenChecks ?? source.ruleResults;
  if (Array.isArray(raw)) {
    const mapped = CHECK_RULES.map((rule, index) => {
      const item = raw.find(entry => entry?.ruleCode === rule.ruleCode || entry?.code === rule.ruleCode || entry?.ruleName === rule.ruleName || entry?.name === rule.ruleName) || raw[index];
      return normalizeCheck(item, rule);
    });
    return raw.length > CHECK_RULES.length ? mapped.concat(raw.slice(CHECK_RULES.length).map(item => normalizeCheck(item, { ruleCode: item?.ruleCode || item?.code, ruleName: item?.ruleName || item?.name || '其他规则' }))) : mapped;
  }
  if (raw && typeof raw === 'object') {
    return CHECK_RULES.map(rule => normalizeCheck(raw[rule.ruleCode] ?? raw[rule.ruleName], rule));
  }
  return CHECK_RULES.map(rule => {
    const key = rule.keys.find(candidate => source[candidate] !== undefined);
    return normalizeCheck(key ? source[key] : null, rule);
  });
}

function checkStateLabel(item) {
  return item?.passed === true ? '满足' : item?.passed === false ? '不满足' : '待返回';
}

function checkClass(item) {
  return item?.passed === true ? 'pass' : item?.passed === false ? 'fail' : 'pending';
}

function checkDescription(item) {
  return [item?.message, item?.dataSource].filter(Boolean).join(' · ') || '后端规则快照';
}

function approvalOpinion(row) {
  return row?.approvalOpinion || row?.reviewOpinion || row?.reviewComment || row?.reviewRemark || '-';
}

function statusLabel(value) {
  return { DRAFT: '草稿', IN_APPROVAL: '审批中', APPROVED: '已通过', REJECTED: '已退回' }[value] || value || '-';
}

function statusType(value) {
  return { DRAFT: 'info', IN_APPROVAL: 'warning', APPROVED: 'success', REJECTED: 'danger' }[value] || 'info';
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

load();
</script>

<style scoped lang="scss">
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 14px; }
.page-head h1 { margin: 0; font-size: 18px; }
.page-head span, .list-head > div > span { display: block; margin-top: 4px; font-size: 12px; color: #909399; }
.apply-card { margin-bottom: 14px; border-top: 3px solid #1f5b8f; }
.lookup { display: flex; width: 100%; gap: 10px; }
.customer-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; padding: 12px; margin-bottom: 14px; background: #f7f9fc; }
.customer-summary div, .stack { display: flex; flex-direction: column; gap: 3px; }
.customer-summary small, .stack small { font-size: 11px; color: #909399; }
.performance { grid-column: span 2; }
.checks { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 16px; }
.checks article { display: flex; align-items: flex-start; gap: 9px; padding: 12px; border: 1px solid #dcdfe6; border-radius: 4px; }
.checks article > div { display: flex; flex: 1; flex-direction: column; gap: 3px; }
.checks small { color: #909399; }
.checks .pass, .snapshot-list .pass { color: #27864b; background: #f4fbf6; }
.checks .fail, .snapshot-list .fail { color: #b76a17; background: #fff8ef; }
.checks .pending, .snapshot-list .pending { color: #606266; background: #f5f7fa; }
.check-mark { flex: none; font-size: 12px; font-weight: 600; }
.actions { display: flex; justify-content: flex-end; }
.list-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; margin-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.snapshot-list { display: grid; gap: 8px; }
.snapshot-list > div { display: grid; grid-template-columns: 52px 1fr; gap: 4px 8px; padding: 8px; border-radius: 4px; }
.snapshot-list small { grid-column: 2; color: #909399; }
.detail-loading { margin-bottom: 10px; color: #909399; }
@media (max-width: 760px) {
  .page-head, .list-head { align-items: flex-start; flex-direction: column; }
  .lookup { flex-direction: column; }
  .customer-summary, .checks { grid-template-columns: 1fr; }
  .performance { grid-column: auto; }
}
</style>
