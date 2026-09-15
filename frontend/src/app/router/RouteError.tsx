import { useEffect } from 'react';
import { RetryableError } from '../../components/feedback/Feedback';
import { markRouteErrorIndexed } from '../../layouts/usePageChrome';

export function RouteError() {
  useEffect(() => {
    markRouteErrorIndexed();
  }, []);
  return <RetryableError onRetry={() => window.location.reload()} />;
}
