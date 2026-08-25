<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head"><PageTitle /><span>展示审批通过后进入全行公开认领的客户</span></header>
    <el-form inline @submit.prevent>
      <el-form-item label="客户名称"><el-input v-model="keyword" clearable placeholder="名称关键字" @keyup.enter="search" /></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-alert title="认领仅建立本人客户关系，不会自动生成触达任务；请在“已认领客户”中手动发起。" type="info" :closable="false" show-icon />
    <el-table :data="rows" v-loading="loading" border stripe class="table">
      <el-table-column prop="custName" label="客户名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="180" show-overflow-tooltip />
      <el-table-column prop="industryName" label="行业" min-width="120"><template #default="{ row }">{{ row.industryName || '-' }}</template></el-table-column>
      <el-table-column prop="customerTypeName" label="客户类型" width="110"><template #default="{ row }">{{ row.customerTypeName || '-' }}</template></el-table-column>
      <el-table-column prop="ownerOrgName" label="来源机构" min-width="150"><template #default="{ row }">{{ row.ownerOrgName || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="140" fixed="right" class-name="operation-cell">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="success" :loading="claiming === row.id" @click="claim(row)">认领</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total"
      v-model:current-page="pageNo" v-model:page-size="pageSize" :page-sizes="[10,20,50]" @change="load" /></div>

    <LeadDetailDrawer v-model="detailVisible" :lead="selected" :loading="detailLoading" />
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import LeadDetailDrawer from '@/components/LeadDetailDrawer.vue';
import { claimCustomer, getAvailableCustomerLeadDetail, listAvailableCustomers } from '@/api/customerMarketing';

const keyword = ref(''); const rows = ref([]); const total = ref(0); const loading = ref(false);
const claiming = ref(''); const pageNo = ref(1); const pageSize = ref(20);
const detailVisible = ref(false); const detailLoading = ref(false); const selected = ref(null);
async function load() {
  loading.value = true;
  try { const r = await listAvailableCustomers({ keyword: keyword.value || undefined, pageNo: pageNo.value, pageSize: pageSize.value }); rows.value = r?.records || []; total.value = r?.total || 0; }
  finally { loading.value = false; }
}
function search() { pageNo.value = 1; load(); }
function reset() { keyword.value = ''; search(); }
async function openDetail(row) {
  detailVisible.value = true;
  detailLoading.value = true;
  selected.value = null;
  try {
    const leadId = row.currentLeadId || row.leadId;
    selected.value = await getAvailableCustomerLeadDetail(leadId);
  } finally {
    detailLoading.value = false;
  }
}
async function claim(row) {
  await ElMessageBox.confirm(`确认认领客户“${row.custName}”？`, '客户认领', { type:'warning' });
  claiming.value = row.id;
  try { await claimCustomer(row.id); ElMessage.success('认领成功，请到已认领客户发起触达'); await load(); }
  finally { claiming.value = ''; }
}
load();
</script>

<style scoped lang="scss">
.page-head { display:flex; align-items:baseline; gap:12px; margin-bottom:14px; h1{font-size:18px;margin:0} span{font-size:12px;color:#909399} }
.table { margin-top:12px; }.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>
