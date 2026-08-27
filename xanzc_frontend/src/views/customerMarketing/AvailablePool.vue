<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div>
        <PageTitle />
        <span>仅展示后端按权限返回的 PUBLIC 公开线索；认领后到已认领客户池发起触达。</span>
      </div>
      <span class="scope-note">客户主键仅用于接口操作，不在页面展示</span>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            clearable
            placeholder="客户名称 / 统一社会信用代码"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      title="认领只建立本人客户关系，不自动生成触达任务。"
      description="同一条 PUBLIC 线索可由符合权限的不同客户经理分别认领；认领后请在已认领客户池发起触达。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-table :data="rows" v-loading="loading" border stripe class="table">
      <el-table-column label="线索类型" min-width="145">
        <template #default="{ row }">{{ leadTypeLabel(row.leadType || row.type) }}</template>
      </el-table-column>
      <el-table-column label="客户名称" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.custName || row.customerName || '-' }}</template>
      </el-table-column>
      <el-table-column label="统一社会信用代码" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.unifiedCreditCode || row.unifiedSocialCreditCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="所属行业" min-width="125">
        <template #default="{ row }">{{ row.industryName || '-' }}</template>
      </el-table-column>
      <el-table-column label="所属集团类型" min-width="135">
        <template #default="{ row }">{{ row.groupTypeName || '-' }}</template>
      </el-table-column>
      <el-table-column label="所属集团名称" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ row.groupName || '-' }}</template>
      </el-table-column>
      <el-table-column label="来源机构" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.ownerOrgName || '-' }}</template>
      </el-table-column>
      <el-table-column label="客户类型" min-width="115">
        <template #default="{ row }">{{ row.customerTypeName || '-' }}</template>
      </el-table-column>
      <el-table-column label="重点客户（是否基石客户）" width="155">
        <template #default="{ row }">{{ yesNo(row.isKeystone ?? row.cornerstoneCustomer) }}</template>
      </el-table-column>
      <el-table-column label="企业类型" min-width="120">
        <template #default="{ row }">{{ row.enterpriseTypeName || '-' }}</template>
      </el-table-column>
      <el-table-column label="是否开户" width="95">
        <template #default="{ row }">{{ yesNo(row.isAccountOpened ?? row.opened) }}</template>
      </el-table-column>
      <el-table-column label="客户标签" min-width="220">
        <template #default="{ row }">
          <div v-if="tagNames(row).length" class="tag-list">
            <el-tag v-for="tag in tagNames(row)" :key="tag" type="primary" effect="plain">{{ tag }}</el-tag>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="客户说明" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.customerDesc || row.customerDescription || '-' }}</template>
      </el-table-column>
      <el-table-column label="授信金额（元）" min-width="145" align="right">
        <template #default="{ row }">{{ money(row.creditAmount) }}</template>
      </el-table-column>
      <el-table-column label="授信敞口（元）" min-width="155" align="right">
        <template #default="{ row }">{{ money(row.creditExposureAmount ?? row.exposureAmount) }}</template>
      </el-table-column>
      <el-table-column label="附件" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ attachmentNames(row) || '-' }}</template>
      </el-table-column>
      <el-table-column label="下发方式" width="135">
        <template #default="{ row }">{{ distributionLabel(row.distributionMode || row.sourceType || 'PUBLIC') }}</template>
      </el-table-column>
      <el-table-column label="可认领范围" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ claimScope(row) }}</template>
      </el-table-column>
      <el-table-column label="已认领人数" width="115" align="center">
        <template #default="{ row }">{{ claimCount(row) }} 人</template>
      </el-table-column>
      <el-table-column label="下发时间" min-width="165">
        <template #default="{ row }">{{ formatTime(row.releasedTime || row.releasedAt || row.distributedTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right" class-name="operation-cell">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="success" :loading="claiming === claimIdOf(row)" @click="claim(row)">认领</el-button>
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

    <el-drawer
      v-model="detailVisible"
      title="客户线索详情"
      size="min(900px, 95vw)"
      destroy-on-close
    >
      <el-skeleton v-if="detailLoading" :rows="12" animated />
      <MarketingLeadReadonlyDetail v-else-if="selected" :detail="selected" />
      <el-empty v-else description="暂无详情" />
    </el-drawer>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import MarketingLeadReadonlyDetail from '@/components/MarketingLeadReadonlyDetail.vue';
import { claimCustomer, getAvailableCustomerLeadDetail, listAvailableCustomers } from '@/api/customerMarketing';
import { marketingLeadYuanToWan } from '@/api/marketingManagement';

const keyword = ref('');
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const claiming = ref('');
const pageNo = ref(1);
const pageSize = ref(20);
const detailVisible = ref(false);
const detailLoading = ref(false);
const selected = ref(null);

function pageOf(result) {
  if (Array.isArray(result)) return { records: result, total: result.length };
  const candidates = [result, result?.page, result?.data, result?.data?.page];
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
    const result = await listAvailableCustomers({
      keyword: keyword.value || undefined,
      sourceType: 'PUBLIC',
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

function search() {
  pageNo.value = 1;
  load();
}

function reset() {
  keyword.value = '';
  search();
}

async function openDetail(row) {
  detailVisible.value = true;
  detailLoading.value = true;
  selected.value = null;
  try {
    const leadId = row.sourceLeadId || row.leadId || row.currentLeadId;
    if (!leadId && !row?.id) throw new Error('当前线索缺少来源标识，请刷新后重试');
    const detail = await getAvailableCustomerLeadDetail(leadId || row.id);
    selected.value = normalizeDetail(detail || row);
  } catch (error) {
    detailVisible.value = false;
    selected.value = null;
    ElMessage.error(`详情加载失败：${error?.message || '请稍后重试'}`);
  } finally {
    detailLoading.value = false;
  }
}

async function claim(row) {
  const id = claimIdOf(row);
  if (!id) {
    ElMessage.warning('当前线索缺少认领标识，请刷新后重试');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认认领客户“${row.custName || row.customerName || '-'}”？`,
      '客户认领',
      { type: 'warning' },
    );
    claiming.value = id;
    const sourceLeadId = row.sourceLeadId || row.leadId || row.currentLeadId;
    if (sourceLeadId) await claimCustomer(id, sourceLeadId);
    else await claimCustomer(id);
    ElMessage.success('认领成功，请到已认领客户池发起触达');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && error?.message) {
      ElMessage.error(`认领失败：${error.message}`);
    }
  } finally {
    claiming.value = '';
  }
}

function claimIdOf(row) {
  return row?.custId || row?.customerId || row?.id || '';
}

function leadTypeLabel(value) {
  return { NEW_ACCOUNT: '新客户开户线索', EXISTING_MARKETING: '存量客户营销线索' }[value] || value || '-';
}

function normalizeDetail(value) {
  if (!value || typeof value !== 'object') return null;
  const detail = value.lead ? value : { ...value, lead: value };
  const lead = { ...detail.lead };
  ['creditAmount', 'creditExposureAmount'].forEach(field => {
    lead[field] = marketingLeadYuanToWan(lead[field]);
  });
  return { ...detail, lead };
}

function yesNo(value) {
  return value === true || value === 1 || value === '1' ? '是'
    : value === false || value === 0 || value === '0' ? '否' : '-';
}

function money(value) {
  return value == null || value === '' ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-';
}

function tagNames(row) {
  const source = row?.tagNames || row?.currentTags || row?.tags || [];
  if (!Array.isArray(source)) return source ? [String(source)] : [];
  return [...new Set(source.map(item => typeof item === 'string' ? item : item?.tagName || item?.name).filter(Boolean))];
}

function attachmentNames(row) {
  const source = row?.attachments || row?.attachmentNames || [];
  if (!Array.isArray(source)) return source ? String(source) : '';
  return source.map(item => typeof item === 'string' ? item : item?.fileName || item?.name || item?.id).filter(Boolean).join('、');
}

function distributionLabel(value) {
  return { PUBLIC: '全行公开认领', SCOPE: '指定范围', OWNER: '主办专属', PUBLIC_CLAIM: '全行公开认领' }[value] || value || '-';
}

function claimScope(row) {
  const scope = row?.claimScope || row?.allowedManagers || row?.managerScopeNames || row?.managerScopeIds;
  if (Array.isArray(scope)) {
    return scope.map(item => typeof item === 'string' ? item : item?.managerName || item?.name || item?.managerEmpId || item?.id).filter(Boolean).join('、') || '全行客户经理';
  }
  return scope || (row?.distributionMode === 'SCOPE' ? '-' : '全行客户经理');
}

function claimCount(row) {
  return Number(row?.claimedCount ?? row?.claimCount ?? row?.claimedNumber ?? 0) || 0;
}

load();
</script>

<style scoped lang="scss">
.page-head { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; margin-bottom: 14px; }
.page-head h1 { margin: 0; font-size: 18px; }
.page-head span { font-size: 12px; color: #909399; }
.scope-note { white-space: nowrap; }
.filter-card { margin-bottom: 12px; }
.table { margin-top: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 5px; }
@media (max-width: 760px) {
  .page-head { align-items: flex-start; flex-direction: column; gap: 6px; }
  .scope-note { white-space: normal; }
}
</style>
