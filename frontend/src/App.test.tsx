import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { App } from './App';

function renderApp() {
  render(
    <MemoryRouter>
      <App />
    </MemoryRouter>,
  );
}

describe('最小应用壳', () => {
  it('渲染应用名称', () => {
    renderApp();
    expect(screen.getByRole('heading', { name: 'Lang API' })).toBeInTheDocument();
  });

  it('提供客户端路由入口', () => {
    renderApp();
    expect(screen.getByRole('link', { name: '控制台' })).toHaveAttribute('href', '/dashboard');
  });
});
