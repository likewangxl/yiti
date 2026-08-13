// @vitest-environment happy-dom
import { afterAll, afterEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import ElementPlus from 'element-plus';
import '../index.scss';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const previousResizeObserver = globalThis.ResizeObserver;
globalThis.ResizeObserver = ResizeObserverStub;

const OperationTable = defineComponent({
  template: `
    <main class="bp-crud">
      <el-table :data="[{ name: '普通长文本', action: '编辑' }]">
        <el-table-column prop="name" label="名称" width="80" />
        <el-table-column label="操作" class-name="operation-cell" fixed="right" width="100">
          <template #default><el-button link>编辑</el-button></template>
        </el-table-column>
      </el-table>
    </main>`
});

let wrapper;

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
});

describe('操作列真实 DOM 基线', () => {
  it('Element Plus 将 class-name 落在固定右侧单元格，操作单元格覆盖省略规则而普通文本仍省略', async () => {
    wrapper = mount(OperationTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const operationCell = wrapper.find('td.operation-cell .cell');
    const ordinaryCell = wrapper.find('td:not(.operation-cell) .cell');

    expect(operationCell.exists()).toBe(true);
    expect(operationCell.element.matches('.operation-cell .cell')).toBe(true);
    expect(operationCell.element.closest('td')?.classList.contains('el-table-fixed-column--right')).toBe(true);
    expect(ordinaryCell.element.matches('.operation-cell .cell')).toBe(false);
  });
});

afterAll(() => {
  globalThis.ResizeObserver = previousResizeObserver;
});
