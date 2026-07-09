// Backend timestamps (createdAt, lastLogin, etc.) are serialized from Java
// LocalDateTime as e.g. "2026-07-04T10:30:00" — no "Z"/offset suffix.
// The backend now stores all such values in UTC (see
// CrowdMonitoringApplication.main(), which pins the JVM default time zone
// to UTC). The native JS `Date` constructor treats a date-time string with
// no timezone designator as LOCAL browser time, not UTC, which would
// silently shift every displayed timestamp by the user's UTC offset.
// This helper appends "Z" (when missing) so such strings are parsed as UTC,
// and the browser then correctly converts them to local time for display.
export function parseServerDate(value) {
  if (!value) return null
  const hasZone = /Z$|[+-]\d{2}:\d{2}$/.test(value)
  return new Date(hasZone ? value : `${value}Z`)
}
