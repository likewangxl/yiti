<template>
  <main v-bp-overflow-tooltip class="page bp-crud marketing-customer-list" aria-labelledby="marketing-customer-list-title">
    <header class="page-head">
      <div>
        <PageTitle id="marketing-customer-list-title" />
        <span>营销管理员查看和维护全部营销客户主档，并管理客户主办权。</span>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="企业名称 / 客户号 / 统一社会信用代码" @keyup.enter="search" /></el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model="query.unifiedCreditCode" clearable /></el-form-item>
        <el-form-item label="客户号"><el-input v-model="query.custNo" clearable /></el-form-item>
        <el-form-item label="所属行业">
          <el-select v-model="query.industry" clearable filterable placeholder="全部" :loading="industryLoading" style="width: 150px">
            <el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="主办工号"><el-input v-model="query.mainManagerId" clearable /></el-form-item>
        <el-form-item label="开户状态"><el-select v-model="query.isAccountOpened" clearable placeholder="全部" style="width: 120px"><el-option label="已开户" :value="1" /><el-option label="未开户" :value="0" /></el-select></el-form-item>
        <el-form-item label="主办状态"><el-select v-model="query.ownershipStatus" clearable placeholder="全部" style="width: 130px"><el-option label="有主办" value="ASSIGNED" /><el-option label="无主办" value="UNASSIGNED" /></el-select></el-form-item>
        <el-form-item label="维护方式"><el-select v-model="query.ownershipMaintainMode" clearable placeholder="全部" style="width: 130px"><el-option label="自动同步" value="AUTO" /><el-option label="人工维护" value="MANUAL" /></el-select></el-form-item>
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
      <el-table-column label="所属行业" min-width="120"><template #default="{ row }">{{ industryLabelOf(row.industry) }}</template></el-table-column>
      <el-table-column label="开户状态" width="95"><template #default="{ row }"><el-tag :type="row.isAccountOpened === 1 ? 'success' : 'info'">{{ row.isAccountOpened === 1 ? '已开户' : '未开户' }}</el-tag></template></el-table-column>
      <el-table-column label="主办客户经理" min-width="170"><template #default="{ row }">{{ ownerLabel(row) }}</template></el-table-column>
      <el-table-column label="主办机构" min-width="145"><template #default="{ row }">{{ row.mainOrgName || row.mainOrgId || '-' }}</template></el-table-column>
      <el-table-column label="主办维护" width="105"><template #default="{ row }"><el-tag :type="row.ownershipMaintainMode === 'MANUAL' ? 'warning' : 'success'">{{ row.ownershipMaintainMode === 'MANUAL' ? '人工维护' : '自动同步' }}</el-tag></template></el-table-column>
      <el-table-column label="授信敞口" min-width="130"><template #default="{ row }">{{ money(row.creditExposureAmount) }}</template></el-table-column>
      <el-table-column label="操作" width="250" fixed="right" class-name="operation-cell">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openOwner(row)">主办权</el-button>
          <el-button v-if="row.ownershipMaintainMode === 'MANUAL'" link type="success" @click="restoreAuto(row)">恢复自动</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" @change="load" /></div>

    <MarketingCustomerDetailDrawer v-model="detailVisible" :customer="selected" :loading="detailLoading" @edit="openEdit" />
    <MarketingCustomerOwnerDialog v-model="ownerDialogVisible" :customer="selected" :allow-unassign="true" :submitting="ownerSubmitting" @submit="submitOwner" />

    <el-dialog v-model="editVisible" class="bp-crud-dialog" title="编辑营销客户资料" width="min(900px, 94vw)" :close-on-click-modal="false" destroy-on-close>
      <el-alert title="仅修改客户主档允许维护的企业和营销资料；客户号、统一社会信用代码、开户状态和主办权请使用专用操作。" type="info" :closable="false" show-icon />
      <el-form ref="editFormRef" :model="editForm" label-position="top" class="edit-form" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="企业名称"><el-input v-model="editForm.custName" maxlength="200" /></el-form-item>
          <el-form-item label="法定代表人"><el-input v-model="editForm.legalRepresentative" maxlength="100" /></el-form-item>
          <el-form-item label="企业联系人"><el-input v-model="editForm.contactPerson" maxlength="200" /></el-form-item>
          <el-form-item label="联系电话"><el-input v-model="editForm.contactMobile" maxlength="50" /></el-form-item>
          <el-form-item label="注册地址"><el-input v-model="editForm.registeredAddress" maxlength="500" /></el-form-item>
          <el-form-item label="经营地址"><el-input v-model="editForm.businessAddress" maxlength="500" /></el-form-item>
          <el-form-item label="所属行业">
            <el-select v-model="editForm.industry" clearable filterable placeholder="请选择所属行业" :loading="industryLoading" style="width: 100%">
              <el-option v-for="item in industryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户类型"><el-input v-model="editForm.customerType" maxlength="50" /></el-form-item>
          <el-form-item label="集团类型"><el-input v-model="editForm.groupType" maxlength="50" /></el-form-item>
          <el-form-item label="集团名称"><el-input v-model="editForm.groupName" maxlength="200" /></el-form-item>
          <el-form-item label="客户标签">
            <el-select
              v-model="editForm.tagIds"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              clearable
              :loading="tagLoading"
              placeholder="请选择客户标签"
              style="width: 100%"
            >
              <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.tagName || tag.name || tag.id" :value="tag.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="注册资本（元）"><el-input-number v-model="editForm.registeredCapital" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item>
          <el-form-item label="授信金额（元）"><el-input-number v-model="editForm.creditAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item>
          <el-form-item label="授信敞口（元）"><el-input-number v-model="editForm.creditExposureAmount" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item>
          <el-form-item label="是否触达限制"><el-radio-group v-model="editForm.touchRestricted"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group></el-form-item>
          <el-form-item label="是否基石客户"><el-radio-group v-model="editForm.isKeystone"><el-radio :value="1">是</el-radio><el-radio :value="0">否</el-radio></el-radio-group></el-form-item>
        </div>
        <el-form-item label="经营范围"><el-input v-model="editForm.businessScope" type="textarea" :rows="2" maxlength="2000" /></el-form-item>
        <el-form-item label="客户说明"><el-input v-model="editForm.customerDesc" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item label="修改原因" required><el-input v-model="editForm.reason" type="textarea" :rows="2" maxlength="500" placeholder="请填写修改原因" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible = false">取消</el-button><el-button type="primary" :loading="editSubmitting" @click="saveProfile">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import MarketingCustomerDetailDrawer from '@/components/MarketingCustomerDetailDrawer.vue';
import MarketingCustomerOwnerDialog from '@/components/MarketingCustomerOwnerDialog.vue';
import { useDict } from '@/composables/useDict';
import {
  getMarketingCustomer,
  listEditableMarketingCustomerTags,
  listMarketingCustomers,
  restoreCustomerOwnershipAuto,
  transferCustomerOwner,
  updateMarketingCustomerProfile,
} from '@/api/marketingManagement';

const query = reactive({ keyword: '', unifiedCreditCode: '', custNo: '', mainManagerId: '', ownershipStatus: '', isAccountOpened: '', industry: '', customerType: '', isKeystone: '', ownershipMaintainMode: '', pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref('');
const selected = ref(null);
const detailVisible = ref(false);
const detailLoading = ref(false);
const ownerDialogVisible = ref(false);
const ownerSubmitting = ref(false);
const editVisible = ref(false);
const editSubmitting = ref(false);
const editFormRef = ref(null);
const editForm = reactive(emptyEditForm());
const tagOptions = ref([]);
const tagLoading = ref(false);
const {
  options: industryOptions,
  labelOf: industryLabelOf,
  loading: industryLoading,
  reload: reloadIndustry,
} = useDict('INDUSTRY');
const PROFILE_EDIT_FIELDS = [
  'custName', 'legalRepresentative', 'contactPerson', 'contactMobile',
  'registeredAddress', 'businessAddress', 'businessScope', 'industry',
  'groupType', 'groupName', 'customerType', 'enterpriseType', 'isKeystone',
  'customerDesc', 'registeredCapital', 'creditAmount', 'creditExposureAmount',
  'touchRestricted', 'tagIds', 'profileVersion', 'lockVersion', 'reason',
];

function emptyEditForm() {
  return { id: null, custName: '', legalRepresentative: '', contactPerson: '', contactMobile: '', registeredAddress: '', businessAddress: '', businessScope: '', industry: '', groupType: '', groupName: '', customerType: '', enterpriseType: '', isKeystone: null, customerDesc: '', registeredCapital: null, creditAmount: null, creditExposureAmount: null, touchRestricted: null, profileVersion: null, lockVersion: null, tagIds: [], reason: '' };
}

const money = value => value == null || value === '' ? '-' : `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const ownerLabel = row => row.mainManagerName || row.mainManagerId || '无主办';
const pageOf = result => result?.records || result?.list || result?.content || [];
const params = () => Object.fromEntries(Object.entries(query).map(([key, value]) => [key, value === '' ? undefined : value]));

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [result] = await Promise.all([
      listMarketingCustomers(params()),
      reloadIndustry(),
    ]);
    rows.value = pageOf(result);
    total.value = Number(result?.total || 0);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    errorMessage.value = `营销客户列表加载失败：${error?.message || '请稍后重试'}`;
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

function search() { query.pageNo = 1; load(); }
function reset() { Object.assign(query, { keyword: '', unifiedCreditCode: '', custNo: '', mainManagerId: '', ownershipStatus: '', isAccountOpened: '', industry: '', customerType: '', isKeystone: '', ownershipMaintainMode: '', pageNo: 1 }); load(); }

async function fetchDetail(row) {
  detailLoading.value = true;
  selected.value = null;
  try {
    const detail = await getMarketingCustomer(row.id);
    selected.value = detail || null;
    return detail;
  } catch (error) {
    ElMessage.error(`客户详情加载失败：${error?.message || '请稍后重试'}`);
    return null;
  } finally {
    detailLoading.value = false;
  }
}

function dateOnly(value) {
  if (!value) return null;
  const text = String(value).slice(0, 10);
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
  const date = match
    ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
    : new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  date.setHours(0, 0, 0, 0);
  return date;
}

function isEditableTag(tag) {
  if (!tag || tag.recordStatus !== 'ACTIVE') return false;
  const expiresAt = dateOnly(tag.expiresAt);
  if (!expiresAt) return true;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return expiresAt >= today;
}

async function loadTagOptions() {
  tagLoading.value = true;
  try {
    const result = await listEditableMarketingCustomerTags();
    tagOptions.value = pageOf(result).filter(isEditableTag);
  } catch (error) {
    tagOptions.value = [];
    ElMessage.error(`客户标签加载失败：${error?.message || '请稍后重试'}`);
  } finally {
    tagLoading.value = false;
  }
}

async function openDetail(row) {
  selected.value = row;
  detailVisible.value = true;
  await fetchDetail(row);
}

async function openEdit(row) {
  const detailPromise = row?.profileVersion != null || !Array.isArray(row?.tagIds)
    ? fetchDetail(row)
    : Promise.resolve(row);
  const [detail] = await Promise.all([detailPromise, loadTagOptions()]);
  const editSource = detail || row;
  Object.assign(editForm, emptyEditForm(), editSource, {
    id: editSource?.id,
    tagIds: Array.isArray(detail?.tagIds)
      ? [...detail.tagIds]
      : Array.isArray(row?.tagIds) ? [...row.tagIds] : [],
    reason: '',
  });
  editVisible.value = true;
}

async function saveProfile() {
  if (!editForm.reason.trim()) {
    ElMessage.warning('请填写修改原因');
    return;
  }
  if (editSubmitting.value || !editForm.id) return;
  editSubmitting.value = true;
  const payload = Object.fromEntries(PROFILE_EDIT_FIELDS.map(key => [
    key,
    key === 'tagIds' ? (Array.isArray(editForm.tagIds) ? [...editForm.tagIds] : []) : editForm[key],
  ]));
  try {
    await updateMarketingCustomerProfile(editForm.id, payload);
    ElMessage.success('客户资料已保存');
    editVisible.value = false;
    await load();
    if (detailVisible.value) await fetchDetail({ id: editForm.id });
  } catch (error) {
    ElMessage.error(`客户资料保存失败：${error?.message || '请刷新后重试'}`);
  } finally {
    editSubmitting.value = false;
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
    await transferCustomerOwner(selected.value.id, { ...payload, lockVersion: selected.value.lockVersion });
    ElMessage.success(payload.transferAction === 'UNASSIGN' ? '客户已设置为无主办' : '客户主办权已转交');
    ownerDialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(`主办权操作失败：${error?.message || '请刷新后重试'}`);
  } finally {
    ownerSubmitting.value = false;
  }
}

async function restoreAuto(row) {
  try {
    const result = await ElMessageBox.prompt('恢复自动同步后，下一次客户经理关系全量快照会刷新该客户主办权。', '恢复自动同步', { inputPlaceholder: '请输入操作原因', inputValidator: value => !!value?.trim() || '操作原因不能为空' });
    await restoreCustomerOwnershipAuto(row.id, { reason: result.value.trim(), lockVersion: row.lockVersion });
    ElMessage.success('已恢复主办权自动同步');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close' && error?.message) ElMessage.error(`恢复自动同步失败：${error.message}`);
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
.edit-form { margin-top: 16px; }
.form-grid { display: grid; gap: 0 14px; grid-template-columns: repeat(3, minmax(0, 1fr)); }
@media (max-width: 900px) { .form-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) { .form-grid { grid-template-columns: 1fr; } .page-head { gap: 12px; } }
</style>
