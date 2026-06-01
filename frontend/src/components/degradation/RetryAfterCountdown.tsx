import { useEffect, useState } from 'react';

type RetryAfterCountdownProps = {
  retryAfterSeconds?: number;
  targetTimestamp?: number;
};

function secondsUntil(targetTimestamp?: number): number {
  if (!targetTimestamp) return 0;
  return Math.max(0, Math.ceil((targetTimestamp - Date.now()) / 1000));
}

export function RetryAfterCountdown({ retryAfterSeconds, targetTimestamp }: RetryAfterCountdownProps) {
  const initialTarget = targetTimestamp ?? (retryAfterSeconds ? Date.now() + retryAfterSeconds * 1000 : undefined);
  const [remaining, setRemaining] = useState(() => secondsUntil(initialTarget));

  useEffect(() => {
    if (!initialTarget) return undefined;

    setRemaining(secondsUntil(initialTarget));
    const interval = window.setInterval(() => {
      setRemaining(secondsUntil(initialTarget));
    }, 1000);

    return () => window.clearInterval(interval);
  }, [initialTarget]);

  if (!initialTarget || remaining <= 0) {
    return <span className="retry-countdown">Puedes intentar nuevamente.</span>;
  }

  return <span className="retry-countdown">Reintento seguro en {remaining}s.</span>;
}
