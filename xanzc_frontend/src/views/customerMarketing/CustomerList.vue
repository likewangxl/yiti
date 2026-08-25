<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div><PageTitle /><span>按主办关系或有效认领关系展示当前用户可见客户。</span></div>
      <el-button :loading="exporting" @click="download">导出客户</el-button>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="客户关键词">
          <el-input v-model="query.keyword" clearable placeholder="客户名称 / 客户号 / 统一社会信用代码" class="keyword" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="客户状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:150px">
            <el-option label="有效" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="custNo" label="客户号" min-width="145"><template #default="{ row }">{{ row.custNo || '-' }}</template></el-table-column>
      <el-table-column prop="custName" label="客户名称" min-width="190" show-overflow-tooltip>
        <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">{{ row.custName }}</el-button></template>
      </el-table-column>
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="185" />
      <el-table-column label="行业" min-width="125"><template #default="{ row }">{{ row.industryName || row.industry || '-' }}</template></el-table-column>
      <el-table-column prop="customerType" label="客户类型" width="110"><template #default="{ row }">{{ customerTypeLabel(row.customerType) }}</template></el-table-column>
      <el-table-column label="主办客户经理" min-width="165"><template #default="{ row }">{{ row.mainManagerName || '-' }}<span v-if="row.mainManagerId">（{{ row.mainManagerId }}）</span></template></el-table-column>
      <el-table-column label="主办机构" min-width="155"><template #default="{ row }">{{ row.mainOrgName || row.mainOrgId || '-' }}</template></el-table-column>
      <el-table-column label="开户" width="78"><template #default="{ row }"><el-tag :type="row.isAccountOpened ? 'success' : 'info'">{{ row.isAccountOpened ? '是' : '否' }}</el-tag></template></el-table-column>
      <el-table-column label="客户状态" width="95"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '有效' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="最近触达" min-width="165"><template #default="{ row }">{{ formatTime(row.lastTouchTime) }}</template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right" class-name="operation-cell"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10,20,50,100]" @change="load" /></div>

    <el-drawer v-model="detailVisible" title="客户详情" size="min(780px, 94vw)" destroy-on-close>
      <el-skeleton v-if="detailLoading" :rows="9" animated />
      <template v-else-if="selected">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户名称">{{ selected.custName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户号">{{ selected.custNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="统一社会信用代码" :span="2">{{ selected.unifiedCreditCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属行业">{{ selected.industryName || selected.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">{{ customerTypeLabel(selected.customerType) }}</el-descriptions-item>
          <el-descriptions-item label="集团类型">{{ selected.groupType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="企业类型">{{ selected.enterpriseType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="基石客户">{{ selected.isKeystone ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="是否开户">{{ selected.isAccountOpened ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="主办客户经理" :span="2">{{ selected.mainManagerName || '-' }}<span v-if="selected.mainManagerId">（{{ selected.mainManagerId }}）</span> · {{ selected.mainOrgName || selected.mainOrgId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="授信金额">{{ money(selected.creditAmount) }}</el-descriptions-item>
          <el-descriptions-item label="授信敞口">{{ money(selected.creditExposureAmount) }}</el-descriptions-item>
          <el-descriptions-item label="客户说明" :span="2">{{ selected.customerDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源系统">{{ selected.sourceSystem || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源机构">{{ selected.ownerOrgName || selected.ownerOrgId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近触达">{{ formatTime(selected.lastTouchTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(selected.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
        <section class="tag-section"><h3>客户标签</h3><div v-if="selected.tagIds?.length" class="tags"><el-tag v-for="tag in selected.tagIds" :key="tag" effect="plain">{{ tag }}</el-tag></div><el-empty v-else description="暂无标签" :image-size="52" /></section>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { exportMarketingCustomers, getMarketingCustomer, listMarketingCustomers } from '@/api/customerMarketing';

const query = reactive({ keyword:'', status:'', pageNo:1, pageSize:20 });
const rows = ref([]), total = ref(0), loading = ref(false), exporting = ref(false);
const selected = ref(null), detailVisible = ref(false), detailLoading = ref(false);
const customerTypeLabel = value => ({ CORP:'对公客户', RETAIL:'零售客户' }[value] || value || '-');
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-';
const money = value => value == null || value === '' ? '-' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits:2 })}`;
const params = () => ({ keyword:query.keyword || undefined, status:query.status || undefined, pageNo:query.pageNo, pageSize:query.pageSize });

async function load() {
  loading.value = true;
  try { const result = await listMarketingCustomers(params()); rows.value = result?.records || []; total.value = result?.total || 0; }
  finally { loading.value = false; }
}
function search() { query.pageNo = 1; load(); }
function reset() { query.keyword = ''; query.status = ''; search(); }
async function openDetail(row) {
  detailVisible.value = true; detailLoading.value = true; selected.value = row;
  try { selected.value = await getMarketingCustomer(row.id) || row; }
  finally { detailLoading.value = false; }
}
async function download() {
  exporting.value = true;
  try {
    const blob = await exportMarketingCustomers(params());
    const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
    anchor.href = url; anchor.download = `客户列表_${Date.now()}.xlsx`; anchor.click(); URL.revokeObjectURL(url);
    ElMessage.success('客户列表导出已开始');
  } finally { exporting.value = false; }
}
load();
</script>

<style scoped lang="scss">
.page-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:14px}.page-head h1{margin:0;font-size:18px}.page-head span{display:block;margin-top:4px;color:#909399;font-size:12px}.filter-card{margin-bottom:14px}.filter-card :deep(.el-card__body){padding-bottom:2px}.keyword{width:300px}.pager{display:flex;justify-content:flex-end;margin-top:14px}.tag-section{margin-top:22px}.tag-section h3{font-size:16px}.tags{display:flex;gap:8px;flex-wrap:wrap}@media(max-width:720px){.page-head{gap:12px}.keyword{width:220px}}
</style>
