// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const DICT_LABELS = {
  INDUSTRY: { IT: '信息技术' },
  GROUP_TYPE: { SINGLE: '非集团客户' },
  CUSTOMER_TYPE: { CORP: '对公客户' },
  ENTERPRISE_TYPE: { PRIVATE: '民营' },
  LEAD_SOURCE: { SELF_FOUND: '自行挖掘' },
};

vi.mock('@/composables/useDict', () => ({
  useDict: vi.fn((dictType) => ({
    labelOf: (value) => DICT_LABELS[dictType]?.[value] || value || '-',
  })),
}));

import LeadDetailDrawer from '../LeadDetailDrawer.vue';

const passthrough = (name) => ({ name, template: '<div><slot /></div>' });
const stubs = {
  'el-drawer': passthrough('ElDrawer'),
  'el-skeleton': passthrough('ElSkeleton'),
  'el-alert': passthrough('ElAlert'),
  'el-descriptions': passthrough('ElDescriptions'),
  'el-descriptions-item': passthrough('ElDescriptionsItem'),
  'el-tag': passthrough('ElTag'),
  'el-empty': passthrough('ElEmpty'),
  LeadAttachmentPreview: {
    props: ['attachments'],
    template: '<div class="attachment-preview-stub">{{ attachments.map(item => item.fileName).join(",") }}</div>',
  },
};

let wrapper;
afterEach(() => {
  wrapper?.unmount();
  vi.clearAllMocks();
});

describe('LeadDetailDrawer 字典字段展示', () => {
  it('经营信息使用字典中文标签而不是英文编码', () => {
    wrapper = mount(LeadDetailDrawer, {
      props: {
        modelValue: true,
        lead: {
          id: 'LEAD-1',
          industry: 'IT',
          groupType: 'SINGLE',
          customerType: 'CORP',
          enterpriseType: 'PRIVATE',
          leadSource: 'SELF_FOUND',
        },
      },
      global: { stubs },
    });

    const text = wrapper.text();
    expect(text).toContain('信息技术');
    expect(text).toContain('非集团客户');
    expect(text).toContain('对公客户');
    expect(text).toContain('民营');
    expect(text).toContain('自行挖掘');
    expect(text).not.toContain('SELF_FOUND');
  });

  it('完整详情把线索附件交给统一预览组件', () => {
    wrapper = mount(LeadDetailDrawer, {
      props: {
        modelValue: true,
        lead: {
          id: 'LEAD-2',
          contactPerson: '张经理',
          contactMobile: '13800000000',
          attachments: [{ id: 'FILE-1', fileName: '经营场所.pdf' }],
        },
      },
      global: { stubs },
    });

    expect(wrapper.text()).toContain('张经理');
    expect(wrapper.text()).toContain('13800000000');
    expect(wrapper.get('.attachment-preview-stub').text()).toContain('经营场所.pdf');
  });
});
