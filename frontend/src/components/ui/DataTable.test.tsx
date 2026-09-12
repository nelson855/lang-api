import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { DataTable, type DataColumn } from './DataTable';
import { renderWithLocale } from '../../test/render';

interface Row {
  name: string;
  protocol: string;
}

const COLUMNS: Array<DataColumn<Row>> = [
  { key: 'name', header: '名称' },
  { key: 'protocol', header: '协议' },
];

describe('DataTable 数据表', () => {
  it('渲染语义表头与数据行', () => {
    renderWithLocale(
      <DataTable columns={COLUMNS} rows={[{ name: '模型 A', protocol: 'OPENAI' }]} />,
    );
    expect(screen.getByRole('columnheader', { name: '名称' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '模型 A' })).toBeInTheDocument();
  });

  it('空数据时显示空状态而不伪造行', () => {
    renderWithLocale(<DataTable columns={COLUMNS} rows={[]} emptyText="暂无模型数据" />);
    expect(screen.queryByRole('row', { name: /模型 A/ })).toBeNull();
    expect(screen.getByText('暂无模型数据')).toBeInTheDocument();
    expect(document.querySelector('.table-scroll')).not.toBeNull();
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(
      <DataTable columns={COLUMNS} rows={[{ name: '模型 A', protocol: 'OPENAI' }]} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
