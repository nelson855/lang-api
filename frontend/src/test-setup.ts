import '@testing-library/jest-dom';
import type { AxeResults } from 'axe-core';
import { afterEach, expect } from 'vitest';

expect.extend({
  toHaveNoViolations(received: AxeResults) {
    const violations = received.violations ?? [];
    return {
      pass: violations.length === 0,
      message: () =>
        violations.length === 0
          ? '预期存在可访问性问题'
          : violations.map((violation) => `${violation.id}: ${violation.help}`).join('\n'),
    };
  },
});

// jsdom 缺 Radix Select 需要的 Pointer Capture、ResizeObserver、scrollIntoView API
if (typeof Element !== 'undefined' && !Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
  Element.prototype.setPointerCapture = () => undefined;
  Element.prototype.releasePointerCapture = () => undefined;
  Element.prototype.scrollIntoView = () => undefined;
}

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

afterEach(() => {
  localStorage.clear();
  document.documentElement.setAttribute('lang', '');
});
