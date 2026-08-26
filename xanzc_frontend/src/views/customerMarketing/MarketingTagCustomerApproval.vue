<template>
  <main v-bp-overflow-tooltip class="page bp-crud marketing-tag-customer-approval">
    <PageTitle />
    <el-tabs v-model="tab" @tab-change="load"><el-tab-pane label="待审批" name="pending"/><el-tab-pane label="审批记录" name="history"/></el-tabs>
    <el-form inline><el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="标签 / 企业名称 / 统一社会信用代码"/></el-form-item><el-button type="primary" @click="search">查询</el-button></el-form>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column label="标签名称" min-width="160"><template #default="{row}">{{row.tag?.tagName||row.tagName||'-'}}</template></el-table-column>
      <el-table-column label="标签分类" width="130"><template #default="{row}">{{row.tag?.tagCategory||'-'}}</template></el-table-column>
      <el-table-column label="导入模式" width="110"><template #default="{row}">{{batchOf(row)?.importMode||'-'}}</template></el-table-column>
      <el-table-column label="导入批次" min-width="160"><template #default="{row}">{{batchOf(row)?.batchNo||'-'}}</template></el-table-column>
      <el-table-column label="标签审批" width="110"><template #default="{row}"><el-tag>{{approvalLabel(row.tag?.approvalStatus)}}</el-tag></template></el-table-column>
      <el-table-column prop="pendingCustomerCount" label="待审批客户" width="120"/>
      <el-table-column prop="approvedCustomerCount" label="已通过" width="90"/>
      <el-table-column prop="rejectedCustomerCount" label="已拒绝" width="90"/>
      <el-table-column v-if="tab==='pending'" label="操作" min-width="210" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button v-if="row.tag?.approvalStatus==='PENDING'" link type="success" @click="approveTagOnly(row)">标签通过</el-button><el-button link type="primary" :disabled="!row.pendingCustomerCount" @click="openCustomers(row)">待审批客户</el-button></template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination v-model:current-page="query.pageNo" :total="total" layout="total, prev, pager, next" @change="load"/></div>

    <el-drawer v-model="drawerVisible" :title="`待审批客户 · ${selectedSummary?.tag?.tagName||''}`" size="min(1100px,97vw)">
      <div class="drawer-toolbar"><el-input v-model="customerKeyword" clearable placeholder="企业名称 / 统一社会信用代码" style="width:300px" @keyup.enter="loadCustomers"/><div><el-button :disabled="!selectedIds.length" type="success" @click="approveSelected(false)">批量通过</el-button><el-button :disabled="!selectedIds.length" type="danger" @click="rejectSelected(false)">批量驳回</el-button><el-button type="primary" @click="approveSelected(true)">全部通过</el-button></div></div>
      <el-table :data="customers" v-loading="customerLoading" border stripe @selection-change="selectionChanged"><el-table-column type="selection" width="48"/><el-table-column prop="rowNo" label="原始行" width="80"/><el-table-column prop="custName" label="企业名称" min-width="170"/><el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="190"/><el-table-column prop="customerChangeType" label="客户匹配" width="145"/><el-table-column prop="approvalStatus" label="审批状态" width="100"/><el-table-column label="开户状态" width="100"><template #default="{row}">{{row.isAccountOpened===1?'已开户':'以主档为准'}}</template></el-table-column><el-table-column label="当前主办" min-width="130"><template #default="{row}">{{row.mainManagerId||'以主档为准'}}</template></el-table-column><el-table-column label="操作" width="150" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="success" @click="approveOne(row)">通过</el-button><el-button link type="danger" @click="rejectOne(row)">驳回</el-button></template></el-table-column></el-table>
    </el-drawer>
  </main>
</template>
<script setup>
import { reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageTitle from '@/components/PageTitle.vue';
import { approveCustomerTag, approveTagCustomer, approveTagCustomers, getPendingTagCustomers, listPendingTagCustomerApprovals, listTagCustomerApprovalHistory, rejectCustomerTag, rejectTagCustomer, rejectTagCustomers } from '@/api/marketingManagement';
const TAG_APPROVAL_REQUIRED='CUST-40905';
const tab=ref('pending'); const query=reactive({keyword:'',pageNo:1,pageSize:20}); const rows=ref([]); const total=ref(0); const loading=ref(false);
const drawerVisible=ref(false); const selectedSummary=ref(null); const customers=ref([]); const customerLoading=ref(false); const selectedIds=ref([]); const customerKeyword=ref('');
const batchOf=row=>row?.batches?.[0]||null; const approvalLabel=v=>({PENDING:'待审批',APPROVED:'已通过',REJECTED:'已驳回'}[v]||v||'-');
async function load(){loading.value=true;try{const r=await(tab.value==='pending'?listPendingTagCustomerApprovals(query):listTagCustomerApprovalHistory(query));rows.value=r?.records||[];total.value=Number(r?.total||0);}catch(error){rows.value=[];ElMessage.error(`标签审批列表加载失败：${error?.message||'请稍后重试'}`);}finally{loading.value=false;}}
function search(){query.pageNo=1;load();}
async function approveTagOnly(summary){try{await ElMessageBox.confirm(`确认通过标签【${summary.tag.tagName}】审批请求？`,'标签审批');await approveCustomerTag(summary.tag.id,{});ElMessage.success('标签审批已通过');await load();}catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(`标签审批失败：${error?.message||'请稍后重试'}`);}}
async function openCustomers(summary){selectedSummary.value=summary;drawerVisible.value=true;customerKeyword.value='';await loadCustomers();}
async function loadCustomers(){customerLoading.value=true;try{const r=await getPendingTagCustomers(selectedSummary.value.tag.id,{keyword:customerKeyword.value,pageNo:1,pageSize:100});customers.value=r?.records||[];}catch(error){customers.value=[];ElMessage.error(`待审批客户加载失败：${error?.message||'请稍后重试'}`);}finally{customerLoading.value=false;}}
function selectionChanged(items){selectedIds.value=items.map(item=>item.id);}
function payload(ids,allPending=false,approveTag=false,opinion=''){return{tagId:selectedSummary.value.tag.id,batchId:batchOf(selectedSummary.value)?.id,detailIds:ids,allPending,approveTag,opinion};}
async function withTagConfirmation(action,request){try{return await action(request);}catch(error){if(error?.code!==TAG_APPROVAL_REQUIRED)throw error;const tagName=selectedSummary.value.tag.tagName;await ElMessageBox.confirm(`标签【${tagName}】尚未审批，是否先通过标签审批请求？`,'标签审批前置确认',{type:'warning'});await approveCustomerTag(selectedSummary.value.tag.id,{});return action({...request, approveTag: true});}}
async function approveSelected(allPending){if(!allPending&&!selectedIds.value.length)return;try{const request=payload(selectedIds.value,allPending,false);await withTagConfirmation(approveTagCustomers,request);ElMessage.success('客户审批已完成');await loadCustomers();await load();}catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(`客户审批失败：${error?.message||'请刷新后重试'}`);}}
async function rejectSelected(allPending){try{const {value}=await ElMessageBox.prompt('请输入驳回原因','批量驳回',{inputValidator:v=>!!v?.trim()||'驳回原因不能为空'});await rejectTagCustomers(payload(selectedIds.value,allPending,false,value));ElMessage.success('客户已驳回');await loadCustomers();await load();}catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(`驳回失败：${error?.message||'请稍后重试'}`);}}
async function approveOne(row){try{await withTagConfirmation(data=>approveTagCustomer(row.id,data),{approveTag:false,opinion:''});ElMessage.success('客户审批已通过');await loadCustomers();await load();}catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(`审批失败：${error?.message||'请刷新后重试'}`);}}
async function rejectOne(row){try{const {value}=await ElMessageBox.prompt('请输入驳回原因','客户驳回',{inputValidator:v=>!!v?.trim()||'驳回原因不能为空'});await rejectTagCustomer(row.id,{opinion:value});ElMessage.success('客户已驳回');await loadCustomers();await load();}catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(`驳回失败：${error?.message||'请稍后重试'}`);}}
// 保留标签拒绝 API，页面待产品确认入口位置后复用。
void rejectCustomerTag;
load();
</script>
<style scoped>.pager{display:flex;justify-content:flex-end;margin-top:14px}.drawer-toolbar{display:flex;justify-content:space-between;gap:14px;margin-bottom:14px}</style>
