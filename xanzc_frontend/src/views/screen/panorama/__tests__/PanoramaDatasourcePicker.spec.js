// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import PanoramaDatasourcePicker from '../PanoramaDatasourcePicker.vue';

const sources = [
  { id: 77, dsName: '机构指标汇总' },
  { id: 78, ds_name: '机构时序明细' },
  { id: 79, dsCode: 'DS_CODE_ONLY' },
  { id: 80, __compositionColumnsUnsupported: true, dsName: '不兼容构成来源' },
  { id: 81 }
];

const mountedWrappers = [];

function mountPicker(props = {}) {
  const wrapper = mount(PanoramaDatasourcePicker, {
    attachTo: document.body,
    attrs: { 'data-testid': 'slot-datasource', id: 'panorama-datasource' },
    props: { sources, modelValue: '', ...props },
    global: { plugins: [ElementPlus] }
  });
  mountedWrappers.push(wrapper);
  return wrapper;
}

function visibleOptions() {
  return [...document.body.querySelectorAll('.panorama-datasource-picker__popper .el-select-dropdown__item')]
    .filter(option => option.style.display !== 'none');
}

afterEach(() => {
  mountedWrappers.splice(0).forEach(wrapper => wrapper.unmount());
  document.body.querySelectorAll('.panorama-datasource-picker__popper').forEach(node => node.remove());
  document.body.querySelectorAll('.panorama-datasource-picker').forEach(node => node.remove());
});

describe('PanoramaDatasourcePicker', () => {
  it('使用真实 Element Plus DOM 点击打开、按标签选择并以字符串 ID 发出 update/change', async () => {
    const wrapper = mountPicker();
    const picker = wrapper.get('[data-testid="slot-datasource"]');

    await picker.get('.el-select__wrapper').trigger('click');
    await flushPromises();

    const popper = document.body.querySelector('.panorama-datasource-picker__popper');
    expect(popper).toBeTruthy();
    expect(popper.closest('body')).toBe(document.body);
    expect([...popper.querySelectorAll('.el-select-dropdown__item')]
      .find(option => option.textContent.includes('机构指标汇总'))?.textContent).toContain('机构指标汇总');

    await [...popper.querySelectorAll('.el-select-dropdown__item')]
      .find(option => option.textContent.includes('机构指标汇总'))
      .click();
    await flushPromises();

    expect(wrapper.emitted('update:modelValue')).toEqual([['77']]);
    expect(wrapper.emitted('change')).toEqual([['77']]);
    await wrapper.setProps({ modelValue: '77' });
    await flushPromises();
    expect(picker.text()).toContain('机构指标汇总');
  });

  it('filterable 搜索只保留匹配项，clearable 清空并发出空字符串', async () => {
    const wrapper = mountPicker({ modelValue: '77' });
    const picker = wrapper.get('[data-testid="slot-datasource"]');

    await picker.get('.el-select__wrapper').trigger('click');
    const input = picker.get('input[role="combobox"]');
    await input.setValue('时序');
    await vi.waitFor(() => expect(visibleOptions().map(option => option.textContent.trim())).toEqual(['机构时序明细']));

    await input.setValue('');
    await flushPromises();
    await picker.get('.el-select').trigger('mouseenter');
    await picker.get('.el-select__clear').trigger('click');
    await flushPromises();
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['']);
    expect(wrapper.emitted('change')?.at(-1)).toEqual(['']);
  });

  it('保留名称回退规则并把不兼容来源作为禁用选项显示', async () => {
    const wrapper = mountPicker();
    const picker = wrapper.get('[data-testid="slot-datasource"]');
    await picker.get('.el-select__wrapper').trigger('click');
    await flushPromises();

    const popper = document.body.querySelector('.panorama-datasource-picker__popper');
    const options = [...popper.querySelectorAll('.el-select-dropdown__item')];
    expect(options.map(option => option.textContent.trim())).toEqual([
      '机构指标汇总', '机构时序明细', 'DS_CODE_ONLY', '不兼容构成来源（当前双列模式不支持）', '数据源 #81'
    ]);
    expect(options.find(option => option.textContent.includes('不兼容构成来源')).classList.contains('is-disabled')).toBe(true);
  });

  it('disabled 时不打开下拉，也不触发选择事件', async () => {
    const wrapper = mountPicker({ disabled: true });
    const picker = wrapper.get('[data-testid="slot-datasource"]');
    expect(picker.get('.el-select__wrapper').classes()).toContain('is-disabled');

    await picker.get('.el-select__wrapper').trigger('click');
    await flushPromises();
    expect(picker.get('input[role="combobox"]').attributes('aria-expanded')).toBe('false');
    expect(wrapper.emitted('change')).toBeUndefined();
  });
});
