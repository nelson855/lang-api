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

afterEach(() => {
  localStorage.clear();
  document.documentElement.setAttribute('lang', '');
});
