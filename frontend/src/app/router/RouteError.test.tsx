import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { buildRoutes } from './routes';
import { renderWithLocale } from '../../test/render';
import { RouteError } from './RouteError';

describe('路由错误边界', () => {
  it('各级路由均挂载自有错误页', () => {
    const withBoundary = buildRoutes().filter((route) => route.errorElement !== undefined);
    expect(withBoundary.length).toBe(buildRoutes().length);
  });

  it('错误页可重试恢复', () => {
    const reload = vi.fn();
    Object.defineProperty(window, 'location', { value: { reload }, writable: true });
    renderWithLocale(<RouteError />);
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
