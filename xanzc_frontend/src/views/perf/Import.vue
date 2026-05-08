<template>
  <div>
    <div class="page-h">
      <h1>数据导入</h1>
      <span class="desc">指标结果 / KPI 结果 / 目标值</span>
    </div>

    <div class="card-section">
      <el-form label-width="100px" size="default">
        <el-form-item label="导入类型">
          <el-radio-group v-model="kind">
            <el-radio value="indicator">指标结果</el-radio>
            <el-radio value="kpi">KPI 结果</el-radio>
            <el-radio value="target">目标值</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数据日期">
          <el-date-picker v-model="date" type="date" style="width:260px" />
        </el-form-item>
        <el-form-item label="方案">
          <el-select v-model="plan" style="width:260px"><el-option value="2026Q2" label="2026Q2 KPI" /></el-select>
        </el-form-item>
        <el-form-item>
          <el-button>📥 下载导入模板</el-button>
        </el-form-item>
      </el-form>

      <el-upload drag action="#" :auto-upload="false" style="margin-top:12px">
        <el-icon style="font-size:48px;color:#1E5BBA"><upload-filled /></el-icon>
        <div class="el-upload__text">
          点击或拖拽 .xlsx 到此处
        </div>
        <template #tip><div class="el-upload__tip">仅支持 xlsx，最大 20MB</div></template>
      </el-upload>
    </div>

    <div class="card-section">
      <div class="section-title">最近导入</div>
      <el-table :data="rows" size="default">
        <el-table-column prop="id" label="批次号" width="190" />
        <el-table-column label="类型" width="110">
          <template #default="{row}"><el-tag class="tag-info" effect="plain">{{ row.type }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="file" label="文件名" min-width="200" />
        <el-table-column prop="uploader" label="导入人" width="140" />
        <el-table-column label="有效/总" width="120" align="right">
          <template #default="{row}">{{ row.valid }}/{{ row.total }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}"><el-tag class="tag-success" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column label="操作" width="120">
          <template #default><el-button link type="primary" size="small">下载错误文件</el-button></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';
import { perfImports } from '@/mock';
import { listImports } from '@/api/perf';
const kind = ref('indicator'), date = ref(new Date(2026,3,22)), plan = ref('2026Q2');
const rows = ref(perfImports);
onMounted(async () => { try { const r = await listImports(); if (r) rows.value = r; } catch {} });
</script>
