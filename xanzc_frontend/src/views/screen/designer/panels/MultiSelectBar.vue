<template>
  <div class="dsn-multi">
    <div class="hint">已选中 {{ store.curComponents.length }} 个组件</div>
    <div class="sec">
      <div class="t">对齐</div>
      <div class="btns">
        <el-button size="small" @click="store.alignSelected('left')">左对齐</el-button>
        <el-button size="small" @click="store.alignSelected('hcenter')">水平居中</el-button>
        <el-button size="small" @click="store.alignSelected('right')">右对齐</el-button>
        <el-button size="small" @click="store.alignSelected('top')">顶对齐</el-button>
        <el-button size="small" @click="store.alignSelected('vcenter')">垂直居中</el-button>
        <el-button size="small" @click="store.alignSelected('bottom')">底对齐</el-button>
      </div>
    </div>
    <div class="sec">
      <div class="t">分布(≥3 个可用)</div>
      <div class="btns">
        <el-button size="small" :disabled="!canDistribute" @click="store.distributeSelected('h')">水平等间距</el-button>
        <el-button size="small" :disabled="!canDistribute" @click="store.distributeSelected('v')">垂直等间距</el-button>
      </div>
    </div>
    <div class="sec">
      <div class="t">成组</div>
      <div class="btns">
        <el-button size="small" type="primary" @click="store.groupSelected()">成组</el-button>
      </div>
    </div>
    <div class="tip">Del 批量删除 / Esc 清空选区 / 方向键批量微移</div>
  </div>
</template>
<script setup>
// 多选工具条——右栏在 curComponents.length > 1 时替代属性面板显示(属性面板仅单选可见)。
// 对齐/分布算法在 utils/align.js 纯函数,store.alignSelected/distributeSelected 应用 patch 并记快照。
import { computed } from 'vue';
import { useScreenDesignerStore } from '@/stores/screenDesigner';
const store = useScreenDesignerStore();
const canDistribute = computed(() => store.curComponents.filter(c => !c.isLock).length >= 3);
</script>
<style scoped>
.dsn-multi { padding: 12px; color: #d5e6ff; }
.hint { font-size: 13px; margin-bottom: 12px; color: #7dd3fc; }
.sec { margin-bottom: 14px; }
.sec .t { font-size: 12px; color: #7d9bc9; margin-bottom: 6px; }
.btns { display: flex; flex-wrap: wrap; gap: 6px; }
.btns .el-button { margin: 0; }
.tip { margin-top: 16px; font-size: 12px; color: #55719c; }
</style>
