import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useQueryClient } from '@tanstack/react-query';
import { AppQueryProvider } from './queryProvider';

function Probe() {
  const client = useQueryClient();
  return <p>{`retry=${String(client.getDefaultOptions().queries?.retry)}`}</p>;
}

describe('应用级 Query Provider', () => {
  it('为子树提供保守默认的客户端', () => {
    render(
      <AppQueryProvider>
        <Probe />
      </AppQueryProvider>,
    );
    expect(screen.getByText('retry=false')).toBeInTheDocument();
  });
});
