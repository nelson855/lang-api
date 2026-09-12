import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { useState } from 'react';
import { Dialog } from './Dialog';
import { renderWithLocale } from '../../test/render';

function Fixture() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        打开设置
      </button>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title="通知设置"
        description="选择接收通知的方式"
        actions={<button type="button">保存</button>}
      >
        <label>
          <input type="checkbox" /> 邮件通知
        </label>
      </Dialog>
    </>
  );
}

describe('Dialog 对话框', () => {
  it('只用键盘打开、浏览并关闭，焦点回到触发控件', async () => {
    const user = userEvent.setup();
    renderWithLocale(<Fixture />);
    const trigger = screen.getByRole('button', { name: '打开设置' });
    trigger.focus();
    await user.keyboard('{Enter}');
    const dialog = await screen.findByRole('dialog', { name: '通知设置' });
    expect(dialog).toBeInTheDocument();
    expect(dialog.contains(document.activeElement)).toBe(true);
    await user.tab();
    expect(dialog.contains(document.activeElement)).toBe(true);
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    await waitFor(() => expect(trigger).toHaveFocus());
  });
});
