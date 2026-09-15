import { usePublicConfigData } from '../../app/providers/publicConfigGate';
import {
  BRAND_MARK_FRAME,
  BRAND_MARK_PATHS,
  BRAND_MARK_STROKE_WIDTH,
  BRAND_MARK_VIEWBOX,
} from './brandAsset';

export function BrandMark() {
  return (
    <svg width="24" height="24" viewBox={BRAND_MARK_VIEWBOX} aria-hidden="true" focusable="false">
      <rect
        x={BRAND_MARK_FRAME.x}
        y={BRAND_MARK_FRAME.y}
        width={BRAND_MARK_FRAME.width}
        height={BRAND_MARK_FRAME.height}
        rx={BRAND_MARK_FRAME.rx}
        fill="var(--color-primary)"
      />
      {BRAND_MARK_PATHS.map((d) => (
        <path
          key={d}
          d={d}
          stroke="var(--color-primary-contrast)"
          strokeWidth={BRAND_MARK_STROKE_WIDTH}
          fill="none"
          strokeLinecap="round"
        />
      ))}
    </svg>
  );
}

export function Brand() {
  const config = usePublicConfigData();
  return (
    <span className="brand" role="img" aria-label={config.siteName}>
      <BrandMark />
      <span className="brand-name" aria-hidden="true">
        {config.siteName}
      </span>
    </span>
  );
}
