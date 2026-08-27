<template>
  <main v-bp-overflow-tooltip class="page bp-crud marketing-customer-tags" aria-labelledby="marketing-customer-tags-title">
    <header class="page-head">
      <div>
        <PageTitle id="marketing-customer-tags-title" />
        <span>按标签管理营销客户群；新增标签及客户导入均须审批通过后生效。</span>
      </div>
      <el-button type="primary" @click="openCreate">新增标签</el-button>
    </header>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent><el-form-item label="标签名称"><el-input v-model="query.keyword" clearable /></el-form-item><el-form-item label="标签分类"><el-input v-model="query.category" clearable /></el-form-item><el-form-item label="审批状态"><el-select v-model="query.approvalStatus" clearable style="width:140px"><el-option label="待审批" value="PENDING"/><el-option label="已通过" value="APPROVED"/><el-option label="已驳回" value="REJECTED"/></el-select></el-form-item><el-button type="primary" @click="search">查询</el-button></el-form>
    </el-card>
    <el-table :data="tags" v-loading="loading" border stripe class="customer-tags-table">
      <el-table-column prop="tagName" label="标签名称" min-width="170" />
      <el-table-column prop="tagCategory" label="标签分类" width="130" />
      <el-table-column prop="tagType" label="标签类型" width="120" />
      <el-table-column prop="customerCount" label="客户数量" width="100" />
      <el-table-column prop="approvalStatus" label="审批状态" width="110"><template #default="{row}"><el-tag>{{ approvalLabel(row.approvalStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="status" label="启用状态" width="100"><template #default="{row}">{{row.status==='ENABLED'?'启用':'停用'}}</template></el-table-column>
      <el-table-column label="操作" min-width="270" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="showCustomers(row)">查看客户群</el-button><el-button link type="primary" @click="openImport(row,'APPEND')" :disabled="row.approvalStatus!=='APPROVED'">追加导入</el-button><el-button link type="warning" @click="openImport(row,'REPLACE')" :disabled="row.approvalStatus!=='APPROVED'">全量替换</el-button></template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50, 100]" @change="load"/></div>

    <el-dialog v-model="createVisible" title="新增营销客户标签" width="560px"><el-form :model="tagForm" label-position="top"><el-form-item label="标签名称" required><el-input v-model="tagForm.tagName"/></el-form-item><el-form-item label="标签分类"><el-input v-model="tagForm.tagCategory"/></el-form-item><el-form-item label="标签类型"><el-input v-model="tagForm.tagType"/></el-form-item><el-form-item label="优先级"><el-input-number v-model="tagForm.tagPriority" :min="0"/></el-form-item><el-form-item label="说明"><el-input v-model="tagForm.description" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="createTag">提交审批</el-button></template></el-dialog>

    <el-dialog v-model="importVisible" :title="`${importMode==='APPEND'?'追加导入':'全量替换'} · ${selectedTag?.tagName||''}`" width="620px">
      <el-alert v-if="importMode==='REPLACE'" title="全量替换仅在全部有效客户审批通过后一次生效；任一客户被拒绝或文件存在错误，现有客户群保持不变。" type="warning" :closable="false" show-icon/>
      <el-upload drag :auto-upload="false" :limit="1" accept=".xlsx,.xls,.csv" :on-change="fileChanged"><el-icon><UploadFilled/></el-icon><div class="el-upload__text">拖拽文件到此处或点击选择</div></el-upload>
      <template #footer><el-button plain :loading="templateDownloading" @click="downloadTemplate">下载导入模板</el-button><el-button @click="importVisible=false">取消</el-button><el-button type="primary" :loading="importing" @click="submitImport">创建导入批次</el-button></template>
    </el-dialog>

    <el-drawer v-model="customersVisible" :title="`标签客户群 · ${selectedTag?.tagName||''}`" size="min(1000px,96vw)"><el-alert title="只展示已审批并已写入 MARKETING_CUSTOMER_TAG_REL 的正式客户关系。" type="info" :closable="false"/><el-table :data="customers" v-loading="customerLoading" border class="drawer-table"><el-table-column prop="custNo" label="客户号" width="140"/><el-table-column prop="custName" label="企业名称" min-width="180"/><el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190"/><el-table-column prop="accountOpened" label="开户状态" width="110"/><el-table-column prop="mainManagerId" label="主办客户经理" width="140"/><el-table-column prop="effectiveTime" label="标签生效时间" min-width="165"/></el-table></el-drawer>
  </main>
</template>
<script setup>
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import PageTitle from '@/components/PageTitle.vue';
import { createCustomerTagImportBatch, createMarketingCustomerTag, listMarketingCustomerTagCustomers, listMarketingCustomerTags, previewCustomerTagImportBatch, listCustomerTagImportBatches, getCustomerTagImportBatchDetails, downloadCustomerTagImportSourceFile, downloadCustomerTagImportTemplate } from '@/api/marketingManagement';
const query=reactive({keyword:'',category:'',approvalStatus:'',pageNo:1,pageSize:20}); const tags=ref([]); const total=ref(0); const loading=ref(false);
const createVisible=ref(false); const saving=ref(false); const tagForm=reactive({tagName:'',tagCategory:'',tagType:'',tagPriority:0,description:''});
const importVisible=ref(false); const importing=ref(false); const importMode=ref('APPEND'); const importFile=ref(null); const selectedTag=ref(null);
const templateDownloading=ref(false);
const customersVisible=ref(false); const customers=ref([]); const customerLoading=ref(false);
const approvalLabel=v=>({PENDING:'待审批',APPROVED:'已通过',REJECTED:'已驳回'}[v]||v||'-');
async function load(){loading.value=true;try{const r=await listMarketingCustomerTags(query);tags.value=r?.records||[];total.value=Number(r?.total||0);}catch(error){ElMessage.error(`标签列表加载失败：${error?.message||'请稍后重试'}`);}finally{loading.value=false;}}
function search(){query.pageNo=1;load();} function openCreate(){Object.assign(tagForm,{tagName:'',tagCategory:'',tagType:'',tagPriority:0,description:''});createVisible.value=true;}
async function createTag(){if(!tagForm.tagName.trim()){ElMessage.warning('标签名称不能为空');return;}saving.value=true;try{await createMarketingCustomerTag(tagForm);ElMessage.success('标签已提交审批');createVisible.value=false;await load();}catch(error){ElMessage.error(`标签新增失败：${error?.message||'请稍后重试'}`);}finally{saving.value=false;}}
function openImport(tag,mode){selectedTag.value=tag;importMode.value=mode;importFile.value=null;importVisible.value=true;} function fileChanged(upload){importFile.value=upload.raw;}
async function submitImport(){if(!importFile.value){ElMessage.warning('请选择导入文件');return;}importing.value=true;try{await previewCustomerTagImportBatch({tagId:selectedTag.value.id,importMode:importMode.value,file:importFile.value});await createCustomerTagImportBatch({tagId:selectedTag.value.id,importMode:importMode.value,file:importFile.value});ElMessage.success('标签客户导入批次已创建，客户审批通过后生效');importVisible.value=false;}catch(error){ElMessage.error(`标签客户导入失败：${error?.message||'请检查文件'}`);}finally{importing.value=false;}}
function saveBlob(blob, filename){if(!blob || typeof URL?.createObjectURL!=='function') throw new Error('浏览器不支持文件下载');const data=blob instanceof Blob?blob:new Blob([blob],{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});const url=URL.createObjectURL(data);const anchor=document.createElement('a');anchor.href=url;anchor.download=filename;document.body.appendChild(anchor);anchor.click();setTimeout(()=>{URL.revokeObjectURL(url);anchor.remove();},0);}
async function downloadTemplate(){templateDownloading.value=true;try{saveBlob(await downloadCustomerTagImportTemplate(),'营销客户标签导入模板.xlsx');ElMessage.success('模板下载已开始');}catch(error){ElMessage.error(`模板下载失败：${error?.message||'请稍后重试'}`);}finally{templateDownloading.value=false;}}
async function showCustomers(tag){selectedTag.value=tag;customersVisible.value=true;customerLoading.value=true;try{const r=await listMarketingCustomerTagCustomers(tag.id,{pageNo:1,pageSize:100});customers.value=r?.records||[];}catch(error){customers.value=[];ElMessage.error(`标签客户群加载失败：${error?.message||'请稍后重试'}`);}finally{customerLoading.value=false;}}
// 批次历史/明细/OBS 下载由后续抽屉复用，显式保留目标 API 契约。
void listCustomerTagImportBatches; void getCustomerTagImportBatchDetails; void downloadCustomerTagImportSourceFile;
load();
</script>
<style scoped lang="scss">
.page-head { align-items: flex-start; display: flex; justify-content: space-between; margin-bottom: 14px; }
.page-head h1 { font-size: 18px; margin: 0; }
.page-head span { color: #909399; display: block; font-size: 12px; margin-top: 4px; }
.filter-card { margin-bottom: 14px; }
.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.customer-tags-table { width: 100%; }
.drawer-table { margin-top: 14px; }
</style>
