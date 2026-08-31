// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import MapAttr from '../map-center/Attr.vue';

const SelectStub = {
  name: 'ElSelect', props: ['modelValue'], emits: ['update:modelValue', 'change'],
  methods: {
    update(event) {
      this.$emit('update:modelValue', event.target.value);
      this.$emit('change', event.target.value);
    }
  },
  template: '<select :value="modelValue" @change="update"><slot /></select>'
};

const stubs = {
  CommonAttr: { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': SelectStub,
  'el-option-group': { template: '<optgroup><slot /></optgroup>' },
  'el-option': { props: ['value', 'label'], template: '<option :value="value">{{ label }}</option>' },
  'el-input': true
};

describe('MapCenter 地域选择属性面板', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('以下拉菜单列出陕西全省和十个地市，并把宝鸡选择写回画布配置', async () => {
    const element = { propValue: { schemaVersion: 1, mode: 'SHAANXI_LEGACY', regionCode: '610000' }, style: {} };
    const wrapper = mount(MapAttr, { props: { element }, global: { stubs } });
    const select = wrapper.find('select');
    expect(select.exists()).toBe(true);
    expect(select.findAll('option').map(option => option.text())).toEqual([
      '陕西省（全省概览）', '西安市（经营六区）', '铜川市', '宝鸡市', '咸阳市', '渭南市',
      '延安市', '汉中市', '榆林市', '安康市', '商洛市'
    ]);

    await select.setValue('610300');
    expect(element.propValue).toMatchObject({
      schemaVersion: 1, mode: 'SHAANXI_LEGACY', baseRegion: 'CITY_DISTRICT', regionCode: '610300'
    });
  });

  it('选择西安时继续使用既有复合经营地图业务契约', async () => {
    const element = { propValue: { schemaVersion: 1, mode: 'SHAANXI_LEGACY' }, style: {} };
    const wrapper = mount(MapAttr, { props: { element }, global: { stubs } });
    await wrapper.find('select').setValue('610100');
    expect(element.propValue).toMatchObject({
      schemaVersion: 2, mode: 'XIAN_COMPOSITE', baseRegion: 'XIAN_OUTLINE',
      localSelector: { cityCode: '610100', operatingLevel: 'PRIMARY' }
    });
  });
});
