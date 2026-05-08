<!--
  预置报表 —— 对应 HTML RptPreset
  接入 yiti API：GET /api/reports/presets（mock 兜底）
-->
<template>
  <div class="rpt-presets">
    <div class="page-h">
      <h1>预置报表</h1>
      <span class="desc">点击卡片直达对应报表 / 统计页</span>
    </div>

    <div class="grid" v-loading="loading">
      <div v-for="r in rows" :key="r.code || r.title" class="card-item" @click="onOpen(r)">
        <div class="t">{{ r.title }}</div>
        <div class="d">{{ r.desc }}</div>
        <div class="f">
          <span class="v">访问 {{ r.visits }} 次/月</span>
          <a class="more">查看 →</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import { listPresets } from '@/api/report';
import { reportPresets } from '@/mock';

const router = useRouter();
const rows = ref(reportPresets);
const loading = ref(false);

onMounted(async () => {
  loading.value = true;
  try {
    const r = await listPresets();
    if (Array.isArray(r) && r.length) rows.value = r;
  } finally { loading.value = false; }
});

function onOpen(item) {
  if (!item?.route) {
    ElMessage.info(`「${item.title}」尚未配置路由`);
    return;
  }
  // 路由已配置则跳转；未注册时优雅降级为提示，避免 console 噪音
  const target = router.resolve(item.route);
  if (!target.matched.length) {
    ElMessage.info(`「${item.title}」即将上线（路由 ${item.route} 待开发）`);
    return;
  }
  router.push(item.route);
}
</script>

<style lang="scss" scoped>
.rpt-presets {
  .page-h { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
    h1 { font-size: 18px; font-weight: 600; color: $text-1; }
    .desc { color: $text-3; font-size: 12px; }
  }
  .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
  .card-item {
    background: #fff;
    border: 1px solid $border-1;
    border-radius: 4px;
    padding: 18px 20px;
    cursor: pointer;
    transition: .15s;
    &:hover { border-color: $primary-400; box-shadow: 0 4px 12px rgba(30,91,186,.15); transform: translateY(-2px); }
    .t { font-size: 16px; font-weight: 600; color: $text-1; margin-bottom: 8px; }
    .d { font-size: 13px; color: $text-3; line-height: 1.6; min-height: 42px; }
    .f { margin-top: 14px; display: flex; align-items: center; padding-top: 12px; border-top: 1px dashed $border-1;
      .v { font-size: 12px; color: $text-3; }
      .more { margin-left: auto; color: $primary; font-size: 13px; }
    }
  }
}
</style>
