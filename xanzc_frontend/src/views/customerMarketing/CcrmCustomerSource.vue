<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div>
        <PageTitle />
        <p class="scope-tip">CCRM 独立来源数据，仅供历史客户与主办权检索；不会直接创建线索、触达任务或资产立项。</p>
      </div>
      <div class="head-actions">
        <el-button type="primary" @click="openCreate">新增客户源记录</el-button>
        <el-button @click="importVisible = true">导入 Excel</el-button>
        <el-button :loading="templateLoading" @click="downloadTemplate">下载模板</el-button>
        <el-button :loading="exporting" @click="downloadExport">按当前筛选导出</el-button>
      </div>
    </header>

    <el-alert class="boundary-alert" type="warning" :closable="false" show-icon>
      本页面维护 CCRM 独立来源数据，和客户线索、触达任务、客户主档及 M98 等其他来源保持隔离；只供历史客户/主办权检索。
    </el-alert>

    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent="search">
        <el-form-item label="综合关键词">
          <el-input v-model="query.keyword" clearable class="keyword" placeholder="客户名称 / CCRM 客户号 / 统一社会信用代码" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="主办客户经理"><el-input v-model="query.mainManager" clearable placeholder="工号或姓名" /></el-form-item>
        <el-form-item label="主办机构"><el-input v-model="query.mainOrg" clearable placeholder="机构代码或名称" /></el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="query.customerType" clearable placeholder="全部" class="filter-select">
            <el-option label="对公客户" value="CORP" /><el-option label="零售客户" value="RETAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业"><el-input v-model="query.industry" clearable placeholder="行业代码或名称" /></el-form-item>
        <el-form-item label="集团类型">
          <el-select v-model="query.groupType" clearable placeholder="全部" class="filter-select">
            <el-option label="集团" value="GROUP" /><el-option label="非集团" value="SINGLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="开户状态">
          <el-select v-model="query.accountOpened" clearable placeholder="全部" class="filter-select">
            <el-option label="已开户" value="1" /><el-option label="未开户" value="0" /><el-option label="未知" value="UNKNOWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据状态">
          <el-select v-model="query.recordStatus" clearable placeholder="全部" class="filter-select">
            <el-option label="有效" value="ACTIVE" /><el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="完整性">
          <el-select v-model="query.validationStatus" clearable placeholder="全部" class="filter-select">
            <el-option label="有效" value="VALID" /><el-option label="缺字段" value="INCOMPLETE" /><el-option label="冲突" value="CONFLICT" />
          </el-select>
        </el-form-item>
        <el-form-item label="导入方式">
          <el-select v-model="query.importMode" clearable placeholder="全部" class="filter-select">
            <el-option label="接口" value="API" /><el-option label="文件" value="FILE" /><el-option label="人工" value="MANUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="CCRM 更新时间">
          <div class="time-range">
            <el-date-picker v-model="query.sourceUpdatedStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="开始时间" />
            <span>至</span>
            <el-date-picker v-model="query.sourceUpdatedEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="结束时间" />
          </div>
        </el-form-item>
        <el-form-item class="filter-actions"><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-table :data="rows" v-loading="loading" border stripe row-key="id" class="source-table">
      <el-table-column prop="ccrmCustNo" label="CCRM 客户号" min-width="145" />
      <el-table-column prop="custName" label="客户名称" min-width="190" show-overflow-tooltip>
        <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">{{ row.custName || '-' }}</el-button></template>
      </el-table-column>
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190" show-overflow-tooltip />
      <el-table-column prop="customerType" label="客户类型" width="110"><template #default="{ row }">{{ customerTypeLabel(row.customerType) }}</template></el-table-column>
      <el-table-column label="行业" min-width="125" show-overflow-tooltip><template #default="{ row }">{{ row.industryName || row.industryCode || '-' }}</template></el-table-column>
      <el-table-column label="主办客户经理" min-width="165" show-overflow-tooltip><template #default="{ row }">{{ managerLabel(row) }}</template></el-table-column>
      <el-table-column label="主办机构" min-width="155" show-overflow-tooltip><template #default="{ row }">{{ row.mainOrgName || row.mainOrgId || '-' }}</template></el-table-column>
      <el-table-column label="开户状态" width="92"><template #default="{ row }"><el-tag :type="accountTagType(row.isAccountOpened)">{{ accountLabel(row.isAccountOpened) }}</el-tag></template></el-table-column>
      <el-table-column prop="creditAmount" label="授信金额" min-width="125" align="right"><template #default="{ row }">{{ money(row.creditAmount) }}</template></el-table-column>
      <el-table-column label="完整性" width="92"><template #default="{ row }"><el-tag :type="validationTagType(row.validationStatus)">{{ validationLabel(row.validationStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="导入方式" width="88"><template #default="{ row }">{{ importModeLabel(row.importMode) }}</template></el-table-column>
      <el-table-column label="CCRM 更新时间" min-width="165"><template #default="{ row }">{{ formatTime(row.sourceUpdatedTime) }}</template></el-table-column>
      <el-table-column label="操作" width="150" fixed="right" class-name="operation-cell">
        <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button link type="primary" @click="openEdit(row)">修改</el-button><el-button v-if="row.recordStatus !== 'INACTIVE'" link type="danger" @click="remove(row)">删除</el-button></template>
      </el-table-column>
    </el-table>
    <div class="pager"><el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" background layout="total, sizes, prev, pager, next" :page-sizes="[10,20,50,100]" :total="total" @change="load" /></div>

    <el-drawer v-model="detailVisible" title="CCRM 客户源详情" size="min(820px, 94vw)" destroy-on-close>
      <el-skeleton v-if="detailLoading" :rows="10" animated />
      <template v-else-if="selected">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="CCRM 客户号">{{ selected.ccrmCustNo || '-' }}</el-descriptions-item><el-descriptions-item label="客户名称">{{ selected.custName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="统一社会信用代码" :span="2">{{ selected.unifiedCreditCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户类型">{{ customerTypeLabel(selected.customerType) }}</el-descriptions-item><el-descriptions-item label="企业类型">{{ selected.enterpriseType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ selected.industryName || selected.industryCode || '-' }}</el-descriptions-item><el-descriptions-item label="集团类型">{{ selected.groupType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="集团名称">{{ selected.groupName || '-' }}</el-descriptions-item><el-descriptions-item label="主办权状态">{{ ownershipLabel(selected.ownershipStatus) }}</el-descriptions-item>
          <el-descriptions-item label="主办客户经理" :span="2">{{ managerLabel(selected) }}</el-descriptions-item><el-descriptions-item label="主办机构" :span="2">{{ selected.mainOrgName || selected.mainOrgId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开户状态">{{ accountLabel(selected.isAccountOpened) }}</el-descriptions-item><el-descriptions-item label="开户日期">{{ formatTime(selected.accountOpenDate) }}</el-descriptions-item>
          <el-descriptions-item label="授信金额">{{ money(selected.creditAmount) }}</el-descriptions-item><el-descriptions-item label="授信敞口">{{ money(selected.creditExposureAmount) }}</el-descriptions-item>
          <el-descriptions-item label="收入结算账户名称" :span="2">{{ selected.incomeSettlementAccountName || '-' }}</el-descriptions-item><el-descriptions-item label="经营信息" :span="2">{{ selected.businessInfo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源更新时间">{{ formatTime(selected.sourceUpdatedTime) }}</el-descriptions-item><el-descriptions-item label="导入方式">{{ importModeLabel(selected.importMode) }}</el-descriptions-item>
          <el-descriptions-item label="导入批次号">{{ selected.importBatchNo || '-' }}</el-descriptions-item><el-descriptions-item label="数据状态">{{ recordStatusLabel(selected.recordStatus) }}</el-descriptions-item>
          <el-descriptions-item label="完整性">{{ validationLabel(selected.validationStatus) }}</el-descriptions-item><el-descriptions-item label="校验说明">{{ selected.validationMessage || '-' }}</el-descriptions-item>
          <el-descriptions-item label="乐观锁版本">{{ selected.lockVersion ?? '-' }}</el-descriptions-item><el-descriptions-item label="停用原因">{{ selected.disabledReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建审计" :span="2">{{ auditLabel(selected.createdBy, selected.createdTime) }}</el-descriptions-item><el-descriptions-item label="更新审计" :span="2">{{ auditLabel(selected.updatedBy, selected.updatedTime) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else description="暂无详情" />
    </el-drawer>

    <el-dialog v-model="formVisible" :title="formTitle" width="min(920px, 94vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="125px" @submit.prevent="save()">
        <section class="form-section"><h3>客户身份</h3><div class="form-grid">
          <el-form-item label="CCRM 客户号"><el-input v-model="form.ccrmCustNo" maxlength="100" /></el-form-item><el-form-item label="客户名称" prop="custName"><el-input v-model="form.custName" maxlength="200" /></el-form-item>
          <el-form-item label="统一社会信用代码" prop="unifiedCreditCode"><el-input v-model="form.unifiedCreditCode" maxlength="18" @input="form.unifiedCreditCode = String(form.unifiedCreditCode || '').toUpperCase()" /></el-form-item><el-form-item label="客户类型" prop="customerType"><el-select v-model="form.customerType" placeholder="请选择"><el-option label="对公客户" value="CORP" /></el-select></el-form-item>
        </div></section>
        <section class="form-section"><h3>分类信息</h3><div class="form-grid">
          <el-form-item label="行业代码"><el-input v-model="form.industryCode" /></el-form-item><el-form-item label="行业名称"><el-input v-model="form.industryName" /></el-form-item>
          <el-form-item label="集团类型"><el-select v-model="form.groupType" clearable placeholder="请选择"><el-option label="集团" value="GROUP" /><el-option label="非集团" value="SINGLE" /></el-select></el-form-item><el-form-item label="集团名称"><el-input v-model="form.groupName" maxlength="200" /></el-form-item>
          <el-form-item label="企业类型"><el-input v-model="form.enterpriseType" /></el-form-item>
        </div></section>
        <section class="form-section"><h3>主办权</h3><div class="form-grid">
          <el-form-item label="主办客户经理工号"><el-input v-model="form.mainManagerId" /></el-form-item><el-form-item label="主办客户经理姓名"><el-input v-model="form.mainManagerName" /></el-form-item>
          <el-form-item label="主办机构代码"><el-input v-model="form.mainOrgId" /></el-form-item><el-form-item label="主办机构名称"><el-input v-model="form.mainOrgName" /></el-form-item>
          <el-form-item label="主办权状态"><el-select v-model="form.ownershipStatus" placeholder="请选择"><el-option label="已分配" value="ASSIGNED" /><el-option label="未分配" value="UNASSIGNED" /><el-option label="未知" value="UNKNOWN" /></el-select></el-form-item>
        </div></section>
        <section class="form-section"><h3>开户与授信</h3><div class="form-grid">
          <el-form-item label="是否开户"><el-select v-model="form.isAccountOpened" clearable placeholder="未知"><el-option label="是" :value="1" /><el-option label="否" :value="0" /></el-select></el-form-item><el-form-item label="开户日期"><el-date-picker v-model="form.accountOpenDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="授信金额"><el-input v-model="form.creditAmount" type="number" min="0" /></el-form-item><el-form-item label="授信敞口"><el-input v-model="form.creditExposureAmount" type="number" min="0" /></el-form-item>
        </div></section>
        <section class="form-section"><h3>经营校验</h3><div class="form-grid">
          <el-form-item label="收入结算账户名称"><el-input v-model="form.incomeSettlementAccountName" maxlength="200" /></el-form-item><el-form-item label="经营信息" class="wide-item"><el-input v-model="form.businessInfo" type="textarea" maxlength="2000" :rows="3" /></el-form-item>
        </div></section>
        <section class="form-section source-audit"><h3>来源与审计</h3><p>人工新增和修改的导入方式固定为 MANUAL；审计人、审计时间、批次号及完整性状态由服务端维护。</p><p v-if="isEditing">当前乐观锁版本：{{ form.lockVersion ?? '-' }}</p></section>
      </el-form>
      <template #footer><el-button @click="formVisible = false">取消</el-button><el-button type="primary" :loading="formLoading" @click="save()">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入 CCRM 客户源数据" width="min(900px, 94vw)" destroy-on-close>
      <p class="import-boundary">只允许导入 CCRM 源字段；按统一社会信用代码优先、CCRM 客户号其次匹配，不按客户名称覆盖。</p>
      <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx" :on-change="onImportFileChange"><el-button>选择 Excel 文件</el-button></el-upload>
      <p v-if="importFile" class="selected-file">已选择：{{ importFile.name }}</p>
      <div class="import-actions"><el-button type="primary" :loading="importLoading" :disabled="!importFile" @click="previewFile()">上传并预览</el-button><el-button v-if="importPreview.batchId" :disabled="!canExecuteImport" @click="executeImport">确认执行</el-button></div>
      <template v-if="importPreview.batchId"><el-divider /><div class="import-summary"><el-tag type="success">新增 {{ importPreview.addedCount ?? 0 }}</el-tag><el-tag type="warning">更新 {{ importPreview.updatedCount ?? 0 }}</el-tag><el-tag>不变 {{ importPreview.unchangedCount ?? 0 }}</el-tag><el-tag type="danger">失败 {{ importFailedCount }}</el-tag></div><el-alert v-if="importFailedCount" type="error" :closable="false">存在失败行，整批不能执行，请修正后重新上传。</el-alert><el-table v-if="importErrors.length" :data="importErrors" border stripe class="import-error-table"><el-table-column prop="row" label="行号" width="80" /><el-table-column prop="field" label="字段" width="180" /><el-table-column prop="reason" label="失败原因" min-width="260" /></el-table><el-empty v-else description="校验通过，无错误明细" :image-size="60" /></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createCcrmCustomer, deleteCcrmCustomer, downloadCcrmImportTemplate, executeCcrmImport, exportCcrmCustomers, getCcrmCustomer, listCcrmCustomers, previewCcrmImport, updateCcrmCustomer } from '@/api/ccrmCustomers';

const query = reactive({ keyword: '', mainManager: '', mainOrg: '', customerType: '', industry: '', groupType: '', accountOpened: '', recordStatus: '', validationStatus: '', importMode: '', sourceUpdatedStart: '', sourceUpdatedEnd: '', pageNo: 1, pageSize: 20 });
const rows = ref([]), total = ref(0), loading = ref(false), exporting = ref(false), templateLoading = ref(false);
const selected = ref(null), detailVisible = ref(false), detailLoading = ref(false), formVisible = ref(false), formLoading = ref(false), isEditing = ref(false), formRef = ref(null);
const form = reactive(blankForm()), formErrors = ref({});
const importVisible = ref(false), importLoading = ref(false), importFile = ref(null), importPreview = ref({});

function blankForm() { return { id: undefined, ccrmCustNo: '', custName: '', unifiedCreditCode: '', customerType: 'CORP', industryCode: '', industryName: '', groupType: '', groupName: '', enterpriseType: '', mainManagerId: '', mainManagerName: '', mainOrgId: '', mainOrgName: '', ownershipStatus: 'UNKNOWN', isAccountOpened: null, accountOpenDate: '', creditAmount: '', creditExposureAmount: '', incomeSettlementAccountName: '', businessInfo: '', lockVersion: undefined }; }
const formRules = { custName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }], unifiedCreditCode: [{ required: true, message: '请输入18位统一社会信用代码', trigger: 'blur' }], customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }] };
const formTitle = computed(() => isEditing.value ? '修改 CCRM 客户源记录' : '新增 CCRM 客户源记录');
const importErrors = computed(() => { const v = importPreview.value || {}; return Array.isArray(v.errors) ? v.errors : (Array.isArray(v.errorDetails) ? v.errorDetails : []); });
const importFailedCount = computed(() => { const v = importPreview.value || {}; const count = v.failedCount ?? v.failureCount ?? v.errorCount; return count == null ? importErrors.value.length : Number(count); });
const canExecuteImport = computed(() => Boolean(importPreview.value?.batchId) && importFailedCount.value === 0);

function isBlank(value) { return value == null || String(value).trim() === ''; }
function numeric(value) { return isBlank(value) ? null : Number(value); }
function validateForm(value = form) {
  const errors = {};
  if (isBlank(value.custName)) errors.custName = '客户名称不能为空';
  if (!/^[0-9A-Z]{18}$/.test(String(value.unifiedCreditCode || '').trim())) errors.unifiedCreditCode = '统一社会信用代码必须为18位大写字母或数字';
  if (value.customerType !== 'CORP') errors.customerType = '客户类型必须为对公客户';
  const manager = !isBlank(value.mainManagerId), org = !isBlank(value.mainOrgId);
  if (manager !== org || (value.ownershipStatus === 'ASSIGNED' && (!manager || !org))) errors.ownership = '主办客户经理和主办机构必须成对填写';
  if (value.groupType === 'GROUP' && isBlank(value.groupName)) errors.groupName = '集团类型为集团时必须填写集团名称';
  const credit = numeric(value.creditAmount), exposure = numeric(value.creditExposureAmount);
  if (credit != null && (!Number.isFinite(credit) || credit < 0)) errors.creditAmount = '授信金额必须为非负数';
  if (exposure != null && (!Number.isFinite(exposure) || exposure < 0)) errors.creditExposureAmount = '授信敞口必须为非负数';
  if (credit != null && exposure != null && Number.isFinite(credit) && Number.isFinite(exposure) && exposure > credit) errors.creditExposureAmount = '授信敞口不能超过授信金额';
  return errors;
}
function buildQueryParams() { return { keyword: query.keyword || undefined, mainManager: query.mainManager || undefined, mainOrg: query.mainOrg || undefined, customerType: query.customerType || undefined, industry: query.industry || undefined, groupType: query.groupType || undefined, accountOpened: query.accountOpened || undefined, recordStatus: query.recordStatus || undefined, validationStatus: query.validationStatus || undefined, importMode: query.importMode || undefined, sourceUpdatedStart: query.sourceUpdatedStart || undefined, sourceUpdatedEnd: query.sourceUpdatedEnd || undefined, pageNo: query.pageNo, pageSize: query.pageSize }; }
function normalizePage(result) { const value = result?.page || result?.data || result; if (Array.isArray(value)) return { records: value, total: value.length }; return { records: value?.records || value?.list || value?.content || [], total: value?.total ?? 0 }; }
function normalizeDetail(value) { return value?.data && !value.id ? value.data : value; }
async function load() { loading.value = true; try { const page = normalizePage(await listCcrmCustomers(buildQueryParams())); rows.value = page.records; total.value = page.total; } catch (error) { rows.value = []; total.value = 0; ElMessage.error(error?.message || 'CCRM 客户源查询失败'); } finally { loading.value = false; } }
function search() { query.pageNo = 1; return load(); }
function reset() { Object.assign(query, { keyword: '', mainManager: '', mainOrg: '', customerType: '', industry: '', groupType: '', accountOpened: '', recordStatus: '', validationStatus: '', importMode: '', sourceUpdatedStart: '', sourceUpdatedEnd: '', pageNo: 1 }); return load(); }
async function openDetail(row) { selected.value = row; detailVisible.value = true; detailLoading.value = true; try { selected.value = normalizeDetail(await getCcrmCustomer(row.id)) || row; } catch (error) { ElMessage.error(error?.message || 'CCRM 客户源详情查询失败'); } finally { detailLoading.value = false; } }
function assignForm(value = {}) { Object.assign(form, blankForm(), value || {}); }
function openCreate() { isEditing.value = false; formErrors.value = {}; assignForm(); formVisible.value = true; }
async function openEdit(row) { isEditing.value = true; formErrors.value = {}; formVisible.value = true; formLoading.value = true; try { const detail = normalizeDetail(await getCcrmCustomer(row.id)); assignForm(detail || row); } catch (error) { assignForm(row); ElMessage.error(error?.message || 'CCRM 客户源详情查询失败'); } finally { formLoading.value = false; } }
const writableFields = ['ccrmCustNo', 'custName', 'unifiedCreditCode', 'customerType', 'industryCode', 'industryName', 'groupType', 'groupName', 'enterpriseType', 'mainManagerId', 'mainManagerName', 'mainOrgId', 'mainOrgName', 'ownershipStatus', 'isAccountOpened', 'accountOpenDate', 'creditAmount', 'creditExposureAmount', 'incomeSettlementAccountName', 'businessInfo'];
function normalizeAccountOpened(value) { return value === true || value === 1 || value === '1' ? 1 : value === false || value === 0 || value === '0' ? 0 : null; }
function savePayload(value, editing) { const payload = {}; writableFields.forEach(field => { if (value[field] !== undefined) payload[field] = value[field]; }); payload.custName = String(payload.custName || '').trim(); payload.unifiedCreditCode = String(payload.unifiedCreditCode || '').trim().toUpperCase(); payload.isAccountOpened = normalizeAccountOpened(payload.isAccountOpened); payload.creditAmount = numeric(payload.creditAmount); payload.creditExposureAmount = numeric(payload.creditExposureAmount); payload.accountOpenDate = payload.accountOpenDate || null; if (!editing) payload.importMode = 'MANUAL'; if (editing) payload.lockVersion = value.lockVersion; return payload; }
async function save(value = form) { const editing = Boolean(value.id || isEditing.value), errors = validateForm(value); formErrors.value = errors; if (Object.keys(errors).length) { ElMessage.warning(Object.values(errors)[0]); return false; } if (editing && isBlank(value.lockVersion)) { ElMessage.warning('修改必须携带乐观锁版本'); return false; } formLoading.value = true; try { if (editing) await updateCcrmCustomer(value.id, savePayload(value, true)); else await createCcrmCustomer(savePayload(value, false)); ElMessage.success(editing ? 'CCRM 客户源记录已保存' : 'CCRM 客户源记录已新增'); formVisible.value = false; await load(); return true; } catch (error) { ElMessage.error(error?.message || '保存失败，请稍后重试'); return false; } finally { formLoading.value = false; } }
async function remove(row) { let result; try { result = await ElMessageBox.prompt('停用后记录仍保留用于历史追溯，请填写停用原因。', '停用 CCRM 客户源记录', { inputType: 'textarea', inputAttrs: { maxlength: 500 }, inputValidator: value => isBlank(value) ? '停用原因不能为空' : true, confirmButtonText: '确认停用', cancelButtonText: '取消' }); } catch (_) { return false; } const reason = String(result?.value || '').trim(); if (!reason) { ElMessage.warning('停用原因不能为空'); return false; } try { await deleteCcrmCustomer(row.id, { reason, lockVersion: row.lockVersion }); ElMessage.success('CCRM 客户源记录已停用'); await load(); return true; } catch (error) { ElMessage.error(error?.message || '停用失败，请稍后重试'); return false; } }
function downloadBlob(blob, filename) { if (!blob || typeof URL?.createObjectURL !== 'function') return; const data = blob instanceof Blob ? blob : new Blob([blob]); const url = URL.createObjectURL(data); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; document.body.appendChild(anchor); anchor.click(); setTimeout(() => { URL.revokeObjectURL(url); anchor.remove(); }, 0); }
async function downloadTemplate() { templateLoading.value = true; try { downloadBlob(await downloadCcrmImportTemplate(), 'CCRM客户源数据导入模板.xlsx'); ElMessage.success('模板下载已开始'); } catch (error) { ElMessage.error(error?.message || '模板下载失败'); } finally { templateLoading.value = false; } }
async function downloadExport() { exporting.value = true; try { downloadBlob(await exportCcrmCustomers(buildQueryParams()), `CCRM客户源数据_${Date.now()}.xlsx`); ElMessage.success('CCRM 客户源数据导出已开始'); } catch (error) { ElMessage.error(error?.message || '导出失败'); } finally { exporting.value = false; } }
function onImportFileChange(uploadFile) { importFile.value = uploadFile?.raw || uploadFile || null; importPreview.value = {}; }
function normalizeImportPreview(result) { const value = result?.data && !result.batchId ? result.data : (result || {}); return { ...value, errors: Array.isArray(value.errors) ? value.errors : (Array.isArray(value.errorDetails) ? value.errorDetails : []) }; }
async function previewFile(file = importFile.value) { if (!file) { ElMessage.warning('请选择 .xlsx 文件'); return null; } if (!String(file.name || '').toLowerCase().endsWith('.xlsx')) { ElMessage.warning('仅支持 .xlsx 文件'); return null; } importLoading.value = true; try { const result = normalizeImportPreview(await previewCcrmImport(file)); importFile.value = file; importPreview.value = result; return result; } catch (error) { ElMessage.error(error?.message || '导入预览失败'); return null; } finally { importLoading.value = false; } }
async function executeImport() { if (!canExecuteImport.value) { ElMessage.warning(importFailedCount.value ? '存在失败行，不能执行整批导入' : '请先完成导入预览'); return false; } try { await ElMessageBox.confirm('预览通过后将按统一社会信用代码优先、CCRM 客户号其次执行新增和更新，是否继续？', '确认执行导入', { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' }); } catch (_) { return false; } importLoading.value = true; try { await executeCcrmImport(importPreview.value.batchId); ElMessage.success('CCRM 客户源数据导入已执行'); importVisible.value = false; importFile.value = null; importPreview.value = {}; await load(); return true; } catch (error) { ElMessage.error(error?.message || '导入执行失败'); return false; } finally { importLoading.value = false; } }
function customerTypeLabel(value) { return ({ CORP: '对公客户', RETAIL: '零售客户' }[value] || value || '-'); }
function managerLabel(row) { return row?.mainManagerName ? `${row.mainManagerName}${row.mainManagerId ? `（${row.mainManagerId}）` : ''}` : (row?.mainManagerId || '-'); }
function ownershipLabel(value) { return ({ ASSIGNED: '已分配', UNASSIGNED: '未分配', UNKNOWN: '未知' }[value] || value || '未知'); }
function accountLabel(value) { return value === true || value === 1 || value === '1' ? '已开户' : value === false || value === 0 || value === '0' ? '未开户' : '未知'; }
function accountTagType(value) { return value === true || value === 1 || value === '1' ? 'success' : value === false || value === 0 || value === '0' ? 'info' : 'warning'; }
function validationLabel(value) { return ({ VALID: '有效', INCOMPLETE: '缺字段', CONFLICT: '冲突' }[value] || value || '未知'); }
function validationTagType(value) { return ({ VALID: 'success', INCOMPLETE: 'warning', CONFLICT: 'danger' }[value] || 'info'); }
function importModeLabel(value) { return ({ API: '接口', FILE: '文件', MANUAL: '人工' }[value] || value || '-'); }
function recordStatusLabel(value) { return ({ ACTIVE: '有效', INACTIVE: '停用' }[value] || value || '未知'); }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-'; }
function auditLabel(user, time) { return [user, formatTime(time)].filter(value => value && value !== '-').join(' · ') || '-'; }
function money(value) { if (isBlank(value)) return '-'; const number = Number(value); return Number.isFinite(number) ? `¥${number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '-'; }

load();
</script>

<style scoped lang="scss">
.page-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:12px}.page-head h1{margin:0;font-size:18px}.scope-tip{margin:5px 0 0;color:#909399;font-size:12px;line-height:1.5}.head-actions{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.boundary-alert{margin-bottom:14px}.filter-card{margin-bottom:14px}.filter-card :deep(.el-card__body){padding-bottom:2px}.keyword{width:300px}.filter-select{width:130px}.time-range{display:flex;align-items:center;gap:6px}.time-range :deep(.el-date-editor){width:180px}.filter-actions{margin-left:auto}.source-table{width:100%}.pager{display:flex;justify-content:flex-end;margin-top:14px}.form-section{padding:0 0 4px}.form-section h3{margin:10px 0 12px;padding-left:9px;border-left:3px solid var(--el-color-primary);font-size:15px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 18px}.form-grid :deep(.el-select),.form-grid :deep(.el-date-editor),.form-grid :deep(.el-input){width:100%}.wide-item{grid-column:1 / -1}.source-audit{padding:2px 14px 8px;color:#909399;font-size:12px;background:#f8f9fb}.source-audit p{margin:7px 0}.import-boundary{margin:0 0 14px;color:#606266;line-height:1.6}.selected-file{margin:10px 0;color:#606266}.import-actions{display:flex;gap:8px;margin-top:14px}.import-summary{display:flex;gap:8px;margin:12px 0}.import-error-table{margin-top:12px}@media(max-width:900px){.page-head{flex-direction:column}.head-actions{justify-content:flex-start}.form-grid{grid-template-columns:1fr}.wide-item{grid-column:auto}.time-range{flex-wrap:wrap}.time-range :deep(.el-date-editor){width:100%}.keyword{width:240px}}
</style>
