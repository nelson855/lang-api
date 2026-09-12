import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import './DataTable.css';

export interface DataColumn<T> {
  key: string;
  header: ReactNode;
  render?: (row: T) => ReactNode;
}

export interface DataTableProps<T> {
  columns: Array<DataColumn<T>>;
  rows: readonly T[];
  emptyText?: string;
  rowKey?: (row: T, index: number) => string;
}

export function DataTable<T>({ columns, rows, emptyText, rowKey }: DataTableProps<T>) {
  const { t } = useTranslation();
  const emptyLabel = emptyText ?? t('states.empty');
  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="data-table-empty">
                {emptyLabel}
              </td>
            </tr>
          ) : (
            rows.map((row, index) => (
              <tr key={rowKey ? rowKey(row, index) : index}>
                {columns.map((column) => (
                  <td key={column.key}>{column.render ? column.render(row) : (row as Record<string, ReactNode>)[column.key]}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
