// Backend timestamps (createdAt, lastLogin, etc.) are serialized from Java
// LocalDateTime as e.g. "2026-07-04T15:01:23" — no "Z"/offset suffix.
// The backend's JVM timezone is pinned to Asia/Kolkata (local system time
// for this deployment — see CrowdMonitoringApplication.main()), and every
// LocalDateTime.now() call stores that local wall-clock time directly, not
// UTC. This helper is a thin, explicit wrapper around `new Date(value)` so
// every date-consuming page goes through one place — if this deployment's
// server region ever changes, only this function needs updating.
export function parseServerDate(value) {
  if (!value) return null
  return new Date(value)
}
