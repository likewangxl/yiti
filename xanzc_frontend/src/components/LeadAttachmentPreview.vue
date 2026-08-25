<template>
  <div class="attachment-list">
    <div v-for="file in attachments" :key="file.id" class="attachment-item">
      <span class="attachment-name" :title="file.fileName || file.id">{{ file.fileName || file.id }}</span>
      <span v-if="file.fileSize != null" class="attachment-size">{{ formatSize(file.fileSize) }}</span>
      <div class="attachment-actions">
        <el-button
          v-if="previewKind(file)"
          link
          type="primary"
          :loading="openingId === file.id"
          @click="openPreview(file)"
        >预览</el-button>
        <a :href="downloadUrl(file)" class="download-link" download>下载</a>
      </div>
    </div>

    <el-dialog
      v-model="preview.visible"
      :title="preview.fileName"
      width="min(960px, 92vw)"
      append-to-body
      destroy-on-close
      @closed="clearPreview"
    >
      <div class="preview-body" v-loading="openingId !== ''">
        <el-image
          v-if="preview.kind === 'image' && preview.url"
          :src="preview.url"
          :preview-src-list="[preview.url]"
          fit="contain"
          class="image-preview"
          hide-on-click-modal
        />
        <iframe
          v-else-if="preview.kind === 'pdf' && preview.url"
          :src="preview.url"
          :title="`${preview.fileName} 预览`"
          class="pdf-preview"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

defineProps({
  attachments: { type: Array, default: () => [] }
});

const openingId = ref('');
const preview = reactive({ visible: false, kind: '', url: '', fileName: '' });

const imageExtensions = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg']);
const mimeByExtension = {
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif',
  webp: 'image/webp', bmp: 'image/bmp', svg: 'image/svg+xml', pdf: 'application/pdf'
};

function extensionOf(file) {
  const name = String(file?.fileName || '');
  const index = name.lastIndexOf('.');
  return index >= 0 ? name.slice(index + 1).toLowerCase() : '';
}

function previewKind(file) {
  const fileType = String(file?.fileType || '').toLowerCase();
  const extension = extensionOf(file);
  if (fileType.startsWith('image/') || imageExtensions.has(extension)) return 'image';
  if (fileType === 'application/pdf' || fileType === 'pdf' || extension === 'pdf') return 'pdf';
  return '';
}

function downloadUrl(file) {
  return `/api/files/${encodeURIComponent(file?.id || '')}/download`;
}

function formatSize(bytes) {
  const value = Number(bytes);
  if (!Number.isFinite(value) || value < 0) return '-';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function clearObjectUrl() {
  if (preview.url) URL.revokeObjectURL(preview.url);
  preview.url = '';
}

function clearPreview() {
  clearObjectUrl();
  preview.kind = '';
  preview.fileName = '';
}

async function openPreview(file) {
  const kind = previewKind(file);
  if (!kind || !file?.id) return;
  openingId.value = file.id;
  try {
    const response = await fetch(downloadUrl(file), { credentials: 'same-origin' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const source = await response.blob();
    const extension = extensionOf(file);
    const expectedMime = kind === 'pdf' ? 'application/pdf' : mimeByExtension[extension] || file.fileType || 'image/*';
    const blob = source.type && source.type !== 'application/octet-stream'
      ? source
      : new Blob([source], { type: expectedMime });
    clearObjectUrl();
    preview.kind = kind;
    preview.fileName = file.fileName || file.id;
    preview.url = URL.createObjectURL(blob);
    preview.visible = true;
  } catch (error) {
    ElMessage.error(`附件预览失败：${error?.message || '请稍后重试'}`);
  } finally {
    openingId.value = '';
  }
}

onBeforeUnmount(clearObjectUrl);
</script>

<style scoped lang="scss">
.attachment-list{display:flex;flex-direction:column;gap:8px}.attachment-item{display:flex;align-items:center;gap:10px;padding:8px 10px;border:1px solid #e4e7ed;border-radius:6px;background:#fafafa}.attachment-name{min-width:0;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.attachment-size{color:#909399;font-size:12px}.attachment-actions{display:flex;align-items:center;gap:10px}.download-link{color:#1f5b8f;text-decoration:none;font-size:14px}.download-link:hover{text-decoration:underline}.preview-body{display:flex;justify-content:center;min-height:240px}.image-preview{width:100%;height:min(68vh,680px)}.pdf-preview{width:100%;height:min(72vh,760px);border:0}
</style>
