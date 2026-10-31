// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';

const api = vi.hoisted(() => ({ saveScreenCanvas: vi.fn(), getScreenCanvas: vi.fn(), publishScreenCanvas: vi.fn(), discardScreenCanvas: vi.fn() }));
vi.mock('@/api/screen', () => api);
import LegacyCanvasWorkbench from '../LegacyCanvasWorkbench.vue';

const canvas = { screenId: 9102, canvasVersion: 4, canvasStyle: { designWidth: 1920, designHeight: 1080, background: '#03132b', keep: { x: 1 } }, components: [
  { id: 'chart', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 17, style: { top: 100, left: 120, width: 300, height: 100 }, propValue: { keep: true }, bindJson: '{}', styleJson: '{}', drillJson: '{}', unknown: 'keep' },
  { id: 'hidden', component: 'TextLabel', isShow: false, style: { top: 2 }, children: [{ id: 'clock', component: 'ClockWidget', style: { top: 3 }, propValue: { format: 'HH:mm' } }] }
], blocks: [{ id: 17, componentType: 'METRIC_CARD', bindJson: '{"dsId":8,"period":"LATEST","items":[{"col":"amount","label":"金额"}]}', styleJson: '{"title":"真实标题"}', drillJson: '{}' }] };

describe('LegacyCanvasWorkbench', () => {
  it('排名已使用categoryCol和items时，不允许编辑不起作用的兼容列', () => {
    const rank={...canvas,components:[{...canvas.components[0],innerType:'RANK_LIST',bindJson:JSON.stringify({dsId:8,categoryCol:'org_code',nameCol:'org_name',valueCol:'old',items:[{col:'amount',label:'金额'}]})}]};
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas:rank}});
    const labels=wrapper.findAll('label');
    expect(labels.find(label=>label.text().includes('名称列')).get('input').element.disabled).toBe(true);
    expect(labels.find(label=>label.text().includes('数值列')).get('input').element.disabled).toBe(true);wrapper.unmount();
  });
  beforeEach(() => { api.saveScreenCanvas.mockReset().mockImplementation(async payload => { api.lastPayload = payload; return {canvasVersion:payload.expectedVersion+1,canvasDraftJson:JSON.stringify({components:payload.components})}; }); api.getScreenCanvas.mockReset().mockImplementation(async () => ({ canvasVersion: api.lastPayload?.expectedVersion + 1, canvasStyleJson: JSON.stringify(api.lastPayload?.canvasStyle || {}), canvasDraftJson: JSON.stringify({ components: api.lastPayload?.components || [] }) })); });

  it('保存后独立回读发现同 ID 内容变化时保留完整草稿并不触发 saved', async () => {
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas}});
    wrapper.vm.draft.components[0].styleJson='{"title":"用户标题"}';
    api.getScreenCanvas.mockResolvedValueOnce({ canvasVersion: 5, canvasStyleJson: JSON.stringify(canvas.canvasStyle), canvasDraftJson: JSON.stringify({ components: [{ ...canvas.components[0], styleJson: '{"title":"后台旧标题"}' }, canvas.components[1]] }) });
    expect(await wrapper.vm.save()).toBe(false);
    expect(wrapper.vm.draft.components[0].styleJson).toContain('用户标题');
    expect(wrapper.emitted('saved')).toBeUndefined();
  });

  it('保存后回读版本、样式和组件内容一致时接受后台归一化并清除 dirty', async () => {
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas}});
    wrapper.vm.draft.components[0].styleJson='{"title":"归一化标题"}';
    api.getScreenCanvas.mockImplementationOnce(async () => ({ canvasVersion: 5, canvasStyleJson: JSON.stringify({ keep: { x: 1 }, background: '#03132b', designHeight: 1080, designWidth: 1920 }), canvasDraftJson: JSON.stringify({ components: [{ ...api.lastPayload.components[0], styleJson: '{"title":"归一化标题"}' }, api.lastPayload.components[1]] }) }));
    expect(await wrapper.vm.save()).toBe(true);
    expect(wrapper.vm.draft.components[0].styleJson).toBe('{"title":"归一化标题"}');
    expect(wrapper.emitted('saved')).toHaveLength(1);
    expect(wrapper.get('[data-testid="legacy-canvas-cancel"]').element.disabled).toBe(true);
  });

  it('保存回包同 ID 但内容不同则拒绝，不等待回读覆盖错误', async () => {
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas}});
    wrapper.vm.draft.components[0].styleJson='{"title":"用户标题"}';
    api.saveScreenCanvas.mockResolvedValueOnce({ canvasVersion: 5, canvasDraftJson: JSON.stringify({ components: [{ ...canvas.components[0], styleJson: '{"title":"后台旧标题"}' }, canvas.components[1]] }) });
    expect(await wrapper.vm.save()).toBe(false);
    expect(api.getScreenCanvas).not.toHaveBeenCalled();
    expect(wrapper.vm.draft.components[0].styleJson).toContain('用户标题');
  });

  it('回读失败时保留 dirty 草稿', async () => {
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas}});
    wrapper.vm.draft.components[0].styleJson='{"title":"回读失败仍保留"}';
    api.getScreenCanvas.mockRejectedValueOnce(new Error('readback unavailable'));
    expect(await wrapper.vm.save()).toBe(false);
    expect(wrapper.vm.draft.components[0].styleJson).toContain('回读失败仍保留');
    expect(wrapper.emitted('saved')).toBeUndefined();
  });

  it('提供不含 Group 的组件选择器，选择窄屏时钟会聚焦编辑区', async () => {
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas}});
    const editor = wrapper.find('.legacy-canvas-workbench__editor').element;
    editor.scrollIntoView = vi.fn();
    const picker = wrapper.get('[data-testid="legacy-node-picker"]');
    expect(picker.findAll('option').map(option => option.element.value)).toEqual(['chart', 'hidden', 'clock']);
    expect(picker.findAll('option').map(option => option.text())).not.toContain(expect.stringContaining('Group'));
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    await picker.setValue('clock');
    await vi.waitFor(() => expect(editor.scrollIntoView).toHaveBeenCalled());
    expect(wrapper.vm.draft.components[1].children[0].id).toBe('clock');
  });

  it('Group 只作为选择器分组，不渲染为业务按钮；隐藏节点有明确分组', () => {
    const grouped = { ...canvas, components: [{ id: 'group', component: 'Group', children: [{ id: 'child', component: 'ClockWidget', isShow: false }] }] };
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas:grouped}});
    expect(wrapper.find('[data-node-id="group"]').exists()).toBe(false);
    expect(wrapper.find('[data-hidden-node-id="child"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="legacy-node-picker"]').find('optgroup[label="隐藏组件"]').exists()).toBe(true);
  });
  it.each([null, {}, {canvasVersion:4}, {canvasVersion:5,canvasDraftJson:'broken'}, {canvasVersion:5,canvasDraftJson:'{"components":[]}'}])('无法确认保存响应时保留全部编辑：%j', async response => {
    const wrapper=mount(LegacyCanvasWorkbench,{props:{screen:{id:9102},canvas}});
    wrapper.vm.draft.components[0].styleJson='{"title":"仍需保存"}';
    api.saveScreenCanvas.mockResolvedValueOnce(response);
    expect(await wrapper.vm.save()).toBe(false);
    expect(wrapper.get('[data-testid="legacy-canvas-cancel"]').element.disabled).toBe(false);
    expect(wrapper.vm.draft.components[0].styleJson).toContain('仍需保存');
    expect(wrapper.emitted('saved')).toBeUndefined();
    wrapper.unmount();
  });
  it('可见/隐藏节点完整呈现，标题来自 block，修改后保存完整旧组件树', async () => {
    const wrapper = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas, datasources: [{ id: 8, dsName: '余额来源', configJson: JSON.stringify({ fieldMeta: [{ col: 'amount', alias: '金额', unit: 'YUAN' }] }) }] } });
    expect(wrapper.find('[data-node-id="chart"]').text()).toContain('真实标题');
    expect(wrapper.find('[data-hidden-node-id="hidden"]').exists()).toBe(true);
    await wrapper.get('[data-node-id="chart"]').trigger('click');
    await wrapper.findAll('select')[1].setValue('8');
    expect(wrapper.emitted('dirty-change')?.at(-1)?.[0]).toBe(true);
    await wrapper.get('[data-testid="legacy-canvas-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalled());
    const payload = api.saveScreenCanvas.mock.calls.at(-1)[0];
    expect(payload.components).toHaveLength(2);
    expect(payload.components[0].unknown).toBe('keep');
    expect(payload.components[1].children[0].propValue.format).toBe('HH:mm');
    expect(payload.components[0].bindJson).toContain('8');
    expect(payload.components[0].bindJson).not.toContain('"fields"');
    expect(payload.components[0].bindJson).not.toContain('"units"');
  });

  it('ChartWidget 缺 block 时 fail-close，不能保存', async () => {
    const broken = { ...canvas, components: [{ ...canvas.components[0], blockId: 999 }] };
    const wrapper = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: broken } });
    await wrapper.get('[data-node-id="chart"]').trigger('click');
    expect(wrapper.text()).toContain('缺少对应区块');
    expect(wrapper.get('[data-testid="legacy-canvas-save"]').attributes('disabled')).toBeDefined();
  });

  it.each([
    ['METRIC_CARD', { items: [{ col: 'amount', label: '金额' }] }], ['LINE_TREND', { items: [{ col: 'amount', label: '金额' }] }],
    ['AREA_STACK', { items: [{ col: 'amount', label: '金额' }] }], ['BAR_COMPARE', { categoryCol: 'name', items: [{ col: 'amount', label: '金额' }] }],
    ['TABLE_LIST', { items: [{ col: 'name', label: '名称' }] }], ['RANK_LIST', { nameCol: 'name', items: [{ col: 'amount', label: '金额' }] }],
    ['GAUGE', { valueCol: 'amount' }], ['LIQUID_PROGRESS', { valueCol: 'amount' }], ['KPI_RADAR', { propValue: { valueField: 'rate' } }]
  ])('innerType %s 只保留真实合同键，不生成 fields/units', (innerType, contract) => {
    const bind = { dsId: 8, period: 'LATEST', ...(contract.propValue ? {} : contract) };
    const component = { ...canvas.components[0], innerType, bindJson: JSON.stringify(bind), propValue: contract.propValue || {} };
    const wrapper = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: { ...canvas, components: [component], blocks: [{ ...canvas.blocks[0], componentType: innerType, bindJson: JSON.stringify(bind) }] } } });
    const payload = wrapper.vm.savePayload();
    const savedBind = JSON.parse(payload.components[0].bindJson);
    expect(savedBind.fields).toBeUndefined();
    expect(savedBind.units).toBeUndefined();
    expect(savedBind.dsId).toBe(8);
    wrapper.unmount();
  });

  it('常用旧组件属性实际落在 propValue，数值列入口与 items 增删排序可见', async () => {
    const make = (innerType, propValue = {}, bind = { dsId: 8, period: 'LATEST', items: [] }) => ({ ...canvas, components: [{ ...canvas.components[0], innerType, propValue, bindJson: JSON.stringify(bind) }], blocks: [{ ...canvas.blocks[0], componentType: innerType, bindJson: JSON.stringify(bind) }] });
    const gauge = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: make('GAUGE', {}, { dsId: 8, period: 'LATEST', valueCol: 'amount' }) } });
    await gauge.get('[data-node-id="chart"]').trigger('click');
    expect(gauge.find('[data-testid="legacy-value-col"]').exists()).toBe(true);
    gauge.unmount();
    const titleCanvas = make('TitleBar', { text: '标题', fontSize: 30, color: '#fff', align: 'left', preset: 'glow' }); titleCanvas.components[0].component = 'TitleBar';
    const title = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: titleCanvas } });
    await title.get('[data-node-id="chart"]').trigger('click');
    await title.get('[data-testid="legacy-title-text"]').setValue('新标题');
    expect(JSON.parse(title.vm.draft.components[0].propValue ? JSON.stringify(title.vm.draft.components[0].propValue) : '{}').text).toBe('新标题');
    expect(JSON.parse(title.vm.draft.components[0].styleJson || '{}').title).toBeUndefined();
    title.unmount();
    const empty = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: make('METRIC_CARD', {}, { dsId: 8, period: 'LATEST', items: [] }) } });
    await empty.get('[data-node-id="chart"]').trigger('click');
    expect(empty.find('[data-testid="legacy-items-all-columns"]').exists()).toBe(true);
    expect(empty.find('[data-testid="legacy-item-add"]').exists()).toBe(true);
    empty.unmount();
  });

  it('Group 子节点叠加父坐标，隐藏父节点的 children 不进入可见画布', () => {
    const grouped = { ...canvas, components: [
      { id: 'group', component: 'Group', style: { left: 100, top: 40 }, children: [{ id: 'child-visible', component: 'TextLabel', style: { left: 20, top: 10, width: 100, height: 30 }, propValue: { text: '子节点' } }] },
      { id: 'hidden-group', component: 'Group', isShow: false, style: { left: 500, top: 500 }, children: [{ id: 'hidden-child', component: 'TextLabel', style: { left: 1, top: 1 } }] }
    ], blocks: [] };
    const wrapper = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: grouped } });
    expect(wrapper.find('[data-node-id="child-visible"]').exists()).toBe(true);
    expect(wrapper.find('[data-node-id="child-visible"]').attributes('style')).toContain('left: 6.25%');
    expect(wrapper.find('[data-node-id="hidden-child"]').exists()).toBe(false);
  });

  it('block-only drill 修改开关/目标后保留原 jump.params 与 drillPeriods', async () => {
    const drillCanvas = { ...canvas, components: [{ ...canvas.components[0], drillJson: '{}' }], blocks: [{ ...canvas.blocks[0], drillJson: '{"drillEnabled":false,"drillPeriods":["LAST_10D"],"jump":{"targetScreenCode":"OLD","params":{"orgCode":"$col:org_code"}}}' }] };
    const wrapper = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: drillCanvas } });
    await wrapper.get('[data-node-id="chart"]').trigger('click');
    await wrapper.get('[data-testid="legacy-drill-enabled"]').setValue(true);
    await wrapper.get('[data-testid="legacy-drill-target"]').setValue('NEW');
    const drill = JSON.parse(wrapper.vm.draft.components[0].drillJson);
    expect(drill.drillPeriods).toEqual(['LAST_10D']);
    expect(drill.jump.params.orgCode).toBe('$col:org_code');
    expect(drill.jump.targetScreenCode).toBe('NEW');
  });

  it('RANK_LIST 优先编辑 categoryCol，旧 nameCol 仍回显；固定类型和 drill 强制 TIMESERIES 来源', async () => {
    const rank = { ...canvas, components: [{ ...canvas.components[0], innerType: 'RANK_LIST', bindJson: '{"dsId":8,"period":"LATEST","categoryCol":"org_code","nameCol":"org_name","valueCol":"amount"}' }], blocks: [{ ...canvas.blocks[0], componentType: 'RANK_LIST', bindJson: '{"dsId":8,"period":"LATEST","categoryCol":"org_code","nameCol":"org_name","valueCol":"amount"}' }] };
    const sources = [{ id: 8, dsType: 'SINGLE', sourceKind: 'WIDE_TABLE', configJson: '{}' }, { id: 9, dsType: 'TIMESERIES', sourceKind: 'WIDE_TABLE', configJson: '{}' }];
    const wrapper = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: rank, datasources: sources } });
    await wrapper.get('[data-node-id="chart"]').trigger('click');
    expect(wrapper.find('[data-testid="legacy-category-col"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="legacy-category-col"]').element.value).toBe('org_code');
    await wrapper.setProps({ canvas: { ...rank, components: [{ ...rank.components[0], innerType: 'KPI_RADAR', bindJson: '{"dsId":8,"period":"LATEST"}' }], blocks: [{ ...rank.blocks[0], componentType: 'KPI_RADAR', bindJson: '{"dsId":8,"period":"LATEST"}' }] } });
    await wrapper.get('[data-node-id="chart"]').trigger('click');
    expect(wrapper.find('[data-testid="legacy-source-8"]').attributes('disabled')).toBeDefined();
    expect(wrapper.find('[data-testid="legacy-source-9"]').exists()).toBe(false);
    await wrapper.setProps({ datasources: [...sources, { id: 10, dsType: 'SINGLE', sourceKind: 'KPI_DETAIL', configJson: '{}' }] });
    expect(wrapper.find('[data-testid="legacy-source-10"]').exists()).toBe(true);
  });

  it('block-only drill 回显完整状态，保存空响应/不递增版本时保留 dirty 与基线', async () => {
    const drillCanvas = { ...canvas, components: [{ ...canvas.components[0], drillJson: '{}' }], blocks: [{ ...canvas.blocks[0], drillJson: '{"drillEnabled":true,"drillPeriods":["LAST_10D"],"jump":{"targetScreenCode":"OLD","params":{"orgCode":"$col:org_code"}}}' }] };
    api.saveScreenCanvas.mockResolvedValueOnce({ canvasVersion: 4, canvasDraftJson: JSON.stringify({ components: drillCanvas.components }) });
    const wrapper = mount(LegacyCanvasWorkbench, { props: { screen: { id: 9102 }, canvas: drillCanvas } });
    await wrapper.get('[data-node-id="chart"]').trigger('click');
    expect(wrapper.get('[data-testid="legacy-drill-enabled"]').element.checked).toBe(true);
    expect(wrapper.get('[data-testid="legacy-drill-target"]').element.value).toBe('OLD');
    wrapper.vm.draft.components[0].styleJson = '{"title":"不会保存"}';
    await wrapper.vm.$nextTick();
    await wrapper.get('[data-testid="legacy-canvas-save"]').trigger('click');
    await vi.waitFor(() => expect(api.saveScreenCanvas).toHaveBeenCalled());
    expect(wrapper.find('[data-testid="legacy-cancel"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="legacy-canvas-cancel"]').attributes('disabled')).toBeUndefined();
  });
});
