#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const projectRoot = process.cwd();
const playwrightCli = path.resolve(projectRoot, 'node_modules/.bin/playwright-cli');
const session = process.env.CORPORATE_PLAYWRIGHT_SESSION || 'corporate-content-check';
const pageUrl = process.env.CORPORATE_CONTENT_URL || 'http://127.0.0.1:8092/#/screen-preview/corporate';
const qaDirValue = process.env.SCREEN_QA_DIR === 'off'
  ? ''
  : (process.env.SCREEN_QA_DIR || '/tmp/yiti-corporate-qa/browser');
const qaDir = qaDirValue ? path.resolve(qaDirValue) : '';
const viewports = [
  { width: 1783, height: 909 },
  { width: 1920, height: 1080 },
  { width: 1366, height: 768 },
  { width: 1280, height: 800 },
  { width: 390, height: 844 }
];

const commandRecords = [];

function recordCommand(args, result) {
  const record = {
    at: new Date().toISOString(),
    command: [playwrightCli, ...args],
    exitCode: result.status,
    signal: result.signal || null,
    stdout: result.stdout || '',
    stderr: result.stderr || ''
  };
  commandRecords.push(record);
  return record;
}

function runCli(args, { allowFailure = false } = {}) {
  const cliArgs = [`-s=${session}`, '--json', ...args];
  const result = spawnSync(playwrightCli, cliArgs, {
    cwd: projectRoot,
    encoding: 'utf8',
    maxBuffer: 50 * 1024 * 1024
  });
  const record = recordCommand(cliArgs, result);
  if (result.error) {
    record.error = result.error.message;
    if (!allowFailure) throw new Error(`playwright-cli 启动失败：${result.error.message}`);
    return { record, value: null };
  }
  if (result.status !== 0 && !allowFailure) {
    throw new Error(`playwright-cli 退出码 ${result.status}：${result.stderr || result.stdout}`);
  }
  const output = String(result.stdout || '').trim();
  if (!output) return { record, value: null };
  try {
    return { record, value: JSON.parse(output) };
  } catch {
    return { record, value: output };
  }
}

function ensureQaDir() {
  if (!qaDir) return;
  fs.mkdirSync(qaDir, { recursive: true });
}

function writeEvidence(name, value) {
  if (!qaDir) return;
  fs.writeFileSync(path.join(qaDir, name), value, 'utf8');
}

function unwrapCliValue(value) {
  let current = value;
  for (let index = 0; index < 3; index += 1) {
    if (typeof current === 'string') {
      try {
        current = JSON.parse(current);
      } catch {
        break;
      }
      continue;
    }
    if (current && typeof current === 'object' && Object.prototype.hasOwnProperty.call(current, 'result')) {
      current = current.result;
      continue;
    }
    break;
  }
  return current;
}

function runCodeSource(width, height, screenshotPath) {
  return `async (page) => {
    await page.setViewportSize({ width: ${width}, height: ${height} });
    await page.goto(${JSON.stringify(pageUrl)}, { waitUntil: 'domcontentloaded' });
    await page.locator('.corporate-target-panel').waitFor({ state: 'visible', timeout: 15000 });
    await page.waitForTimeout(1000);
    await page.locator('.corporate-trend').scrollIntoViewIfNeeded();
    await page.waitForTimeout(1200);
    await page.evaluate(() => window.scrollTo(0, 0));
    const result = await page.evaluate(() => {
      const zoneDefinitions = [
        { name: 'deposit', selector: '.corporate-deposit__body', childSelector: '.corporate-data-card,.corporate-deposit__footer' },
        { name: 'segments', selector: '.corporate-segment-list', childSelector: '.corporate-segment-row' },
        { name: 'attention', selector: '.corporate-attention-list', childSelector: 'li' },
        { name: 'ranking', selector: '.corporate-ranking-list', childSelector: '.corporate-ranking-row' },
        { name: 'targets', selector: '.corporate-target-list', childSelector: '.corporate-target-row' }
      ];
      const failures = [];
      const zones = zoneDefinitions.map(definition => {
        const element = document.querySelector(definition.selector);
        if (!element) {
          const missing = { name: definition.name, selector: definition.selector, missing: true };
          failures.push({ type: 'missing-zone', ...missing });
          return missing;
        }
        const rect = element.getBoundingClientRect();
        const children = [...element.querySelectorAll(definition.childSelector)].map((child, index) => {
          const childRect = child.getBoundingClientRect();
          const rowOverflow = [...child.children].filter(cell => {
            const cellRect = cell.getBoundingClientRect();
            return cellRect.right > childRect.right + 1 || cellRect.left < childRect.left - 1;
          }).map(cell => cell.textContent.trim());
          const inContainer = childRect.top >= rect.top - 1 && childRect.bottom <= rect.bottom + 1;
          if (!inContainer) {
            failures.push({ type: 'child-outside-zone', zone: definition.name, index, top: childRect.top, bottom: childRect.bottom, zoneTop: rect.top, zoneBottom: rect.bottom });
          }
          if (rowOverflow.length) failures.push({ type: 'row-horizontal-overflow', zone: definition.name, index, cells: rowOverflow });
          return { index, top: childRect.top, bottom: childRect.bottom, inContainer, rowOverflow };
        });
        const hiddenContent = element.scrollHeight > element.clientHeight + 1;
        if (hiddenContent) {
          failures.push({ type: 'hidden-zone-content', zone: definition.name, scrollHeight: element.scrollHeight, clientHeight: element.clientHeight });
        }
        return {
          name: definition.name,
          selector: definition.selector,
          top: rect.top,
          bottom: rect.bottom,
          clientHeight: element.clientHeight,
          scrollHeight: element.scrollHeight,
          hiddenContent,
          children
        };
      });

      const footer = document.querySelector('.corporate-preview__footer')?.getBoundingClientRect();
      const grid = document.querySelector('.corporate-main-grid')?.getBoundingClientRect();
      const panelElements = [...document.querySelectorAll('.corporate-panel')];
      const panels = panelElements.map((element, index) => {
        const rect = element.getBoundingClientRect();
        return { index, name: element.querySelector('h2')?.textContent?.trim() || '', ...rect.toJSON() };
      });
      const overlaps = [];
      panels.forEach((left, leftIndex) => panels.slice(leftIndex + 1).forEach(right => {
        const width = Math.min(left.right, right.right) - Math.max(left.left, right.left);
        const height = Math.min(left.bottom, right.bottom) - Math.max(left.top, right.top);
        if (width > 1 && height > 1) overlaps.push({ left: left.name, right: right.name, width, height });
      }));
      if (panels.length !== 7) failures.push({ type: 'panel-count', expected: 7, actual: panels.length });
      if (overlaps.length) failures.push({ type: 'panel-overlap', overlaps });
      panels.forEach(panel => {
        if (footer && panel.bottom > footer.top + 1) failures.push({ type: 'panel-below-footer', name: panel.name, bottom: panel.bottom, footerTop: footer.top });
        if (grid && (panel.bottom > grid.bottom + 1 || panel.left < grid.left - 1 || panel.right > grid.right + 1)) {
          failures.push({ type: 'panel-outside-grid', name: panel.name, panel, grid: grid.toJSON() });
        }
      });

      const pageWidth = Math.max(document.documentElement.scrollWidth, document.body?.scrollWidth || 0);
      const dropdowns = document.querySelectorAll('.corporate-dashboard select,.corporate-dashboard [role="combobox"]').length;
      const trendElement = document.querySelector('.corporate-trend');
      const chartElement = document.querySelector('.corporate-trend__chart');
      const canvasElement = chartElement?.querySelector('canvas');
      const trendRect = trendElement?.getBoundingClientRect();
      const chartRect = chartElement?.getBoundingClientRect();
      const canvasRect = canvasElement?.getBoundingClientRect();
      const chart = {
        trend: trendRect?.toJSON() || null,
        container: chartRect?.toJSON() || null,
        canvas: canvasElement ? {
          rect: canvasRect?.toJSON() || null,
          width: canvasElement.width,
          height: canvasElement.height
        } : null
      };
      if (!chartRect || chartRect.width <= 1 || chartRect.height <= 1) {
        failures.push({ type: 'chart-zero-size', chart });
      }
      if (!canvasElement || canvasElement.width <= 0 || canvasElement.height <= 0) {
        failures.push({ type: 'chart-canvas-zero-size', chart });
      }
      if (canvasRect && trendRect && (canvasRect.left < trendRect.left - 1 || canvasRect.right > trendRect.right + 1 || canvasRect.top < trendRect.top - 1 || canvasRect.bottom > trendRect.bottom + 1)) {
        failures.push({ type: 'chart-canvas-outside-trend', chart });
      }
      if (pageWidth > innerWidth + 1) failures.push({ type: 'page-horizontal-overflow', pageWidth, viewportWidth: innerWidth });
      if (dropdowns) failures.push({ type: 'dropdown-control', count: dropdowns });
      return {
        viewport: { width: innerWidth, height: innerHeight },
        page: {
          width: pageWidth,
          height: Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight || 0),
          horizontalOverflow: pageWidth > innerWidth + 1,
          verticalScrollAvailable: Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight || 0) > innerHeight + 1
        },
        footer: footer?.toJSON() || null,
        grid: grid?.toJSON() || null,
        chart,
        zones,
        panels,
        overlaps,
        dropdowns,
        failures
      };
    });
    ${screenshotPath ? `await page.screenshot({ path: ${JSON.stringify(screenshotPath)}, fullPage: true });` : ''}
    return result;
  }`;
}

function collectCliEvidence() {
  const consoleResult = runCli(['console'], { allowFailure: true });
  const requestsResult = runCli(['requests', '--static'], { allowFailure: true });
  const routesResult = runCli(['route-list'], { allowFailure: true });
  if (!qaDir) return;
  writeEvidence('console.json', JSON.stringify(consoleResult.value, null, 2));
  writeEvidence('requests.json', JSON.stringify(requestsResult.value, null, 2));
  writeEvidence('routes.json', JSON.stringify(routesResult.value, null, 2));
  writeEvidence('console.txt', consoleResult.record.stdout || consoleResult.record.stderr || '');
  writeEvidence('requests.txt', requestsResult.record.stdout || requestsResult.record.stderr || '');
  writeEvidence('routes.txt', routesResult.record.stdout || routesResult.record.stderr || '');
}

function main() {
  ensureQaDir();
  runCli(['close'], { allowFailure: true });
  runCli(['open', pageUrl]);
  const results = [];
  try {
    for (const viewport of viewports) {
      const screenshotPath = qaDir
        ? path.join(qaDir, `corporate-content-${viewport.width}x${viewport.height}.png`)
        : '';
      try {
        const response = runCli(['run-code', runCodeSource(viewport.width, viewport.height, screenshotPath)]);
        const value = unwrapCliValue(response.value);
        const valid = value && typeof value === 'object'
          && value.viewport && Array.isArray(value.zones) && value.zones.length === 5
          && Array.isArray(value.failures);
        results.push(valid
          ? value
          : {
            viewport,
            failures: [{ type: 'invalid-playwright-result', value: String(value ?? '') }]
          });
      } catch (error) {
        results.push({ viewport, failures: [{ type: 'playwright-cli', message: error.message }] });
      }
    }
    collectCliEvidence();
  } finally {
    runCli(['close'], { allowFailure: true });
    if (qaDir) writeEvidence('commands.jsonl', commandRecords.map(record => JSON.stringify(record)).join('\n') + '\n');
  }

  const failures = results.flatMap(result => (Array.isArray(result?.failures) ? result.failures.map(failure => ({ viewport: result.viewport, ...failure })) : []));
  const output = { session, url: pageUrl, viewports, results, failures, passed: failures.length === 0 };
  if (qaDir) writeEvidence('results.json', JSON.stringify(output, null, 2));
  process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
  if (failures.length) process.exitCode = 1;
}

main();
