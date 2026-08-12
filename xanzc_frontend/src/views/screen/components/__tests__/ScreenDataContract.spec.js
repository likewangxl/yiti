// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const queryMock = vi.fn().mockResolvedValue({ columns: [], rows: [] });
vi.mock('@/api/screen', () => ({ queryScreenData: (...args) => queryMock(...args) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import BlockContainer from '../BlockContainer.vue';

describe('schemaVersion 2 区块取数', () => {
  beforeEach(() => { queryMock.mockClear(); });

  it('根据 screenCode + blockId 取数，不提交客户端 dsId/orgGroupCode/orgCodes', async () => {
    mount(BlockContainer, {
      props: {
        block: { id: 12, blockId: 12, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99, period: 'LATEST' }), styleJson: '{}', drillJson: '{}' },
        context: { screenCode: 'SCR_RETAIL', schemaVersion: 2, orgCode: 'O1', empId: '' }
      },
      global: { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } }
    });
    await vi.waitFor(() => expect(queryMock).toHaveBeenCalled());
    const body = queryMock.mock.calls[0][0];
    expect(body).toMatchObject({ schemaVersion: 2, screenCode: 'SCR_RETAIL', blockId: 12, period: 'LATEST', contextParams: { orgCode: 'O1', empId: null } });
    expect(body.dsId).toBeUndefined();
    expect(body.orgGroupCode).toBeUndefined();
    expect(body.orgCodes).toBeUndefined();
  });

  it('未知版本或不完整 schema2 身份时 Fail Close，不发出兼容 v1 请求', async () => {
    mount(BlockContainer, {
      props: {
        block: { id: 13, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99 }), styleJson: '{}', drillJson: '{}' },
        context: { schemaVersion: 3 }
      },
      global: { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } }
    });
    await Promise.resolve();
    expect(queryMock).not.toHaveBeenCalled();
  });

  it('运行时响应中的 schemaVersion/blockId 为字符串时 Fail Close，不能用 Number() 把 "2"/"123" 变成 v2 请求', async () => {
    const wrapper = mount(BlockContainer, {
      props: {
        block: { id: '123', componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99 }), styleJson: '{}', drillJson: '{}' },
        context: { screenCode: 'SCR_RETAIL', schemaVersion: '2' }
      },
      global: { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } }
    });
    await Promise.resolve();
    expect(queryMock).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('已拒绝取数');
  });

  it('当前可信发布快照缺失 RPT-43023 时提示需受控迁移或重新发布，不能自动退回 v1', async () => {
    queryMock.mockRejectedValueOnce({ code: 'RPT-43023', message: '当前发布包缺少可信绑定快照' });
    const wrapper = mount(BlockContainer, {
      props: {
        block: { id: 12, componentType: 'METRIC_CARD', bindJson: JSON.stringify({ dsId: 99 }), styleJson: '{}', drillJson: '{}' },
        context: { screenCode: 'SCR_RETAIL', schemaVersion: 2 }
      },
      global: { stubs: { 'el-icon': true, InfoFilled: true }, directives: { loading: {} } }
    });
    await vi.waitFor(() => expect(wrapper.text()).toContain('需受控迁移或重新发布'));
    expect(queryMock).toHaveBeenCalledTimes(1);
    expect(queryMock.mock.calls[0][0]).toMatchObject({ schemaVersion: 2, blockId: 12 });
  });
});
