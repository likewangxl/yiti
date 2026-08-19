// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import LeadAttachmentPreview from '../LeadAttachmentPreview.vue';

const ButtonStub = {
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
  emits: ['click'],
};
const DialogStub = {
  props: ['modelValue'],
  template: '<div v-if="modelValue" class="dialog-stub"><slot /></div>',
};
const ImageStub = { props: ['src'], template: '<img class="image-stub" :src="src" />' };
const globalOptions = {
  stubs: { 'el-button': ButtonStub, 'el-dialog': DialogStub, 'el-image': ImageStub },
  directives: { loading: () => {} },
};

describe('LeadAttachmentPreview', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      blob: vi.fn().mockResolvedValue(new Blob(['content'], { type: 'application/octet-stream' })),
    }));
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('图片附件可预览且始终保留下载入口', async () => {
    const wrapper = mount(LeadAttachmentPreview, {
      props: { attachments: [{ id: 'F 1', fileName: '现场照片.png', fileType: 'png', fileSize: 2048 }] },
      global: globalOptions,
    });

    expect(wrapper.text()).toContain('预览');
    expect(wrapper.get('a').attributes('href')).toBe('/api/files/F%201/download');
    await wrapper.get('button').trigger('click');
    await flushPromises();

    expect(fetch).toHaveBeenCalledWith('/api/files/F%201/download', { credentials: 'same-origin' });
    expect(URL.createObjectURL).toHaveBeenCalledOnce();
    expect(wrapper.get('.image-stub').attributes('src')).toBe('blob:preview');
  });

  it('PDF 可内嵌预览，Office 附件仅下载', async () => {
    const wrapper = mount(LeadAttachmentPreview, {
      props: { attachments: [
        { id: 'P1', fileName: '征信报告.pdf', fileType: 'application/pdf' },
        { id: 'X1', fileName: '经营信息.xlsx', fileType: 'xlsx' },
      ] },
      global: globalOptions,
    });

    expect(wrapper.findAll('button')).toHaveLength(1);
    expect(wrapper.findAll('a')).toHaveLength(2);
    await wrapper.get('button').trigger('click');
    await flushPromises();
    expect(wrapper.get('iframe').attributes('src')).toBe('blob:preview');
  });

  it('卸载时释放预览对象 URL', async () => {
    const wrapper = mount(LeadAttachmentPreview, {
      props: { attachments: [{ id: 'P1', fileName: '报告.pdf' }] },
      global: globalOptions,
    });
    await wrapper.get('button').trigger('click');
    await flushPromises();
    wrapper.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  });
});
