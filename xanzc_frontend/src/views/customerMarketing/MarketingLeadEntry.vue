<template>
  <main v-bp-overflow-tooltip class="page bp-crud marketing-lead-entry" aria-labelledby="marketing-lead-entry-title">
    <header class="page-head">
      <div>
        <PageTitle id="marketing-lead-entry-title" />
        <span>展示当前登录人的线索录入记录；支持单条录入和批量导入，存量客户会反显客户主档信息。</span>
      </div>
    </header>
    <el-tabs v-model="activeTab" @tab-change="loadActive">
      <el-tab-pane label="线索录入记录" name="manual">
        <el-card shadow="never" class="filter-card">
          <div class="toolbar">
            <el-form inline @submit.prevent><el-form-item label="关键词"><el-input v-model="leadQuery.keyword" clearable placeholder="线索编号 / 企业名称 / 统一社会信用代码" /></el-form-item><el-form-item label="状态"><el-select v-model="leadQuery.status" clearable style="width:140px"><el-option v-for="item in leadStatuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-button type="primary" @click="searchLeads">查询</el-button></el-form>
            <el-button type="primary" @click="openCreate">新增线索</el-button>
          </div>
        </el-card>
        <el-table :data="leads" v-loading="leadLoading" border stripe class="lead-entry-table">
          <el-table-column prop="leadNo" label="线索编号" min-width="160" />
          <el-table-column prop="custName" label="企业名称" min-width="180" />
          <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190" />
          <el-table-column prop="leadSource" label="线索来源" width="110"><template #default>手工录入</template></el-table-column>
          <el-table-column prop="entryEmpId" label="录入人" width="110" />
          <el-table-column prop="entryTime" label="录入时间" min-width="165"><template #default="{row}">{{ formatTime(row.entryTime) }}</template></el-table-column>
          <el-table-column prop="leadStatus" label="状态" width="110"><template #default="{row}"><el-tag>{{ statusLabel(row.leadStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="210" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="showLead(row)">详情</el-button><el-button v-if="row.leadStatus === 'DRAFT'" link type="primary" @click="editLead(row)">编辑</el-button><el-button v-if="row.leadStatus === 'DRAFT'" link type="success" @click="submitLead(row)">提交审批</el-button></template></el-table-column>
        </el-table>
        <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="leadTotal" v-model:current-page="leadQuery.pageNo" v-model:page-size="leadQuery.pageSize" :page-sizes="[10, 20, 50, 100]" @change="loadLeads" /></div>
      </el-tab-pane>

      <el-tab-pane label="批量导入" name="imports">
        <el-card shadow="never" class="filter-card">
          <div class="toolbar">
            <el-form inline @submit.prevent><el-form-item label="导入文件名"><el-input v-model="batchQuery.keyword" clearable /></el-form-item><el-form-item label="导入状态"><el-select v-model="batchQuery.status" clearable style="width:150px"><el-option label="待确认" value="WAITING_CONFIRM" /><el-option label="成功" value="COMPLETED" /><el-option label="失败" value="ALL_FAILED" /></el-select></el-form-item><el-button type="primary" @click="searchBatches">查询</el-button></el-form>
            <el-upload :show-file-list="false" :auto-upload="false" accept=".xlsx,.xls,.csv" :on-change="importFile"><el-button type="primary" :loading="uploading">导入</el-button></el-upload>
          </div>
        </el-card>
        <el-table :data="batches" v-loading="batchLoading" border stripe class="lead-entry-table">
          <el-table-column prop="sourceFileName" label="导入文件名" min-width="220" />
          <el-table-column prop="importTime" label="导入时间" min-width="165"><template #default="{row}">{{ formatTime(row.importTime) }}</template></el-table-column>
          <el-table-column prop="importEmpId" label="导入人" width="110" />
          <el-table-column label="导入情况" width="120"><template #default="{row}"><el-tag :type="batchType(row.importStatus)">{{ batchLabel(row.importStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="统计" min-width="190"><template #default="{row}">总数 {{ row.totalCount || 0 }} / 成功 {{ row.validCount || 0 }} / 失败 {{ failureCount(row) }}</template></el-table-column>
          <el-table-column label="操作" width="120" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="openBatch(row)">详情</el-button></template></el-table-column>
        </el-table>
        <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="batchTotal" v-model:current-page="batchQuery.pageNo" v-model:page-size="batchQuery.pageSize" :page-sizes="[10, 20, 50, 100]" @change="loadBatches" /></div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑线索' : '新增线索'" width="min(880px, 95vw)" destroy-on-close>
      <el-alert v-if="matchedCustomer" :title="`已匹配客户主档：${matchedCustomer.custName || ''}，开户状态：${matchedCustomer.isAccountOpened === 1 ? '已开户' : '未开户'}，主办：${matchedCustomer.mainManagerId || '无'}`" type="warning" :closable="false" show-icon />
      <el-form :model="form" label-position="top" class="form-grid">
        <el-form-item label="统一社会信用代码" required><el-input v-model="form.unifiedCreditCode" maxlength="18" @blur="lookupCustomer" /></el-form-item>
        <el-form-item label="企业名称" required><el-input v-model="form.custName" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactMobile" /></el-form-item>
        <el-form-item label="注册地址"><el-input v-model="form.registeredAddress" /></el-form-item>
        <el-form-item label="经营地址"><el-input v-model="form.businessAddress" /></el-form-item>
        <el-form-item label="所属行业"><el-input v-model="form.industry" /></el-form-item>
        <el-form-item label="客户类型"><el-input v-model="form.customerType" /></el-form-item>
        <el-form-item label="授信敞口"><el-input-number v-model="form.creditExposureAmount" :min="0" :controls="false" /></el-form-item>
        <el-form-item label="分配方式"><el-select v-model="form.distributionMode"><el-option label="公共线索" value="PUBLIC" /><el-option label="主办人" value="OWNER" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="formVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveLead">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="leadDetailVisible" title="线索详情" size="min(760px, 95vw)"><el-descriptions v-if="leadDetail?.lead" :column="2" border><el-descriptions-item label="线索编号">{{ leadDetail.lead.leadNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel(leadDetail.lead.leadStatus) }}</el-descriptions-item><el-descriptions-item label="企业名称">{{ leadDetail.lead.custName }}</el-descriptions-item><el-descriptions-item label="统一社会信用代码">{{ leadDetail.lead.unifiedCreditCode }}</el-descriptions-item><el-descriptions-item label="当前主办">{{ leadDetail.currentCustomer?.mainManagerId || '无' }}</el-descriptions-item><el-descriptions-item label="开户状态">{{ leadDetail.currentCustomer?.isAccountOpened === 1 ? '已开户' : '未开户' }}</el-descriptions-item></el-descriptions></el-drawer>
    <MarketingLeadImportDetailDrawer v-model="batchDrawerVisible" :batch="selectedBatch" @confirm-action="confirmBatch" />
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import MarketingLeadImportDetailDrawer from '@/components/MarketingLeadImportDetailDrawer.vue';
import { createLeadImportBatch, createMarketingLead, getMarketingLead, listLeadImportBatches, listManualLeads, lookupMarketingCustomerByCreditCode, submitMarketingLead, updateMarketingLead, confirmLeadImportBatch, downloadLeadImportSourceFile, downloadLeadImportErrorFile } from '@/api/marketingManagement';

const activeTab = ref('manual');
const PROCESS_VALID = 'PROCESS_VALID';
const ABANDON_REIMPORT = 'ABANDON_REIMPORT';
const leadQuery = reactive({ keyword: '', status: '', leadSource: 'MANUAL', pageNo: 1, pageSize: 20 });
const batchQuery = reactive({ keyword: '', status: '', pageNo: 1, pageSize: 20 });
const leads = ref([]); const leadTotal = ref(0); const leadLoading = ref(false);
const batches = ref([]); const batchTotal = ref(0); const batchLoading = ref(false); const uploading = ref(false);
const formVisible = ref(false); const saving = ref(false); const form = reactive(emptyForm()); const matchedCustomer = ref(null);
const leadDetailVisible = ref(false); const leadDetail = ref(null);
const batchDrawerVisible = ref(false); const selectedBatch = ref(null);
const leadStatuses = [{label:'草稿',value:'DRAFT'},{label:'审批中',value:'IN_APPROVAL'},{label:'已通过',value:'APPROVED'},{label:'已驳回',value:'REJECTED'}];

function emptyForm(){ return { id:null, custName:'', unifiedCreditCode:'', contactPerson:'', contactMobile:'', registeredAddress:'', businessAddress:'', industry:'', customerType:'', creditExposureAmount:null, distributionMode:'PUBLIC' }; }
const pageRows = result => result?.records || result?.list || [];
const formatTime = value => value ? String(value).replace('T',' ').slice(0,19) : '-';
const statusLabel = value => ({DRAFT:'草稿',SUBMITTED:'已提交',IN_APPROVAL:'审批中',APPROVED:'已通过',REJECTED:'已驳回',CANCELLED:'已取消'}[value] || value || '-');
const batchLabel = value => ({WAITING_CONFIRM:'待确认',COMPLETED:'成功',ALL_FAILED:'失败',ABANDONED:'已放弃',IMPORTING:'处理中'}[value] || value || '-');
const batchType = value => ({COMPLETED:'success',WAITING_CONFIRM:'warning',ALL_FAILED:'danger',ABANDONED:'info'}[value] || 'info');
const failureCount = row => Number(row.rejectedCount || 0) + Number(row.errorCount || 0) + Number(row.warningCount || 0);

async function loadLeads(){ leadLoading.value=true; try { const result=await listManualLeads(leadQuery); leads.value=pageRows(result); leadTotal.value=Number(result?.total||0); } catch (error) { ElMessage.error(`线索录入记录加载失败：${error?.message||'请稍后重试'}`); } finally { leadLoading.value=false; } }
async function loadBatches(){ batchLoading.value=true; try { const result=await listLeadImportBatches(batchQuery); batches.value=pageRows(result); batchTotal.value=Number(result?.total||0); } catch (error) { ElMessage.error(`导入记录加载失败：${error?.message||'请稍后重试'}`); } finally { batchLoading.value=false; } }
function loadActive(){ activeTab.value==='manual' ? loadLeads() : loadBatches(); }
function searchLeads(){ leadQuery.pageNo=1; loadLeads(); } function searchBatches(){ batchQuery.pageNo=1; loadBatches(); }
function openCreate(){ Object.assign(form,emptyForm()); matchedCustomer.value=null; formVisible.value=true; }
async function editLead(row){ const detail=await getMarketingLead(row.id); Object.assign(form,emptyForm(),detail?.lead||row,{id:row.id}); matchedCustomer.value=detail?.currentCustomer||null; formVisible.value=true; }
async function lookupCustomer(){ if(!form.unifiedCreditCode || form.unifiedCreditCode.length!==18) return; try { matchedCustomer.value=await lookupMarketingCustomerByCreditCode(form.unifiedCreditCode); if(matchedCustomer.value){ Object.entries(matchedCustomer.value).forEach(([key,value])=>{ if(value!=null && key in form) form[key]=value; }); ElMessage.warning('已反显客户主档，请确认企业详细参数后保存'); } } catch (error) { ElMessage.error(`客户主档查询失败：${error?.message||'请稍后重试'}`); } }
async function saveLead(){ if(!form.custName || !form.unifiedCreditCode){ ElMessage.warning('企业名称和统一社会信用代码不能为空'); return; } saving.value=true; const payload={...form}; delete payload.id; try { form.id ? await updateMarketingLead(form.id,payload) : await createMarketingLead(payload); ElMessage.success('线索草稿已保存'); formVisible.value=false; await loadLeads(); } catch (error) { ElMessage.error(`线索保存失败：${error?.message||'请稍后重试'}`); } finally { saving.value=false; } }
async function showLead(row){ try { leadDetail.value=await getMarketingLead(row.id); leadDetailVisible.value=true; } catch (error) { ElMessage.error(`线索详情加载失败：${error?.message||'请稍后重试'}`); } }
async function submitLead(row){ try { await ElMessageBox.confirm(`确认提交线索 ${row.leadNo} 审批？`,'提交审批'); await submitMarketingLead(row.id); ElMessage.success('已提交审批'); await loadLeads(); } catch (error) { if(error!=='cancel'&&error!=='close') ElMessage.error(`提交失败：${error?.message||'请稍后重试'}`); } }
async function importFile(upload){ if(!upload?.raw || uploading.value) return; uploading.value=true; try { await createLeadImportBatch(upload.raw); ElMessage.success('导入文件已处理'); await loadBatches(); } catch (error) { ElMessage.error(`导入失败：${error?.message||'请检查文件'}`); } finally { uploading.value=false; } }
function openBatch(row){ selectedBatch.value=row; batchDrawerVisible.value=true; }
async function confirmBatch(action){ const normalizedAction=action===PROCESS_VALID?PROCESS_VALID:ABANDON_REIMPORT; const actionLabel=normalizedAction===PROCESS_VALID?'仅处理正常数据':'放弃并重新导入'; try { const {value}=await ElMessageBox.prompt(`确认${actionLabel}？`,'导入批次确认',{inputPlaceholder:'可填写备注'}); await confirmLeadImportBatch(selectedBatch.value.id,{action:normalizedAction,remark:value}); ElMessage.success('批次已处理'); batchDrawerVisible.value=false; await loadBatches(); } catch (error) { if(error!=='cancel'&&error!=='close') ElMessage.error(`批次确认失败：${error?.message||'请稍后重试'}`); } }

// 文件下载由 MarketingLeadImportDetailDrawer 调用，保留显式引用用于页面契约审计。
void downloadLeadImportSourceFile; void downloadLeadImportErrorFile;
loadLeads();
</script>

<style scoped lang="scss">
.page-head { align-items: flex-start; display: flex; justify-content: space-between; margin-bottom: 14px; }
.page-head h1 { font-size: 18px; margin: 0; }
.page-head span { color: #909399; display: block; font-size: 12px; margin-top: 4px; }
.filter-card { margin-bottom: 14px; }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.toolbar { align-items: flex-start; display: flex; gap: 16px; justify-content: space-between; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.lead-entry-table { width: 100%; }
.form-grid { display: grid; gap: 0 16px; grid-template-columns: repeat(2, minmax(0, 1fr)); }
@media (max-width: 680px) { .toolbar { flex-direction: column; } .form-grid { grid-template-columns: 1fr; } }
</style>
