<template>
  <div class="export-container">
    <h2 class="page-title">数据导出</h2>

    <div class="export-grid">
      <div
        v-for="item in exportItems"
        :key="item.id"
        class="export-card"
        :class="{ 'is-exporting': exportingType === item.type }"
        @click="handleExport(item)"
      >
        <div class="card-icon">{{ item.icon }}</div>
        <div class="card-name">{{ item.name }}</div>
        <div class="card-format">{{ item.format }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 数据导出。
// F1：script 内 API 全部改走 @/api/redengine（exportData，responseType: 'blob'）。
// 能力裁剪说明：源系统本页固定展示 6 张导出卡片（打分明细/党员先锋记录/违规扣分/联建汇总/
// API数据包/全量档案），但 red-engine-center 的 ReExportController 只支持
// GET /api/re/export/{type}，type 仅 submit|score 两种（见其类注释），源系统那 6 种类型本身
// 也只是纯前端 ElMessage 装饰（handleExport 从未真正调用任何导出接口）。本次移植按 YAGNI
// 只保留能映射到真实后端类型的 2 张卡片（打分明细→score，上报明细→submit），其余 4 张
// 因无对应后端能力直接去掉，不新造假接口。
// 下载文件名：http.js 的 call() 响应拦截器只把 body 解包返回、不透出响应头（见 src/api/http.js
// 的 success 拦截器实现），故这里拿不到 Content-Disposition，直接写死
// `export_${type}.xlsx`——与后端 ReExportController.exportData 自己拼的文件名
// （"export_" + type + ".xlsx"）完全一致，不存在信息损失。
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportData } from '@/api/redengine'

const exportItems = ref([
  { id: 1, name: '打分明细', format: 'Excel', icon: '📊', type: 'score' },
  { id: 2, name: '上报明细', format: 'Excel', icon: '📝', type: 'submit' },
])

const exportingType = ref('')

const handleExport = async (item) => {
  if (exportingType.value) return
  exportingType.value = item.type
  try {
    const blob = await exportData(item.type)
    if (!blob) return
    const data = blob instanceof Blob ? blob : new Blob([blob])
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = `export_${item.type}.xlsx`
    document.body.appendChild(a)
    a.click()
    setTimeout(() => { URL.revokeObjectURL(url); a.remove() }, 0)
    ElMessage.success(`✅ ${item.name} 导出成功`)
  } catch (e) {
    ElMessage.error(e?.message || `${item.name} 导出失败`)
  } finally {
    exportingType.value = ''
  }
}
</script>

<style scoped lang="scss">
.export-container { padding: 0; }
.page-title { font-size: 22px; font-weight: 700; color: #1e293b; margin: 0 0 20px 0; }

.export-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.export-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: all 0.3s;
  text-align: center;

  &:hover {
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
    transform: translateY(-2px);
  }

  &.is-exporting {
    opacity: 0.6;
    pointer-events: none;
  }

  .card-icon { font-size: 32px; margin-bottom: 10px; }
  .card-name { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 4px; }
  .card-format { font-size: 12px; color: #64748b; }
}
</style>
