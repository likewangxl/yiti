<template>
  <div>
    <div class="page-h">
      <h1>KPI 规则</h1>
      <span class="desc">方案 · 权重 · 公式预览 · 计分上下限</span>
      <div class="actions">
        <el-button type="primary">+ 新增方案</el-button>
      </div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="方案名称"><el-input v-model="kw" placeholder="" style="width:200px" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" style="width:160px"><el-option value="" label="全部" /><el-option value="启用" label="启用" /><el-option value="试运行" label="试运行" /></el-select>
        </el-form-item>
        <el-form-item label="适用机构"><el-select v-model="org" style="width:200px"><el-option value="" label="全部" /></el-select></el-form-item>
        <el-form-item label="更新时间"><el-date-picker v-model="t" type="daterange" style="width:280px" /></el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table :data="rows" size="default">
        <el-table-column prop="code" label="方案编码" width="120" />
        <el-table-column label="方案名称" min-width="200">
          <template #default="{row}"><a class="link">{{ row.name }}</a></template>
        </el-table-column>
        <el-table-column prop="scope" label="适用范围" min-width="200" />
        <el-table-column prop="items" label="指标项" width="100" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="row.status === '启用' ? 'tag-success' : 'tag-warning'" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="操作" width="200">
          <template #default>
            <el-button link type="primary" size="small">查看</el-button>
            <el-button link type="primary" size="small">复制版本</el-button>
            <el-button link type="primary" size="small">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { perfKpiRules } from '@/mock';
import { listKpiRules } from '@/api/perf';
const rows = ref(perfKpiRules);
const kw = ref(''), status = ref(''), org = ref(''), t = ref(null);
onMounted(async () => { try { const r = await listKpiRules(); if (r) rows.value = r; } catch {} });
</script>

<style lang="scss" scoped>
.table { padding: 0; }
.link { color: $primary; cursor: pointer; }
</style>
