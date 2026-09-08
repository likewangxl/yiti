// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';

import PanoramaInstitutionDirectory from '../PanoramaInstitutionDirectory.vue';

const institutions = [
  {
    orgCode: 'ORG-001',
    orgName: '西安营业部',
    cityCode: '610100',
    cityName: '西安市',
    located: true,
    lng: 108.94,
    lat: 34.34,
    locationSource: 'PROFILE',
    metrics: { deposit: 0, loan: null, customers: 8.2, rate: 0 },
    trend: [{ date: '2026-09', deposit: 0, loan: null }]
  },
  {
    orgCode: 'ORG-002',
    orgName: '榆林支行',
    cityCode: '610800',
    locationSource: 'MANUAL',
    located: true,
    lng: 109.73,
    lat: 38.28,
    metrics: { deposit: null, loan: 12.5, customers: null, rate: null },
    trend: []
  },
  {
    orgCode: 'ORG-003',
    orgName: '待维护机构',
    cityCode: '999999',
    locationSource: 'GEOCODE_VERIFIED',
    located: false,
    lng: null,
    lat: null,
    metrics: { deposit: 3.4, loan: null, customers: 0, rate: null },
    trend: [{ date: '2026-08', deposit: 3.4, loan: 0 }]
  },
  {
    orgCode: 'ORG-004',
    orgName: '空城市机构',
    cityCode: '',
    locationSource: 'OLD_IMPORT',
    located: true,
    lng: 109.1,
    lat: 34.3,
    metrics: { deposit: null, loan: null, customers: null, rate: null },
    trend: []
  },
  {
    orgCode: 'ORG-005',
    orgName: '咸阳支行',
    cityCode: '610400',
    locationSource: 'GEOCODE_VERIFIED',
    located: true,
    lng: 108.70,
    lat: 34.33,
    metrics: { deposit: 7.8 },
    trend: []
  },
  {
    orgCode: 'ORG-006',
    orgName: '旧来源未定位',
    cityCode: '610100',
    locationSource: 'MANUAL',
    located: false,
    lng: null,
    lat: null,
    metrics: { deposit: null },
    trend: []
  }
];

const model = { dataDate: '2026-09-07', institutions };
const mounted = [];

function mountDirectory(overrides = {}) {
  const wrapper = mount(PanoramaInstitutionDirectory, {
    props: { model, ...overrides },
    attachTo: document.body
  });
  mounted.push(wrapper);
  return wrapper;
}

describe('PanoramaInstitutionDirectory 机构目录', () => {
  beforeEach(() => {
    document.body.style.overflow = '';
  });

  afterEach(() => {
    mounted.splice(0).forEach(wrapper => wrapper.unmount());
    document.body.style.overflow = '';
  });

  it('只展示 model.institutions 中服务端传入的全部机构，并按编码或名称搜索', async () => {
    const wrapper = mountDirectory();
    expect(wrapper.findAll('[data-testid="institution-directory-row"]')).toHaveLength(6);
    await wrapper.get('[data-testid="institution-directory-search"]').setValue('ORG-003');
    expect(wrapper.findAll('[data-testid="institution-directory-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="institution-directory-row"]').text()).toContain('待维护机构');
    await wrapper.get('[data-testid="institution-directory-search"]').setValue('榆林');
    expect(wrapper.findAll('[data-testid="institution-directory-row"]')).toHaveLength(1);
    expect(wrapper.get('[data-testid="institution-directory-row"]').text()).toContain('ORG-002');
    expect(wrapper.get('[data-testid="institution-directory-row"]').text()).toContain('榆林市');
  });

  it('可筛选全部、待定位和城市待维护，缺 cityCode 或未知城市码统一提示未知', async () => {
    const wrapper = mountDirectory();
    await wrapper.get('[data-directory-filter="unlocated"]').trigger('click');
    expect(wrapper.findAll('[data-testid="institution-directory-row"]')).toHaveLength(2);
    await wrapper.get('[data-directory-filter="unknown-city"]').trigger('click');
    expect(wrapper.findAll('[data-testid="institution-directory-row"]')).toHaveLength(2);
    expect(wrapper.text()).toContain('未知城市');
    expect(wrapper.text()).toContain('城市待维护');
    expect(wrapper.text()).not.toContain('610800');
  });

  it('点击无坐标或未知城市机构仍发出 branch-select，并只展示原始已有指标、趋势和零空值区别', async () => {
    const wrapper = mountDirectory();
    await wrapper.get('[data-org-code="ORG-003"]').trigger('click');
    expect(wrapper.emitted('branch-select')).toEqual([['ORG-003']]);
    expect(wrapper.get('[data-testid="institution-directory-detail"]').text()).toContain('待维护机构');
    expect(wrapper.get('[data-testid="institution-directory-detail"]').text()).toContain('3.40');
    expect(wrapper.get('[data-testid="institution-directory-detail"]').text()).toContain('客户总量');
    expect(wrapper.get('[data-testid="institution-directory-detail"]').text()).toContain('0');
    expect(wrapper.get('[data-testid="institution-directory-detail"]').text()).toContain('—');
    expect(wrapper.get('[data-testid="institution-directory-detail"]').text()).toContain('2026-08');
    expect(wrapper.find('[data-testid="institution-directory-map"]').exists()).toBe(false);
  });

  it('Escape、遮罩关闭和焦点恢复只影响机构目录自己的滚动锁', async () => {
    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.textContent = '打开目录';
    document.body.appendChild(trigger);
    trigger.focus();
    const wrapper = mountDirectory();
    expect(document.body.style.overflow).toBe('hidden');
    expect(document.activeElement).toBe(wrapper.get('[data-testid="institution-directory-dialog"]').element);
    await wrapper.get('[data-testid="institution-directory-dialog"]').trigger('keydown.esc');
    expect(wrapper.emitted('close')).toHaveLength(1);
    wrapper.unmount();
    expect(document.body.style.overflow).toBe('');
    expect(document.activeElement).toBe(trigger);
    trigger.remove();
  });

  it('已定位机构按位置来源显示固定中文，未知来源提示待核对，未定位不得冒充旧来源', async () => {
    const wrapper = mountDirectory();

    await wrapper.get('[data-org-code="ORG-001"]').trigger('click');
    expect(wrapper.get('[data-testid="institution-directory-location-source"]').text()).toContain('机构画像');

    await wrapper.get('[data-org-code="ORG-002"]').trigger('click');
    expect(wrapper.get('[data-testid="institution-directory-location-source"]').text()).toContain('人工核定');

    await wrapper.get('[data-org-code="ORG-005"]').trigger('click');
    expect(wrapper.get('[data-testid="institution-directory-location-source"]').text()).toContain('地址解析核定');

    await wrapper.get('[data-org-code="ORG-004"]').trigger('click');
    expect(wrapper.get('[data-testid="institution-directory-location-source"]').text()).toContain('来源待核对');
    expect(wrapper.get('[data-testid="institution-directory-location-source"]').text()).not.toContain('机构画像');
    expect(wrapper.get('[data-testid="institution-directory-location-source"]').text()).not.toContain('人工核定');
    expect(wrapper.get('[data-testid="institution-directory-location-source"]').text()).not.toContain('地址解析核定');

    await wrapper.get('[data-org-code="ORG-006"]').trigger('click');
    const sourceText = wrapper.get('[data-testid="institution-directory-location-source"]').text();
    expect(sourceText).toContain('待定位');
    expect(sourceText).not.toContain('人工核定');
    expect(sourceText).not.toContain('地址解析核定');
    expect(sourceText).not.toContain('机构画像');
  });

  it('焦点停在 dialog 根节点时首次 Shift+Tab 回到最后可聚焦控件', async () => {
    const wrapper = mountDirectory();
    const dialog = wrapper.get('[data-testid="institution-directory-dialog"]');
    const focusable = wrapper.findAll('button, input, [tabindex="0"]');
    const lastFocusable = focusable[focusable.length - 1].element;
    dialog.element.focus();
    await dialog.trigger('keydown', { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(lastFocusable);
  });

  it('demoModel 明确标识演示机构，不把 fixture 误标为服务端授权目录', () => {
    const wrapper = mountDirectory({ model: { ...model, demo: true, demoOnly: true } });
    expect(wrapper.get('.institution-directory__kicker').text()).toContain('演示机构');
    expect(wrapper.get('.institution-directory__header p').text()).toContain('本地演示，非真实机构和业务数据');
    expect(wrapper.text()).not.toContain('当前大屏服务端已授权');
  });
});
