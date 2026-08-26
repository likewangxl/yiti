<template>
  <main v-bp-overflow-tooltip class="page bp-crud my-customers" aria-labelledby="my-customers-title">
    <header class="page-head">
      <div>
        <PageTitle id="my-customers-title" />
        <span>仅展示主办权归属于当前登录人的营销客户；主办权转交后客户将从本列表移除。</span>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="客户名称"><el-input v-model="query.keyword" clearable placeholder="企业名称 / 客户号 / 统一社会信用代码" @keyup.enter="search" /></el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model="query.unifiedCreditCode" clearable /></el-form-item>
        <el-form-item label="客户号"><el-input v-model="query.custNo" clearable /></el-form-item>
        <el-form-item label="开户状态"><el-select v-model="query.isAccountOpened" clearable placeholder="全部" style="width: 120px"><el-option label="已开户" :value="1" /><el-option label="未开户" :value="0" /></el-select></el-form-item>
        <el-form-item label="客户类型"><el-input v-model="query.customerType" clearable /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="page-error" />
    <el-table :data="rows" v-loading="loading" border stripe class="customer-table">
      <el-table-column prop="custNo" label="客户号" min-width="135" />
      <el-table-column prop="custName" label="企业名称" min-width="190" show-overflow-tooltip>
        <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">{{ row.custName || '-' }}</el-button></template>
      </el-table-column>
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="185" show-overflow-tooltip />
      <el-table-column prop="contactPerson" label="企业联系人" min-width="125" />
      <el-table-column prop="contactMobile" label="联系电话" min-width="130" />
      <el-table-column prop="industry" label="所属行业" min-width="120" />
      <el-table-column label="开户状态" width="95"><template #default="{ row }"><el-tag :type="row.isAccountOpened === 1 ? 'success' : 'info'">{{ row.isAccountOpened === 1 ? '已开户' : '未开户' }}</el-tag></template></el-table-column>
      <el-table-column label="主办机构" min-width="145"><template #default="{ row }">{{ row.mainOrgName || row.mainOrgId || '-' }}</template></el-table-column>
      <el-table-column label="授信敞口" min-width="130"><template #default="{ row }">{{ money(row.creditExposureAmount) }}</template></el-table-column>
      <el-table-column label="操作" width="175" fixed="right" class-name="operation-cell">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="warning" @click="openOwner(row)">转交主办</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" @change="load" /></div>

    <MarketingCustomerDetailDrawer v-model="detailVisible" :customer="selected" :loading="detailLoading" />
    <MarketingCustomerOwnerDialog v-model="ownerDialogVisible" :customer="selected" :allow-unassign="allowUnassign" :submitting="ownerSubmitting" @submit="submitOwner" />
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import MarketingCustomerDetailDrawer from '@/components/MarketingCustomerDetailDrawer.vue';
import MarketingCustomerOwnerDialog from '@/components/MarketingCustomerOwnerDialog.vue';
import { getMarketingCustomer, listMyCustomers, transferCustomerOwner } from '@/api/marketingManagement';

const query = reactive({ keyword: '', unifiedCreditCode: '', custNo: '', isAccountOpened: '', customerType: '', pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref('');
const selected = ref(null);
const detailVisible = ref(false);
const detailLoading = ref(false);
const ownerDialogVisible = ref(false);
const ownerSubmitting = ref(false);
const allowUnassign = false;

const money = value => value == null || value === '' ? '-' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const pageOf = result => result?.records || result?.list || result?.content || [];
const params = () => Object.fromEntries(Object.entries(query).map(([key, value]) => [key, value === '' ? undefined : value]));

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await listMyCustomers(params());
    rows.value = pageOf(result);
    total.value = Number(result?.total || 0);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    errorMessage.value = `我的客户加载失败：${error?.message || '请稍后重试'}`;
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

function search() { query.pageNo = 1; load(); }
function reset() { Object.assign(query, { keyword: '', unifiedCreditCode: '', custNo: '', isAccountOpened: '', customerType: '', pageNo: 1 }); load(); }

async function openDetail(row) {
  selected.value = null;
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    selected.value = await getMarketingCustomer(row.id);
  } catch (error) {
    ElMessage.error(`客户详情加载失败：${error?.message || '请稍后重试'}`);
  } finally {
    detailLoading.value = false;
  }
}

function openOwner(row) {
  selected.value = row;
  ownerDialogVisible.value = true;
}

async function submitOwner(payload) {
  if (!selected.value?.id || ownerSubmitting.value) return;
  ownerSubmitting.value = true;
  try {
    // 页面二不暴露“设置为无”，服务端也会再次校验目标必须是其他客户经理。
    await transferCustomerOwner(selected.value.id, { ...payload, transferAction: 'TRANSFER', lockVersion: selected.value.lockVersion });
    ElMessage.success('客户主办权已转交');
    ownerDialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(`主办权转交失败：${error?.message || '请刷新后重试'}`);
  } finally {
    ownerSubmitting.value = false;
  }
}

load();
</script>

<style scoped lang="scss">
.page-head { align-items: flex-start; display: flex; justify-content: space-between; margin-bottom: 14px; }
.page-head h1 { font-size: 18px; margin: 0; }
.page-head span { color: #909399; display: block; font-size: 12px; margin-top: 4px; }
.filter-card { margin-bottom: 14px; }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.page-error { margin-bottom: 12px; }
.customer-table { width: 100%; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
@media (max-width: 620px) { .page-head { gap: 12px; } }
</style>
