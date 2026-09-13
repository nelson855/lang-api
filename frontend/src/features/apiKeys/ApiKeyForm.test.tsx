import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { portalRequest } from '../../api/portalClient';
import { renderApp } from '../../test/renderApp';
import { ApiKeyForm } from './ApiKeyForm';
import type { CreateApiKeyInput } from '../../api/apiKeys';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

function setup(pending = false, onSubmit: (input: CreateApiKeyInput) => void = () => {}) {
  const submit = vi.fn(onSubmit);
  const view = renderApp(
    <ApiKeyForm mode="create" pending={pending} serverError={null} onSubmit={submit} onCancel={() => {}} />,
  );
  return { submit, view };
}

describe('API Key 共享表单', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockResolvedValue({
      data: { siteName: '测试站', apiBaseUrls: [] },
      requestId: 'req-form',
    });
  });
  it('名称必填且限长，无限额度隐藏剩余额度', async () => {
    const user = userEvent.setup();
    const { submit } = setup();

    await user.click(await screen.findByRole('button', { name: '新建' }));
    expect(await screen.findByText('名称不能为空')).toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('名称', { exact: false }), 'x'.repeat(51));
    await user.click(screen.getByRole('button', { name: '新建' }));
    expect(await screen.findByText('名称不能超过 50 个字符')).toBeInTheDocument();

    expect(screen.getByLabelText('剩余额度', { exact: false })).toBeInTheDocument();
    await user.click(screen.getByLabelText('无限额度'));
    await waitFor(() => {
      expect(screen.queryByLabelText('剩余额度', { exact: false })).not.toBeInTheDocument();
    });
  });

  it('额度、期限与模型去重校验', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByRole('button', { name: '新建' });

    await user.type(screen.getByLabelText('名称', { exact: false }), 'probe');
    await user.clear(screen.getByLabelText('剩余额度', { exact: false }));
    await user.type(screen.getByLabelText('剩余额度', { exact: false }), '-5');
    await user.click(screen.getByRole('button', { name: '新建' }));
    expect(await screen.findByText('额度必须是非负整数')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('剩余额度', { exact: false }));
    await user.type(screen.getByLabelText('剩余额度', { exact: false }), '100');
    await user.type(screen.getByLabelText('过期时间'), '2000-01-01T00:00');
    await user.click(screen.getByRole('button', { name: '新建' }));
    expect(await screen.findByText('过期时间必须是未来时间')).toBeInTheDocument();
  });

  it('高级区域默认收起，提交去重并阻止重复提交', async () => {
    const user = userEvent.setup();
    const submit = vi.fn();
    const { view } = setup(false, submit);
    await screen.findByRole('button', { name: '新建' });
    expect(screen.queryByLabelText('模型限制')).not.toBeVisible();

    await user.click(screen.getByText('高级限制'));
    await user.type(screen.getByLabelText('名称', { exact: false }), 'probe');
    await user.clear(screen.getByLabelText('剩余额度', { exact: false }));
    await user.type(screen.getByLabelText('剩余额度', { exact: false }), '100');
    const models = screen.getByLabelText('模型限制');
    await user.type(models, 'gpt-4o,\n gpt-4o\nclaude-sonnet');
    await user.type(screen.getByLabelText('IP 限制'), '1.1.1.1');
    await user.click(screen.getByRole('button', { name: '新建' }));

    await waitFor(() => {
      expect(submit).toHaveBeenCalledTimes(1);
    });
    expect(submit).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'probe', models: ['gpt-4o', 'claude-sonnet'], ips: ['1.1.1.1'] }),
    );

    view.rerender(
      <ApiKeyForm mode="create" pending serverError={null} onSubmit={submit} onCancel={() => {}} />,
    );
    expect(screen.getByRole('button', { name: '新建' })).toBeDisabled();
  });

  it('IP 字面量之外拒绝并提示', async () => {
    const user = userEvent.setup();
    setup();
    await screen.findByRole('button', { name: '新建' });
    await user.click(screen.getByText('高级限制'));
    await user.type(screen.getByLabelText('名称', { exact: false }), 'probe');
    await user.clear(screen.getByLabelText('剩余额度', { exact: false }));
    await user.type(screen.getByLabelText('剩余额度', { exact: false }), '100');
    await user.type(screen.getByLabelText('IP 限制'), 'example.com');
    await user.click(screen.getByRole('button', { name: '新建' }));
    expect(await screen.findByText('只接受 IPv4 或 IPv6 地址')).toBeInTheDocument();
  });
});
