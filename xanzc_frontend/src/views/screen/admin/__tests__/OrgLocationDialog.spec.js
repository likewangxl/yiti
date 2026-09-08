// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({
  getOrgLocationCapabilities: vi.fn(),
  getOrgLocation: vi.fn(),
  updateOrgLocation: vi.fn(),
  previewOrgLocationGeocode: vi.fn()
}));

vi.mock('@/api/orgLocation', () => api);
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}));

import OrgLocationDialog from '../OrgLocationDialog.vue';

const passthrough = { template: '<div><slot /></div>' };
const stubs = {
  'el-dialog': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<section v-if="modelValue" role="dialog"><slot /><slot name="footer" /></section>'
  },
  'el-form': passthrough,
  'el-form-item': passthrough,
  'el-input': {
    props: ['modelValue', 'disabled', 'readonly'],
    emits: ['update:modelValue'],
    template: '<input :value="modelValue" :disabled="disabled" :readonly="readonly" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-button': {
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-tag': passthrough,
  'el-alert': passthrough
};

const org = {
  orgCode: 'ORG_1',
  orgName: '西安机构',
  cityCode: '610100'
};

async function settle() {
  await flushPromises();
  await flushPromises();
}

function mountDialog(overrides = {}) {
  return mount(OrgLocationDialog, {
    props: { modelValue: true, org, ...overrides },
    global: { stubs }
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  api.getOrgLocationCapabilities.mockResolvedValue({
    storageEnabled: true,
    storageAvailable: true,
    geocodingEnabled: true,
    geocodingAvailable: true
  });
  api.getOrgLocation.mockResolvedValue({
    orgCode: 'ORG_1',
    address: '原始地址',
    cityCode: '610100',
    lng: 108.9,
    lat: 34.2,
    coordSys: 'GCJ02',
    addressSource: 'MANUAL',
    status: 'VERIFIED',
    locationSource: 'MANUAL',
    provider: 'MANUAL',
    matchLevel: 'MANUAL',
    version: 2
  });
  api.previewOrgLocationGeocode.mockResolvedValue([]);
  api.updateOrgLocation.mockResolvedValue({ orgCode: 'ORG_1', version: 3 });
});

describe('OrgLocationDialog.vue', () => {
  it('打开后先读能力，能力不可用时显示明确原因且不读取/保存空记录', async () => {
    api.getOrgLocationCapabilities.mockResolvedValueOnce({
      storageEnabled: false,
      storageAvailable: false,
      storageReason: '位置存储未启用',
      geocodingEnabled: false,
      geocodingAvailable: false,
      geocodingReason: '地址解析未启用'
    });
    const wrapper = mountDialog();
    await settle();

    expect(api.getOrgLocationCapabilities).toHaveBeenCalledTimes(1);
    expect(api.getOrgLocation).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('位置存储未启用');
    expect(wrapper.text()).toContain('地址解析未启用');
    expect(wrapper.text()).not.toContain('保存地址与定位');
  });

  it('能力响应必须包含明确布尔字段，格式异常时不能进入记录/保存态', async () => {
    api.getOrgLocationCapabilities.mockResolvedValueOnce({
      storageEnabled: true,
      storageAvailable: true,
      geocodingEnabled: true
    });
    const wrapper = mountDialog();
    await settle();

    expect(api.getOrgLocation).not.toHaveBeenCalled();
    expect(wrapper.vm.recordReady).toBe(false);
    expect(wrapper.vm.capabilityError).toContain('数据格式无效');
    expect(wrapper.find('[data-testid="location-save"]').exists()).toBe(false);
  });

  it.each([
    ['null', null],
    ['array', []],
    ['missing version', { orgCode: 'ORG_1', address: '地址' }],
    ['fractional version', { orgCode: 'ORG_1', address: '地址', version: 1.5 }],
    ['string version', { orgCode: 'ORG_1', address: '地址', version: '1' }],
    ['wrong org', { orgCode: 'OTHER', address: '地址', version: 1 }]
  ])('记录响应为%s时显示格式错误且不伪造空记录', async (_name, invalidRecord) => {
    api.getOrgLocation.mockResolvedValueOnce(invalidRecord);
    const wrapper = mountDialog();
    await settle();

    expect(wrapper.vm.recordReady).toBe(false);
    expect(wrapper.vm.recordError).toContain('数据格式无效');
    expect(wrapper.find('[data-testid="location-save"]').exists()).toBe(false);
  });

  it('城市只接受画像的有效六位编码，未知城市提示先维护画像', async () => {
    const wrapper = mountDialog({ org: { ...org, cityCode: '西安市' } });
    await settle();

    expect(wrapper.vm.cityCodeValid).toBe(false);
    expect(wrapper.text()).toContain('请先维护机构画像中的有效6位城市编码');
    expect(wrapper.find('[data-testid="location-save"]').attributes('disabled')).toBeDefined();
  });

  it('地址解析按钮只在能力、地址、城市和原因齐备时调用，粗糙候选不可确认', async () => {
    api.previewOrgLocationGeocode.mockResolvedValueOnce([
      {
        candidateToken: 'precise-token', address: '原始地址', formattedAddress: '精确门牌', cityCode: '610100',
        lng: 108.91, lat: 34.21, coordSys: 'GCJ02', provider: 'AMAP', matchLevel: '门牌号', verificationAllowed: true
      },
      {
        candidateToken: '', address: '原始地址', formattedAddress: '道路中心点', cityCode: '610100',
        lng: 108.92, lat: 34.22, coordSys: 'GCJ02', provider: 'AMAP', matchLevel: '道路', verificationAllowed: false,
        verificationReason: '匹配精度不足，不能确认 VERIFIED'
      }
    ]);
    const wrapper = mountDialog();
    await settle();

    wrapper.vm.form.address = '待解析地址';
    wrapper.vm.form.reason = '核对候选点';
    await wrapper.vm.$nextTick();
    await wrapper.find('[data-testid="geocode-preview"]').trigger('click');
    await settle();

    expect(api.previewOrgLocationGeocode).toHaveBeenCalledWith('ORG_1', {
      address: '待解析地址', cityCode: '610100', reason: '核对候选点'
    });
    expect(wrapper.findAll('[data-testid="candidate-confirm"]')).toHaveLength(1);
    expect(wrapper.text()).toContain('匹配精度不足，不能确认 VERIFIED');
  });

  it('地址解析返回非数组时显示格式错误而不是伪造空候选', async () => {
    api.previewOrgLocationGeocode.mockResolvedValueOnce({ candidateToken: 'invalid' });
    const wrapper = mountDialog();
    await settle();
    wrapper.vm.form.reason = '核对候选点';
    await wrapper.vm.$nextTick();
    await wrapper.find('[data-testid="geocode-preview"]').trigger('click');
    await settle();

    expect(wrapper.vm.previewError).toContain('数据格式无效');
    expect(wrapper.vm.candidates).toEqual([]);
    expect(wrapper.vm.previewing).toBe(false);
  });

  it('手工坐标保存携带 GCJ02、确认、版本和原因，不发送服务端字段', async () => {
    const wrapper = mountDialog();
    await settle();

    wrapper.vm.form.address = '新地址';
    wrapper.vm.form.lng = 109.01;
    wrapper.vm.form.lat = 34.25;
    wrapper.vm.form.reason = '人工核实坐标';
    await wrapper.vm.$nextTick();
    wrapper.vm.form.manualConfirmed = true;
    await wrapper.find('[data-testid="location-save"]').trigger('click');
    await settle();

    expect(api.updateOrgLocation).toHaveBeenCalledWith('ORG_1', expect.objectContaining({
      address: '新地址', cityCode: '610100', lng: 109.01, lat: 34.25,
      coordSys: 'GCJ02', manualConfirmed: true, version: 2, reason: '人工核实坐标'
    }));
    const body = api.updateOrgLocation.mock.calls[0][1];
    expect(body).not.toHaveProperty('addressSource');
    expect(body).not.toHaveProperty('provider');
    expect(body).not.toHaveProperty('status');
    expect(body).not.toHaveProperty('locationSource');
  });

  it('地址变更保存自动显式清除旧定位，且 record cityCode 作为旧值参与变化判断', async () => {
    api.getOrgLocation.mockResolvedValueOnce({
      orgCode: 'ORG_1', address: '原始地址', cityCode: '610101', lng: 108.9, lat: 34.2,
      coordSys: 'GCJ02', status: 'VERIFIED', locationSource: 'MANUAL', version: 2
    });
    const wrapper = mountDialog();
    await settle();

    expect(wrapper.vm.form.cityCode).toBe('610100');
    expect(wrapper.vm.addressChanged).toBe(true);
    expect(wrapper.text()).toContain('地址或城市变更将使旧坐标失效，需重新确认');
    wrapper.vm.form.address = '新地址';
    wrapper.vm.form.reason = '地址变更';
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.buildUpdatePayload()).toMatchObject({ clearLocation: true });
    await wrapper.find('[data-testid="location-save"]').trigger('click');
    await settle();

    expect(api.updateOrgLocation).toHaveBeenCalledWith('ORG_1', expect.objectContaining({ clearLocation: true }));
  });

  it('选择精确候选后只发送内存中的 candidateToken，不把候选展示地址改写成待绑定地址', async () => {
    api.previewOrgLocationGeocode.mockResolvedValueOnce([{
      candidateToken: 'precise-token', address: '原始地址', formattedAddress: '精确门牌', cityCode: '610100',
      lng: 108.91, lat: 34.21, coordSys: 'GCJ02', provider: 'AMAP', matchLevel: '门牌号', verificationAllowed: true
    }]);
    const wrapper = mountDialog();
    await settle();
    wrapper.vm.form.reason = '核对候选点';
    await wrapper.vm.$nextTick();
    await wrapper.find('[data-testid="geocode-preview"]').trigger('click');
    await settle();
    await wrapper.find('[data-testid="candidate-confirm"]').trigger('click');
    await wrapper.vm.$nextTick();
    await wrapper.find('[data-testid="location-save"]').trigger('click');
    await settle();

    expect(wrapper.vm.form.address).toBe('原始地址');
    expect(api.updateOrgLocation).toHaveBeenCalledWith('ORG_1', {
      address: '原始地址', cityCode: '610100', candidateToken: 'precise-token', version: 2, reason: '核对候选点'
    });
  });

  it('地址变更自动清旧坐标，409 只刷新版本不覆盖当前表单', async () => {
    const wrapper = mountDialog();
    await settle();
    wrapper.vm.form.address = '改过地址';
    wrapper.vm.form.reason = '地址变更';
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('地址或城市变更将使旧坐标失效，需重新确认');

    api.updateOrgLocation.mockRejectedValueOnce(Object.assign(new Error('conflict'), { response: { status: 409 } }));
    api.getOrgLocation.mockResolvedValueOnce({ orgCode: 'ORG_1', address: '服务端地址', cityCode: '610100', version: 4 });
    await wrapper.find('[data-testid="location-save"]').trigger('click');
    await settle();
    expect(api.updateOrgLocation).toHaveBeenCalledWith('ORG_1', expect.objectContaining({ clearLocation: true, version: 2 }));
    expect(api.getOrgLocation).toHaveBeenCalledTimes(2);
    expect(wrapper.vm.form.version).toBe(2);
    expect(wrapper.text()).toContain('版本冲突');
  });

  it('地址变化会使进行中的旧地址解析失效，旧响应及 finally 不能覆盖新请求', async () => {
    let resolveFirst;
    let resolveSecond;
    api.previewOrgLocationGeocode
      .mockReturnValueOnce(new Promise(resolve => { resolveFirst = resolve; }))
      .mockReturnValueOnce(new Promise(resolve => { resolveSecond = resolve; }));
    const wrapper = mountDialog();
    await settle();
    wrapper.vm.form.reason = '核对候选点';
    await wrapper.vm.$nextTick();

    const firstRequest = wrapper.vm.previewCandidates();
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.previewing).toBe(true);
    wrapper.vm.form.address = '新地址';
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.previewing).toBe(false);
    expect(wrapper.vm.candidates).toEqual([]);

    const secondRequest = wrapper.vm.previewCandidates();
    await wrapper.vm.$nextTick();
    expect(wrapper.vm.previewing).toBe(true);
    resolveFirst([{ formattedAddress: '旧地址候选', lng: 108.91, lat: 34.21, coordSys: 'GCJ02', verificationAllowed: true, candidateToken: 'old' }]);
    await settle();
    expect(wrapper.vm.previewing).toBe(true);
    expect(wrapper.vm.candidates).toEqual([]);

    resolveSecond([{ formattedAddress: '新地址候选', lng: 108.92, lat: 34.22, coordSys: 'GCJ02', verificationAllowed: true, candidateToken: 'new' }]);
    await Promise.all([firstRequest, secondRequest]);
    await settle();
    expect(wrapper.vm.previewing).toBe(false);
    expect(wrapper.vm.candidates[0].formattedAddress).toBe('新地址候选');
  });

  it('保存响应格式非法时保留表单，不发送 saved 事件或关闭对话框', async () => {
    api.updateOrgLocation.mockResolvedValueOnce(null);
    const wrapper = mountDialog();
    await settle();
    wrapper.vm.form.reason = '保存地址';
    await wrapper.vm.$nextTick();
    await wrapper.find('[data-testid="location-save"]').trigger('click');
    await settle();

    expect(api.updateOrgLocation).toHaveBeenCalledTimes(1);
    expect(wrapper.vm.saveError).toContain('数据格式无效');
    expect(wrapper.emitted('saved')).toBeUndefined();
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
  });

  it('从关闭且无机构状态同 tick 打开时只读取一次能力', async () => {
    const wrapper = mount(OrgLocationDialog, {
      props: { modelValue: false, org: null },
      global: { stubs }
    });

    await wrapper.setProps({ modelValue: true, org });
    await settle();

    expect(api.getOrgLocationCapabilities).toHaveBeenCalledTimes(1);
    expect(api.getOrgLocation).toHaveBeenCalledTimes(1);
  });

  it('能力读取拒绝时不读取记录、不保存或预览', async () => {
    api.getOrgLocationCapabilities.mockRejectedValueOnce(new Error('能力服务不可用'));
    const wrapper = mount(OrgLocationDialog, {
      props: { modelValue: false, org: null },
      global: { stubs }
    });

    await wrapper.setProps({ modelValue: true, org });
    await settle();
    await wrapper.vm.save();
    await wrapper.vm.previewCandidates();

    expect(api.getOrgLocationCapabilities).toHaveBeenCalledTimes(1);
    expect(api.getOrgLocation).not.toHaveBeenCalled();
    expect(api.updateOrgLocation).not.toHaveBeenCalled();
    expect(api.previewOrgLocationGeocode).not.toHaveBeenCalled();
  });

  it('关闭后迟到的能力/记录响应不更新当前表单', async () => {
    let resolveCapabilities;
    api.getOrgLocationCapabilities.mockReturnValueOnce(new Promise(resolve => { resolveCapabilities = resolve; }));
    const wrapper = mountDialog();
    await wrapper.setProps({ modelValue: false });
    await wrapper.vm.$nextTick();
    resolveCapabilities({ storageEnabled: true, storageAvailable: true });
    await settle();

    expect(api.getOrgLocation).not.toHaveBeenCalled();
    expect(wrapper.vm.capabilities).toBe(null);
  });
});
