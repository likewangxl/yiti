// @vitest-environment happy-dom
import { afterAll, afterEach, beforeEach, describe, expect, it } from 'vitest';
import { compile } from 'sass';
import { resolve } from 'node:path';
import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';

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
let crudBaselineStyle;

beforeEach(() => {
  // Element Plus 官方样式先由上方真实入口加载，项目 SCSS 后注入。
  // 断言通过 DOM 的 getComputedStyle 验证两者的实际层叠结果，而非只匹配源码文本。
  crudBaselineStyle = document.createElement('style');
  crudBaselineStyle.textContent = compile(resolve(process.cwd(), 'src/styles/index.scss')).css;
  document.head.append(crudBaselineStyle);
});

afterEach(() => {
  wrapper?.unmount();
  wrapper = undefined;
  crudBaselineStyle?.remove();
  crudBaselineStyle = undefined;
});

describe('操作列真实 DOM 基线', () => {
  it('Element Plus 将 class-name 落在固定右侧单元格，且真实计算样式覆盖默认单元格规则', async () => {
    wrapper = mount(OperationTable, { attachTo: document.body, global: { plugins: [ElementPlus] } });
    await nextTick();
    await nextTick();

    const operationCell = wrapper.find('td.operation-cell .cell');
    const ordinaryCell = wrapper.find('td:not(.operation-cell) .cell');

    expect(operationCell.exists()).toBe(true);
    expect(operationCell.element.matches('.operation-cell .cell')).toBe(true);
    expect(operationCell.element.closest('td')?.classList.contains('el-table-fixed-column--right')).toBe(true);
    expect(ordinaryCell.element.matches('.operation-cell .cell')).toBe(false);

    const ordinaryStyle = getComputedStyle(ordinaryCell.element);
    expect(ordinaryStyle.overflow).toBe('hidden');
    expect(ordinaryStyle.textOverflow).toBe('ellipsis');
    expect(ordinaryStyle.whiteSpace).toBe('nowrap');

    const operationStyle = getComputedStyle(operationCell.element);
    expect(operationStyle.overflow).toBe('visible');
    expect(operationStyle.textOverflow).toBe('clip');
    expect(operationStyle.whiteSpace).toBe('nowrap');
  });
});

afterAll(() => {
  globalThis.ResizeObserver = previousResizeObserver;
});
