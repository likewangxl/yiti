// @vitest-environment happy-dom
// TDD Red→Green：ReCockpitController.generateAnnualResult（POST /api/re/cockpit/archive/generate/{year}）
// 补齐 @AuditLog(reasonRequired=true) + ReAnnualGenerateReqDTO.reason（@NotBlank）后，
// 前端配套修复：ArchiveView.vue「生成年度报告」由纯 ElMessageBox 二次确认改为「生成原因」弹窗必填，
// 未填原因点确认不应发起 generateAnnual 请求；填写后需将 reason 一并携带（generateAnnual(year, reason)）。
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

vi.mock('@/api/redengine', () => ({
  getRanking: vi.fn(),
  getOrgTree: vi.fn(),
  generateAnnual: vi.fn(),
  archiveSettlement: vi.fn(),
  exportData: vi.fn()
}));

import { getRanking, getOrgTree, generateAnnual, archiveSettlement } from '@/api/redengine';
import { ElMessage } from 'element-plus';
import ArchiveView from '../archive/ArchiveView.vue';

// el-dialog 桩把 title 透出到 data-dialog-title，便于精确定位「生成原因」弹窗内的按钮/输入框。
const stubs = {
  'el-button': { props: ['loading'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { template: '<div><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-dialog': {
    props: ['title'],
    template: '<div :data-dialog-title="title"><slot /><slot name="footer" /></div>'
  },
  'el-form': {
    props: ['model', 'rules'],
    template: '<form><slot /></form>',
    methods: {
      validate() {
        for (const field of Object.keys(this.rules || {})) {
          const required = (this.rules[field] || []).some((r) => r.required);
          const val = this.model ? this.model[field] : undefined;
          if (required && (val === undefined || val === null || String(val).trim() === '')) {
            return Promise.reject(new Error(field + ' required'));
          }
        }
        return Promise.resolve(true);
      }
    }
  },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-input': {
    props: ['modelValue'],
    template:
      '<textarea class="reason-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>'
  }
};

let wrapper;
beforeEach(() => {
  vi.clearAllMocks();
  getRanking.mockResolvedValue([]);
  getOrgTree.mockResolvedValue([]);
  archiveSettlement.mockResolvedValue(false);
});
afterEach(() => { wrapper?.unmount(); });

async function settle() {
  await flushPromises();
  await nextTick();
  await flushPromises();
}

describe('年度考核归档(ArchiveView) - 生成原因必填', () => {
  it('点击「生成年度报告」未填原因直接确认，不应发起 generateAnnual 请求', async () => {
    wrapper = mount(ArchiveView, { global: { stubs } });
    await settle();

    const genBtn = wrapper.findAll('button').find((b) => b.text().includes('生成年度报告'));
    await genBtn.trigger('click');
    await settle();

    const dialog = wrapper.find('[data-dialog-title="生成年度报告"]');
    expect(dialog.exists()).toBe(true);
    const confirmBtn = dialog.findAll('button').find((b) => b.text().includes('确认生成'));
    await confirmBtn.trigger('click');
    await settle();

    expect(generateAnnual).not.toHaveBeenCalled();
  });

  it('填写生成原因后确认，应携带 reason 调用 generateAnnual', async () => {
    generateAnnual.mockResolvedValue({});
    wrapper = mount(ArchiveView, { global: { stubs } });
    await settle();

    const genBtn = wrapper.findAll('button').find((b) => b.text().includes('生成年度报告'));
    await genBtn.trigger('click');
    await settle();

    const dialog = wrapper.find('[data-dialog-title="生成年度报告"]');
    await dialog.find('.reason-stub').setValue('年度考核期结束，按计划生成归档结果');
    await settle();

    const confirmBtn = dialog.findAll('button').find((b) => b.text().includes('确认生成'));
    await confirmBtn.trigger('click');
    await settle();

    const currentYear = new Date().getFullYear();
    expect(generateAnnual).toHaveBeenCalledWith(currentYear, '年度考核期结束，按计划生成归档结果');
    expect(ElMessage.success).toHaveBeenCalled();
  });
});
