import { createContext, useContext, useRef, useState, useCallback } from 'react'
import toast from 'react-hot-toast'
import api from '../services/api'

/**
 * AnalysisContext
 * ------------------------------------------------------------------
 * Global, router-independent home for continuous-analysis UI state.
 *
 * The actual analysis session (VideoCapture, frame loop) lives in the AI
 * service and is unaffected by frontend navigation. But the frontend's
 * "is this camera analyzing?" flag used to live inside MonitoringPage's
 * FeedCard component state, which unmounts every time the user navigates
 * to Dashboard / Cameras / Alerts / Reports / Analytics and back — losing
 * the indicator and making it look like analysis had stopped.
 *
 * Moving that state up to a context mounted above the router (same
 * pattern as AlertSystem in App.jsx) means it survives route changes.
 * `syncStatus` additionally reconciles with the AI service's real session
 * status once per camera per app load, so a hard page refresh also
 * reconnects to a session that's still running in the background.
 */
const AnalysisContext = createContext(null)

export function AnalysisProvider({ children }) {
  // { [cameraId]: boolean } — global "isAnalyzing" per camera.
  const [analyzingMap, setAnalyzingMap] = useState({})

  // Guards against overlapping start/stop calls for the same camera
  // (e.g. a double-click before the first request resolves).
  const inFlightRef = useRef(new Set())
  // Cameras we've already reconciled with the backend's real session
  // status this app session, so we don't re-check on every remount.
  const syncedRef = useRef(new Set())

  const setAnalyzing = useCallback((cameraId, value) => {
    setAnalyzingMap(prev => (prev[cameraId] === value ? prev : { ...prev, [cameraId]: value }))
  }, [])

  const isAnalyzing = useCallback((cameraId) => !!analyzingMap[cameraId], [analyzingMap])

  /**
   * "Analyze Now". Idempotent: if a session is already running for this
   * camera, the backend does NOT open a new capture — it just reports
   * status: 'already_running'. Per spec, both outcomes show an
   * "Analysis started…" style toast (never "already running").
   */
  const startAnalysis = useCallback(async (camera) => {
    const cameraId = camera.id
    if (inFlightRef.current.has(cameraId)) return // ignore rapid repeated clicks mid-request
    inFlightRef.current.add(cameraId)
    try {
      const res = await api.post(`/monitoring/cameras/${cameraId}/analyze-stream/start`)
      const status = res.data?.data?.status
      setAnalyzing(cameraId, true)
      syncedRef.current.add(cameraId)
      toast.success(status === 'already_running' ? 'Analysis started.' : 'Analysis started successfully.')
    } catch {
      toast.error('Service temporarily unavailable')
    } finally {
      inFlightRef.current.delete(cameraId)
    }
  }, [setAnalyzing])

  /** "Stop Analysis" — releases the VideoCapture and closes the session. */
  const stopAnalysis = useCallback(async (camera) => {
    const cameraId = camera.id
    if (inFlightRef.current.has(cameraId)) return
    inFlightRef.current.add(cameraId)
    try {
      await api.post(`/monitoring/cameras/${cameraId}/analyze-stream/stop`)
      setAnalyzing(cameraId, false)
      syncedRef.current.add(cameraId)
      toast.success('Analysis stopped.')
    } catch {
      toast.error('Service temporarily unavailable')
    } finally {
      inFlightRef.current.delete(cameraId)
    }
  }, [setAnalyzing])

  /**
   * Reconciles local UI state with the AI service's real session status.
   * Called once per camera when a FeedCard mounts (covers both returning
   * to Monitoring via SPA navigation — usually already correct — and a
   * hard page reload, where in-memory context state was lost entirely).
   */
  const syncStatus = useCallback(async (cameraId) => {
    if (syncedRef.current.has(cameraId)) return
    syncedRef.current.add(cameraId)
    try {
      const res = await api.get(`/monitoring/cameras/${cameraId}/analyze-stream/status`)
      setAnalyzing(cameraId, !!res.data?.data?.is_analyzing)
    } catch {
      // Leave state as-is if the status check itself fails
    }
  }, [setAnalyzing])

  return (
    <AnalysisContext.Provider value={{ isAnalyzing, startAnalysis, stopAnalysis, syncStatus }}>
      {children}
    </AnalysisContext.Provider>
  )
}

export function useAnalysis() {
  const ctx = useContext(AnalysisContext)
  if (!ctx) throw new Error('useAnalysis must be used within an AnalysisProvider')
  return ctx
}
