/**
 * useCrowdAlerts.js
 *
 * Polls /api/cameras every 15 seconds and:
 *  1. Shows a coloured banner for MEDIUM / HIGH / CRITICAL levels.
 *  2. Triggers the OVERCROWDED full-screen alarm when any camera exceeds 100%.
 *
 * Alert deduplication rules:
 *  - A notification (banner or overcrowded alarm) is only fired when a
 *    camera's crowd level CHANGES from what it was the last time we
 *    checked. Consecutive polls reporting the same level (LOW, MEDIUM,
 *    HIGH, CRITICAL, or OVERCROWDED) never re-fire a notification.
 *      e.g. HIGH HIGH HIGH HIGH         -> 1 notification
 *           HIGH HIGH CRITICAL CRITICAL -> HIGH then CRITICAL only
 *           OVERCROWDED OVERCROWDED     -> 1 notification
 *  - The last-alerted level for a camera is stored per camera ID.
 *  - That stored level is reset (cleared) whenever the camera is no longer
 *    MONITORING (i.e. its Camera Service was stopped), so the next time it
 *    starts monitoring again is treated as a fresh session and can alert
 *    again even on the same level it last showed.
 *  - Banner notifications dismiss themselves after 8 seconds.
 *  - Only fires if the user has that alert level enabled in Settings.
 *
 * Alert stabilization:
 *  - Raw detections can flicker across a threshold (e.g. occupancy
 *    bouncing 25% / 26% between polls). A candidate level only replaces
 *    the committed level once it has been seen for STABILIZATION_MIN_CYCLES
 *    consecutive polls, OR has persisted for at least
 *    STABILIZATION_MIN_DURATION_MS — mirroring the same rule applied
 *    server-side in AlertService. Anything short of that is treated as a
 *    temporary fluctuation and ignored.
 */
import { useEffect, useRef, useCallback } from 'react'
import api from '../services/api'

const POLL_MS = 15_000
const STABILIZATION_MIN_CYCLES = 3
const STABILIZATION_MIN_DURATION_MS = 5_000

function getAlertPrefs() {
  try {
    return JSON.parse(localStorage.getItem('alertPrefs') ||
      '{"medium":true,"high":true,"critical":true,"overcrowded":true}')
  } catch { return { medium:true, high:true, critical:true, overcrowded:true } }
}

export default function useCrowdAlerts({ onBanner, onOvercrowded }) {
  // camera id -> committed/last-alerted level ('LOW'|'MEDIUM'|'HIGH'|'CRITICAL'|'OVERCROWDED')
  const lastLevelRef = useRef({})
  // camera id -> { level, count, firstSeenAt } — stabilization buffer for a
  // candidate level that differs from the committed one.
  const pendingRef = useRef({})

  const poll = useCallback(async () => {
    try {
      const prefs = getAlertPrefs()
      const r = await api.get('/cameras')
      const cameras = r.data.data || []
      const seenIds = new Set()

      cameras.forEach(cam => {
        seenIds.add(cam.id)
        const occ = Number(cam.currentOccupancy || 0)
        const level = occ > 100 ? 'OVERCROWDED' : (cam.currentCrowdLevel || 'LOW')

        // Camera Service stopped for this camera — reset its stored level so
        // the next monitoring session starts fresh (new session begins).
        if (cam.status !== 'MONITORING') {
          delete lastLevelRef.current[cam.id]
          delete pendingRef.current[cam.id]
          return
        }

        const committedLevel = lastLevelRef.current[cam.id]
        if (committedLevel === level) {
          // Stable at the already-committed level — clear any stale pending
          // buffer for a different candidate that may have been mid-flicker.
          delete pendingRef.current[cam.id]
          return
        }

        // Candidate level differs from the committed one — run it through
        // the stabilization buffer instead of committing immediately.
        const now = Date.now()
        const pending = pendingRef.current[cam.id]
        const sameCandidate = pending && pending.level === level
        const nextPending = sameCandidate
          ? { level, count: pending.count + 1, firstSeenAt: pending.firstSeenAt }
          : { level, count: 1, firstSeenAt: now }
        pendingRef.current[cam.id] = nextPending

        const stable = nextPending.count >= STABILIZATION_MIN_CYCLES ||
          (now - nextPending.firstSeenAt) >= STABILIZATION_MIN_DURATION_MS
        if (!stable) return // still stabilizing — ignore this fluctuation

        // Stabilized — commit the new level and notify once.
        lastLevelRef.current[cam.id] = level
        delete pendingRef.current[cam.id]

        if (level === 'OVERCROWDED') {
          if (prefs.overcrowded) onOvercrowded?.(cam)
        } else if (level === 'CRITICAL') {
          if (prefs.critical) onBanner?.({ level:'CRITICAL', cameraName: cam.cameraName, occupancy: occ })
        } else if (level === 'HIGH') {
          if (prefs.high) onBanner?.({ level:'HIGH', cameraName: cam.cameraName, occupancy: occ })
        } else if (level === 'MEDIUM') {
          if (prefs.medium) onBanner?.({ level:'MEDIUM', cameraName: cam.cameraName, occupancy: occ })
        }
        // LOW — no notification, but the level is still committed above so a
        // later LOW -> MEDIUM transition is correctly detected as a change.
      })

      // Clean up state for cameras that no longer exist
      Object.keys(lastLevelRef.current).forEach(id => {
        if (!seenIds.has(Number(id))) delete lastLevelRef.current[id]
      })
      Object.keys(pendingRef.current).forEach(id => {
        if (!seenIds.has(Number(id))) delete pendingRef.current[id]
      })
    } catch {}
  }, [onBanner, onOvercrowded])

  useEffect(() => {
    poll()
    const iv = setInterval(poll, POLL_MS)
    return () => clearInterval(iv)
  }, [poll])
}
