/**
 * OvercrowdedAlarm.jsx
 *
 * Renders a full-screen red popup when any camera crosses 100% occupancy.
 * Alarm behaviour:
 *   - Plays a synthesized siren sound ONCE using the Web Audio API (no file needed).
 *   - Stops automatically after 10 seconds.
 *   - Mute button silences mid-play without dismissing the popup.
 *   - Replay button re-triggers the 10-second alarm once.
 *   - While occupancy stays above 100%, the popup stays visible but the
 *     sound does NOT replay automatically (plays once per entry event).
 *   - Dismiss button hides the popup until the next OVERCROWDED event.
 */
import { useEffect, useRef, useState } from 'react'
import { AlertTriangle, VolumeX, Volume2, RefreshCw, X } from 'lucide-react'

const ALARM_DURATION_MS = 10_000 // 10 seconds

/** Build a pulsing siren using Web Audio API (no external file required). */
function createAlarm(ctx) {
  const osc  = ctx.createOscillator()
  const gain = ctx.createGain()
  const lfo  = ctx.createOscillator()
  const lfoGain = ctx.createGain()

  // LFO modulates frequency: siren sweep effect
  lfo.frequency.value = 2          // 2 Hz sweep
  lfoGain.gain.value  = 200        // ±200 Hz sweep range
  lfo.connect(lfoGain)
  lfoGain.connect(osc.frequency)

  osc.type = 'sawtooth'
  osc.frequency.value = 880        // base pitch

  gain.gain.setValueAtTime(0.4, ctx.currentTime)
  osc.connect(gain)
  gain.connect(ctx.destination)

  lfo.start()
  osc.start()

  return { osc, lfo, gain }
}

export default function OvercrowdedAlarm({ cameras = [], onDismiss }) {
  // cameras: array of CameraResponse-shaped objects currently OVERCROWDED
  const [muted,   setMuted]   = useState(false)
  const [playing, setPlaying] = useState(false)
  const ctxRef    = useRef(null)
  const nodesRef  = useRef(null)
  const timerRef  = useRef(null)

  const stopSound = () => {
    if (nodesRef.current) {
      try {
        nodesRef.current.osc.stop()
        nodesRef.current.lfo.stop()
      } catch {}
      nodesRef.current = null
    }
    clearTimeout(timerRef.current)
    setPlaying(false)
  }

  const playSound = () => {
    if (muted) return
    stopSound()                           // clean up any prior nodes

    if (!ctxRef.current) {
      ctxRef.current = new (window.AudioContext || window.webkitAudioContext)()
    }
    // Resume if browser suspended it (autoplay policy)
    if (ctxRef.current.state === 'suspended') ctxRef.current.resume()

    nodesRef.current = createAlarm(ctxRef.current)
    setPlaying(true)

    // Auto-stop after ALARM_DURATION_MS
    timerRef.current = setTimeout(() => {
      stopSound()
    }, ALARM_DURATION_MS)
  }

  // Play once on mount (first OVERCROWDED entry)
  useEffect(() => {
    playSound()
    return () => stopSound()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleMute = () => {
    setMuted(true)
    stopSound()
  }

  const handleUnmute = () => {
    setMuted(false)
  }

  const handleReplay = () => {
    if (muted) {
      setMuted(false)
      // Give state a tick then play
      setTimeout(playSound, 50)
    } else {
      playSound()
    }
  }

  const worst = cameras[0] || {}

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 9999,
      background: 'rgba(127,0,0,.82)',
      backdropFilter: 'blur(8px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24,
      animation: 'fadeIn .2s ease',
    }}>
      <div style={{
        background: '#1A0000',
        border: '2px solid #EF4444',
        borderRadius: 20,
        padding: '40px 36px',
        maxWidth: 480, width: '100%',
        textAlign: 'center',
        boxShadow: '0 0 60px rgba(239,68,68,.6)',
        animation: 'slideUp .25s ease',
        position: 'relative',
      }}>
        {/* Close */}
        <button onClick={onDismiss} style={{
          position: 'absolute', top: 14, right: 14,
          background: 'rgba(255,255,255,.08)', border: 'none',
          borderRadius: 8, width: 32, height: 32, cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: '#fff',
        }}>
          <X size={16}/>
        </button>

        {/* Pulsing icon */}
        <div style={{
          width: 80, height: 80, borderRadius: '50%',
          background: 'rgba(239,68,68,.2)',
          border: '2px solid #EF4444',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          margin: '0 auto 20px',
          animation: playing ? 'pulse 1s ease-in-out infinite' : 'none',
        }}>
          <AlertTriangle size={36} color="#EF4444"/>
        </div>

        <div style={{ fontSize: 11, fontWeight: 700, color: '#EF4444', letterSpacing: 2, marginBottom: 8 }}>
          ⚠ OVERCROWDED ALERT
        </div>
        <h2 style={{ fontSize: 26, fontWeight: 800, color: '#fff', marginBottom: 8 }}>
          {worst.cameraName || 'Camera'}
        </h2>
        <p style={{ fontSize: 14, color: '#FCA5A5', marginBottom: 6 }}>
          {worst.locationName || 'Location'}
        </p>
        <div style={{
          fontSize: 42, fontWeight: 800,
          color: '#EF4444', lineHeight: 1, marginBottom: 6,
        }}>
          {worst.currentOccupancy != null ? `${Number(worst.currentOccupancy).toFixed(1)}%` : '>100%'}
        </div>
        <p style={{ fontSize: 13, color: '#94A3B8', marginBottom: 28 }}>
          {worst.currentPeopleCount ?? '—'} people / {worst.maximumCapacity ?? '—'} capacity
        </p>

        {cameras.length > 1 && (
          <p style={{ fontSize: 12, color: '#F87171', marginBottom: 20 }}>
            +{cameras.length - 1} other camera{cameras.length > 2 ? 's' : ''} also overcrowded
          </p>
        )}

        {/* Sound controls */}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginBottom: 20 }}>
          {muted ? (
            <button onClick={handleUnmute} style={{
              display: 'flex', alignItems: 'center', gap: 7,
              padding: '9px 18px', borderRadius: 8, border: '1px solid #334155',
              background: 'rgba(255,255,255,.06)', color: '#94A3B8',
              fontSize: 13, fontWeight: 500, cursor: 'pointer',
            }}>
              <Volume2 size={15}/> Unmute
            </button>
          ) : (
            <button onClick={handleMute} style={{
              display: 'flex', alignItems: 'center', gap: 7,
              padding: '9px 18px', borderRadius: 8, border: '1px solid #334155',
              background: 'rgba(255,255,255,.06)', color: '#94A3B8',
              fontSize: 13, fontWeight: 500, cursor: 'pointer',
            }}>
              <VolumeX size={15}/> Mute
            </button>
          )}
          <button onClick={handleReplay} disabled={playing && !muted} style={{
            display: 'flex', alignItems: 'center', gap: 7,
            padding: '9px 18px', borderRadius: 8, border: '1px solid #334155',
            background: 'rgba(255,255,255,.06)', color: playing && !muted ? '#475569' : '#94A3B8',
            fontSize: 13, fontWeight: 500, cursor: playing && !muted ? 'not-allowed' : 'pointer',
          }}>
            <RefreshCw size={15}/> Replay
          </button>
        </div>

        <button onClick={onDismiss} style={{
          width: '100%', padding: '12px', borderRadius: 10,
          background: '#EF4444', color: '#fff',
          border: 'none', fontSize: 14, fontWeight: 600, cursor: 'pointer',
          transition: 'background .15s',
        }}
          onMouseEnter={e => e.currentTarget.style.background = '#DC2626'}
          onMouseLeave={e => e.currentTarget.style.background = '#EF4444'}
        >
          Dismiss Alert
        </button>
      </div>
    </div>
  )
}
