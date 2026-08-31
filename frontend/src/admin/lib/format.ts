/** Backend timestamps are epoch seconds (`toEpochSecond`); log entries are millis. */
export function fmtDate(epochSeconds?: number | null): string {
  if (!epochSeconds) return '—'
  return new Date(epochSeconds * 1000).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit',
  })
}

export function fmtDateTime(epochSeconds?: number | null): string {
  if (!epochSeconds) return '—'
  return new Date(epochSeconds * 1000).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

/** Coarse relative age, e.g. "3d ago" — enough to triage a queue at a glance. */
export function fmtAgo(epochSeconds?: number | null): string {
  if (!epochSeconds) return '—'
  const secs = Math.floor(Date.now() / 1000) - epochSeconds
  if (secs < 60) return 'just now'
  if (secs < 3600) return `${Math.floor(secs / 60)}m ago`
  if (secs < 86400) return `${Math.floor(secs / 3600)}h ago`
  if (secs < 2592000) return `${Math.floor(secs / 86400)}d ago`
  return fmtDate(epochSeconds)
}

export function fmtLogTime(epochMillis: number): string {
  const d = new Date(epochMillis)
  const pad = (n: number, w = 2) => String(n).padStart(w, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`
}

export function fmtNumber(n?: number | null): string {
  return typeof n === 'number' ? n.toLocaleString() : '—'
}

/** Last dotted segment of a logger name, which is the part that identifies it. */
export function shortLogger(logger: string): string {
  return logger.split('.').pop() || logger
}
