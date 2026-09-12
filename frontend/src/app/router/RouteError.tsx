import { RetryableError } from '../../components/feedback/Feedback';

export function RouteError() {
  return <RetryableError onRetry={() => window.location.reload()} />;
}
