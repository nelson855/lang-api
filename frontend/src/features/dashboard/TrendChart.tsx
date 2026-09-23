import { useMemo } from 'react';
import './TrendChart.css';

export interface TrendPointInput {
  bucketStart: string;
  bucketEnd: string;
  value: string;
}

export interface TrendChartProps {
  title: string;
  description: string;
  points: TrendPointInput[];
  timezone: string;
  locale: string;
  chartKind?: 'line';
}

const W = 280;
const H = 96;
const PAD = 8;

function prefersReducedMotion(): boolean {
  try {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}

function toNumber(value: string): number {
  const n = Number(value);
  return Number.isFinite(n) ? n : NaN;
}

function formatAxis(iso: string, timezone: string, locale: string): string {
  try {
    return new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function TrendChart({ title, description, points, timezone, locale }: TrendChartProps) {
  const { linePoints, axisLabel } = useMemo(() => {
    const finite = points
      .map((p) => ({ p, n: toNumber(p.value) }))
      .filter((x) => !Number.isNaN(x.n));
    if (finite.length === 0) {
      return { linePoints: '', axisLabel: '' };
    }
    const values = finite.map((x) => x.n);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const firstT = Date.parse(finite[0].p.bucketStart);
    const lastT = Date.parse(finite[finite.length - 1].p.bucketStart);
    const timeSpan = lastT - firstT || 1;
    const toX = (p: TrendPointInput) => PAD + ((Date.parse(p.bucketStart) - firstT) / timeSpan) * (W - 2 * PAD);
    const toY = (n: number) => H - PAD - ((n - min) / span) * (H - 2 * PAD);
    const coords = finite.map(({ p, n }) => `${toX(p)},${toY(n)}`).join(' ');
    return {
      linePoints: coords,
      axisLabel: `${formatAxis(finite[0].p.bucketStart, timezone, locale)} — ${formatAxis(
        finite[finite.length - 1].p.bucketStart,
        timezone,
        locale,
      )}`,
    };
  }, [points, timezone, locale]);

  const reduced = prefersReducedMotion();

  if (points.length === 0) {
    return (
      <div className="trend-chart trend-chart-empty" role="img" aria-label={description}>
        <p>{description}</p>
      </div>
    );
  }

  return (
    <figure className="trend-chart">
      <figcaption>{title}</figcaption>
      <div className="trend-chart-canvas" aria-hidden="true">
        <svg viewBox={`0 0 ${W} ${H}`} role="presentation" className="trend-chart-svg">
          {linePoints ? (
            <polyline
              className={reduced ? 'trend-chart-path' : 'trend-chart-path trend-animate'}
              points={linePoints}
              fill="none"
            />
          ) : null}
        </svg>
        {axisLabel ? <div className="trend-chart-axis">{axisLabel}</div> : null}
      </div>
      <div className="trend-chart-fallback" role="region">
        <table className="trend-data-table visually-hidden">
          <caption className="visually-hidden">{description}</caption>
          <thead>
            <tr>
              <th scope="col">bucket</th>
              <th scope="col">value</th>
            </tr>
          </thead>
          <tbody>
            {points.map((p) => (
              <tr key={p.bucketStart}>
                <td>{formatAxis(p.bucketStart, timezone, locale)}</td>
                <td>{p.value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </figure>
  );
}