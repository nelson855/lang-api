import { useTranslation } from 'react-i18next';
import './DataTable.css';

export interface PaginationProps {
  page: number;
  pageSize: number;
  total: number;
  onChange: (page: number) => void;
}

export function totalPagesOf(total: number, pageSize: number): number {
  const safeSize = pageSize > 0 ? pageSize : 1;
  return Math.max(1, Math.ceil(Math.max(0, total) / safeSize));
}

export function clampPage(page: number, totalPages: number): number {
  if (!Number.isFinite(page)) {
    return 1;
  }
  return Math.min(totalPages, Math.max(1, Math.floor(page)));
}

function visiblePages(current: number, totalPages: number): number[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }
  const pages = new Set([1, totalPages, current - 1, current, current + 1]);
  return [...pages].filter((page) => page >= 1 && page <= totalPages).sort((a, b) => a - b);
}

export function Pagination({ page, pageSize, total, onChange }: PaginationProps) {
  const { t } = useTranslation();
  const totalPages = totalPagesOf(total, pageSize);
  const current = clampPage(page, totalPages);

  const goTo = (target: number) => {
    const next = clampPage(target, totalPages);
    if (next !== current) {
      onChange(next);
    }
  };

  return (
    <nav className="pagination" aria-label={t('common.pageStatus', { current, total: totalPages })}>
      <button
        type="button"
        className="pagination-page"
        disabled={current <= 1}
        onClick={() => goTo(current - 1)}
      >
        {t('common.prevPage')}
      </button>
      {visiblePages(current, totalPages).map((item) => (
        <button
          key={item}
          type="button"
          className="pagination-page"
          aria-current={item === current ? 'page' : undefined}
          aria-label={t('common.pageStatus', { current: item, total: totalPages })}
          onClick={() => goTo(item)}
        >
          {item}
        </button>
      ))}
      <button
        type="button"
        className="pagination-page"
        disabled={current >= totalPages}
        onClick={() => goTo(current + 1)}
      >
        {t('common.nextPage')}
      </button>
      <span className="pagination-status" role="status">
        {t('common.pageStatus', { current, total: totalPages })}
      </span>
    </nav>
  );
}
