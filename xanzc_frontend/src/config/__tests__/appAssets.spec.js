// @vitest-environment happy-dom

import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const testDirectory = dirname(fileURLToPath(import.meta.url));
const frontendRoot = resolve(testDirectory, '../../..');
const indexHtmlPath = resolve(frontendRoot, 'index.html');
const faviconPath = resolve(frontendRoot, 'public/favicon.svg');

describe('主平台静态资源', () => {
  it('index.html 引用本地 SVG favicon', () => {
    const indexHtml = readFileSync(indexHtmlPath, 'utf8');

    expect(indexHtml).toMatch(
      /<link\b[^>]*\brel=["']icon["'][^>]*\btype=["']image\/svg\+xml["'][^>]*\bhref=["']\/favicon\.svg["'][^>]*>/i
    );
  });

  it('favicon.svg 存在且是带无障碍标题的可解析本地 SVG', () => {
    expect(existsSync(faviconPath)).toBe(true);

    const svgSource = readFileSync(faviconPath, 'utf8');
    const svgDocument = new DOMParser().parseFromString(svgSource, 'image/svg+xml');

    expect(svgDocument.documentElement?.tagName.toLowerCase()).toBe('svg');
    expect(svgDocument.querySelector('parsererror')).toBeNull();
    expect(svgDocument.querySelector('title')?.textContent.trim()).not.toBe('');
    expect(svgDocument.documentElement?.getAttribute('viewBox')).toBe('0 0 64 64');
  });

  it('favicon 不包含可见文字、外链、脚本或 emoji', () => {
    const svgSource = readFileSync(faviconPath, 'utf8');
    const standardSvgNamespace = /xmlns=["']http:\/\/www\.w3\.org\/2000\/svg["']/gi;
    const sourceWithoutNamespace = svgSource.replace(standardSvgNamespace, '');

    expect(svgSource).not.toMatch(/<text\b/i);
    expect(svgSource).not.toMatch(/<script\b|\bon[a-z]+\s*=/i);
    expect(svgSource).not.toMatch(/\b(?:href|src)\s*=/i);
    expect(sourceWithoutNamespace).not.toMatch(/(?:https?:|data:|ftp:|\/\/|url\s*\()/i);
    expect(svgSource).not.toMatch(/\p{Extended_Pictographic}/u);
  });
});
