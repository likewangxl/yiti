<template>
  <main v-bp-overflow-tooltip class="page bp-crud marketing-customer-tags" aria-labelledby="marketing-customer-tags-title">
    <header class="page-head">
      <div>
        <PageTitle id="marketing-customer-tags-title" />
        <span>按标签管理营销客户群；新增标签及客户导入均须审批通过后生效。</span>
      </div>
      <el-button type="primary" @click="openCreate">新增标签</el-button>
    </header>
    <div class="tag-stat-grid" aria-label="标签状态概览">
      <button
        v-for="item in tagStatCards"
        :key="item.key"
        type="button"
        class="tag-stat-card"
        :class="[`tone-${item.tone}`, { 'is-selected': query.viewStatus === item.viewStatus }]"
        :aria-pressed="query.viewStatus === item.viewStatus"
        @click="switchViewStatus(item.viewStatus)"
      >
        <span class="stat-card-top"><span>{{ item.label }}</span><i /></span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </button>
    </div>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent><el-form-item label="标签名称"><el-input v-model="query.keyword" clearable /></el-form-item><el-form-item label="标签类型"><el-select v-model="query.tagType" clearable style="width:160px"><el-option v-for="item in tagTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="状态"><el-select v-model="statusFilter" clearable style="width:140px" @change="filterByStatus"><el-option label="有效" value="ACTIVE"/><el-option label="禁用" value="DISABLED"/><el-option label="待审核" value="PENDING"/><el-option label="已退回" value="REJECTED"/></el-select></el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form>
    </el-card>
    <el-table :data="tags" v-loading="loading" border stripe class="customer-tags-table">
      <el-table-column prop="tagName" label="标签名称" min-width="170" />
      <el-table-column prop="tagType" label="标签类型" width="150"><template #default="{row}">{{ tagTypeLabelOf(row.tagType) }}</template></el-table-column>
      <el-table-column prop="customerCount" label="客户数量" width="100" />
      <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="tagStatusType(row)" effect="plain">{{ tagStatusLabel(row) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" min-width="270" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="showCustomers(row)">查看客户群</el-button><el-button link type="primary" @click="openImport(row,'APPEND')" :disabled="row.approvalStatus!=='APPROVED'">追加导入</el-button><el-button link type="warning" @click="openImport(row,'REPLACE')" :disabled="row.approvalStatus!=='APPROVED'">全量替换</el-button></template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" @change="load"/></div>

    <el-dialog v-model="createVisible" title="新增营销客户标签" width="560px"><el-form :model="tagForm" label-position="top"><el-form-item label="标签名称" required><el-input v-model="tagForm.tagName"/></el-form-item><el-form-item label="标签类型" required><el-select v-model="tagForm.tagType" style="width:100%" placeholder="请选择"><el-option v-for="item in tagTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="优先级"><el-input-number v-model="tagForm.tagPriority" :min="0"/></el-form-item><el-form-item label="说明"><el-input v-model="tagForm.description" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="createTag">提交审批</el-button></template></el-dialog>

    <el-dialog v-model="importVisible" :title="`${importMode==='APPEND'?'追加导入':'全量替换'} · ${selectedTag?.tagName||''}`" width="620px">
      <el-alert v-if="importMode==='REPLACE'" title="全量替换仅在全部有效客户审批通过后一次生效；任一客户被拒绝或文件存在错误，现有客户群保持不变。" type="warning" :closable="false" show-icon/>
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls,.csv" :on-change="fileChanged"><el-icon><UploadFilled/></el-icon><div class="el-upload__text">拖拽文件到此处或点击选择</div></el-upload>
      <template #footer><el-button plain :loading="templateDownloading" @click="downloadTemplate">下载导入模板</el-button><el-button @click="importVisible=false">取消</el-button><el-button type="primary" :loading="importing" @click="submitImport">创建导入批次</el-button></template>
    </el-dialog>

    <el-drawer v-model="customersVisible" :title="`标签客户群 · ${selectedTag?.tagName||''}`" size="min(1000px,96vw)"><el-alert title="只展示已审批并已写入 MARKETING_CUSTOMER_TAG_REL 的正式客户关系。" type="info" :closable="false"/><el-table :data="customers" v-loading="customerLoading" border class="drawer-table"><el-table-column prop="custNo" label="客户号" width="140"/><el-table-column prop="custName" label="企业名称" min-width="180"/><el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190"/><el-table-column prop="accountOpened" label="开户状态" width="110"/><el-table-column prop="mainManagerId" label="主办客户经理" width="140"/><el-table-column prop="effectiveTime" label="标签生效时间" min-width="165"/></el-table></el-drawer>
  </main>
</template>
<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import PageTitle from '@/components/PageTitle.vue';
import { useDict } from '@/composables/useDict';
import { createCustomerTagImportBatch, createMarketingCustomerTag, listMarketingCustomerTagCustomers, listMarketingCustomerTags, previewCustomerTagImportBatch, listCustomerTagImportBatches, getCustomerTagImportBatchDetails, downloadCustomerTagImportSourceFile, downloadCustomerTagImportTemplate } from '@/api/marketingManagement';
const query=reactive({keyword:'',tagType:'',viewStatus:'',pageNo:1,pageSize:20}); const statusFilter=ref(''); const tags=ref([]); const total=ref(0); const loading=ref(false);
const statViewStatuses=['','ACTIVE','PENDING','EXCEPTION'];
const tagStats=reactive({ALL:0,ACTIVE:0,PENDING:0,EXCEPTION:0});
const tagStatCards=computed(()=>[
  {key:'ALL',viewStatus:'',label:'标签总览',value:tagStats.ALL,hint:'全部营销客户标签',tone:'primary'},
  {key:'ACTIVE',viewStatus:'ACTIVE',label:'有效标签',value:tagStats.ACTIVE,hint:'已生效且可使用',tone:'success'},
  {key:'PENDING',viewStatus:'PENDING',label:'待审核标签',value:tagStats.PENDING,hint:'等待标签审批',tone:'warning'},
  {key:'EXCEPTION',viewStatus:'EXCEPTION',label:'异常标签',value:tagStats.EXCEPTION,hint:'禁用或已退回',tone:'danger'}
]);
const {options:tagTypeOptions,labelOf:tagTypeLabelOf,reload:reloadTagTypes}=useDict('CUSTOMER_TAG_TYPE');
const createVisible=ref(false); const saving=ref(false); const tagForm=reactive({tagName:'',tagType:'',tagPriority:0,description:''});
const importVisible=ref(false); const importing=ref(false); const importMode=ref('APPEND'); const importFile=ref(null); const selectedTag=ref(null);
const templateDownloading=ref(false);
const customersVisible=ref(false); const customers=ref([]); const customerLoading=ref(false);
function tagStatusValue(row){if(row.approvalStatus==='PENDING')return'PENDING';if(row.approvalStatus==='REJECTED')return'REJECTED';return row.status==='ENABLED'?'ACTIVE':'DISABLED';}
const tagStatusLabel=row=>({ACTIVE:'有效',DISABLED:'禁用',PENDING:'待审核',REJECTED:'已退回'}[tagStatusValue(row)]||'-');
const tagStatusType=row=>({ACTIVE:'success',DISABLED:'info',PENDING:'warning',REJECTED:'danger'}[tagStatusValue(row)]||'info');
async function load(){loading.value=true;try{const r=await listMarketingCustomerTags(query);tags.value=r?.records||[];total.value=Number(r?.total||0);}catch(error){ElMessage.error(`标签列表加载失败：${error?.message||'请稍后重试'}`);}finally{loading.value=false;}}
async function loadTagStats(){try{const results=await Promise.all(statViewStatuses.map(viewStatus=>listMarketingCustomerTags({viewStatus,pageNo:1,pageSize:1})));statViewStatuses.forEach((viewStatus,index)=>{tagStats[viewStatus||'ALL']=Number(results[index]?.total||0);});}catch(error){ElMessage.error(`标签统计加载失败：${error?.message||'请稍后重试'}`);}}
async function refresh(){await Promise.all([load(),loadTagStats()]);}
function switchViewStatus(viewStatus){query.viewStatus=viewStatus;query.pageNo=1;statusFilter.value=['ACTIVE','DISABLED','PENDING','REJECTED'].includes(viewStatus)?viewStatus:'';load();}
function filterByStatus(viewStatus){query.viewStatus=viewStatus||'';query.pageNo=1;load();}
function search(){query.pageNo=1;Promise.all([reloadTagTypes(),load()]);}
function resetFilters(){query.keyword='';query.tagType='';query.viewStatus='';query.pageNo=1;statusFilter.value='';Promise.all([reloadTagTypes(),refresh()]);}
async function openCreate(){await reloadTagTypes();Object.assign(tagForm,{tagName:'',tagType:'',tagPriority:0,description:''});createVisible.value=true;}
async function createTag(){if(!tagForm.tagName.trim()){ElMessage.warning('标签名称不能为空');return;}if(!tagForm.tagType){ElMessage.warning('请选择标签类型');return;}saving.value=true;try{await createMarketingCustomerTag(tagForm);ElMessage.success('标签已提交审批');createVisible.value=false;await refresh();}catch(error){ElMessage.error(`标签新增失败：${error?.message||'请稍后重试'}`);}finally{saving.value=false;}}
function openImport(tag,mode){selectedTag.value=tag;importMode.value=mode;importFile.value=null;importVisible.value=true;} function fileChanged(upload){importFile.value=upload.raw;}
async function submitImport(){if(!importFile.value){ElMessage.warning('请选择导入文件');return;}importing.value=true;try{await previewCustomerTagImportBatch({tagId:selectedTag.value.id,importMode:importMode.value,file:importFile.value});await createCustomerTagImportBatch({tagId:selectedTag.value.id,importMode:importMode.value,file:importFile.value});ElMessage.success('标签客户导入批次已创建，客户审批通过后生效');importVisible.value=false;}catch(error){ElMessage.error(`标签客户导入失败：${error?.message||'请检查文件'}`);}finally{importing.value=false;}}
function saveBlob(blob, filename){if(!blob || typeof URL?.createObjectURL!=='function') throw new Error('浏览器不支持文件下载');const data=blob instanceof Blob?blob:new Blob([blob],{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});const url=URL.createObjectURL(data);const anchor=document.createElement('a');anchor.href=url;anchor.download=filename;document.body.appendChild(anchor);anchor.click();setTimeout(()=>{URL.revokeObjectURL(url);anchor.remove();},0);}
async function downloadTemplate(){templateDownloading.value=true;try{saveBlob(await downloadCustomerTagImportTemplate(),'营销客户标签导入模板.xlsx');ElMessage.success('模板下载已开始');}catch(error){ElMessage.error(`模板下载失败：${error?.message||'请稍后重试'}`);}finally{templateDownloading.value=false;}}
async function showCustomers(tag){selectedTag.value=tag;customersVisible.value=true;customerLoading.value=true;try{const r=await listMarketingCustomerTagCustomers(tag.id,{pageNo:1,pageSize:100});customers.value=r?.records||[];}catch(error){customers.value=[];ElMessage.error(`标签客户群加载失败：${error?.message||'请稍后重试'}`);}finally{customerLoading.value=false;}}
// 批次历史/明细/OBS 下载由后续抽屉复用，显式保留目标 API 契约。
void listCustomerTagImportBatches; void getCustomerTagImportBatchDetails; void downloadCustomerTagImportSourceFile;
refresh();
</script>
<style scoped lang="scss">
.page-head { align-items: flex-start; display: flex; justify-content: space-between; margin-bottom: 14px; }
.page-head h1 { font-size: 18px; margin: 0; }
.page-head span { color: #909399; display: block; font-size: 12px; margin-top: 4px; }
.tag-stat-grid { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 12px; margin-bottom: 12px; }
.tag-stat-card { position: relative; overflow: hidden; display: grid; gap: 8px; min-height: 112px; padding: 16px 18px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; color: inherit; font: inherit; text-align: left; cursor: pointer; transition: border-color .2s ease, box-shadow .2s ease; }
.tag-stat-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--el-color-primary); content: ''; }
.tag-stat-card:hover { border-color: var(--el-color-primary-light-5); }
.tag-stat-card:focus-visible { outline: 2px solid var(--el-color-primary-light-3); outline-offset: 2px; }
.tag-stat-card.is-selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); box-shadow: 0 0 0 1px var(--el-color-primary-light-7) inset; }
.stat-card-top { display: flex; align-items: center; justify-content: space-between; color: #606266; font-size: 13px; }
.stat-card-top i { width: 8px; height: 8px; border-radius: 50%; background: var(--el-color-primary); box-shadow: 0 0 0 4px var(--el-color-primary-light-9); }
.tag-stat-card strong { color: #303133; font-size: 26px; line-height: 1; }
.tag-stat-card small { color: #909399; font-size: 12px; }
.tag-stat-card.tone-success::before, .tag-stat-card.tone-success .stat-card-top i { background: var(--el-color-success); }
.tag-stat-card.tone-warning::before, .tag-stat-card.tone-warning .stat-card-top i { background: var(--el-color-warning); }
.tag-stat-card.tone-danger::before, .tag-stat-card.tone-danger .stat-card-top i { background: var(--el-color-danger); }
.filter-card { margin-bottom: 14px; }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.customer-tags-table { width: 100%; }
.drawer-table { margin-top: 14px; }
@media (max-width: 760px) { .tag-stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
