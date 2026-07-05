/**
 * CrowdAlertBanner.jsx
 *
 * Renders a coloured notification banner at the top-right of the screen
 * for LOW / MEDIUM / HIGH / CRITICAL levels.
 * OVERCROWDED is handled separately by OvercrowdedAlarm (full-screen popup).
 *
 * No sound is played for any of these levels — only the OVERCROWDED alarm makes noise.
 */
import { AlertTriangle, X } from 'lucide-react'
import { useState, useEffect } from 'react'

const LEVEL_STYLE = {
  LOW:      { bg:'#DCFCE7', border:'#86EFAC', text:'#166534', label:'LOW',       icon:'#16A34A' },
  MEDIUM:   { bg:'#DBEAFE', border:'#93C5FD', text:'#1D4ED8', label:'MEDIUM',    icon:'#2563EB' },
  HIGH:     { bg:'#FFF7ED', border:'#FED7AA', text:'#9A3412', label:'HIGH',      icon:'#EA580C' },
  CRITICAL: { bg:'#FEF2F2', border:'#FECACA', text:'#991B1B', label:'CRITICAL',  icon:'#DC2626' },
}

export function CrowdAlertBanner({ level, cameraName, occupancy, onDismiss }) {
  const [visible, setVisible] = useState(true)
  const s = LEVEL_STYLE[level]
  if (!s || !visible) return null

  const dismiss = () => { setVisible(false); onDismiss?.() }

  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 12,
      padding: '12px 14px',
      background: s.bg, border: `1px solid ${s.border}`,
      borderRadius: 10, boxShadow: '0 4px 12px rgba(0,0,0,.08)',
      maxWidth: 340, width: '100%',
      animation: 'slideUp .2s ease',
    }}>
      <AlertTriangle size={18} color={s.icon} style={{ flexShrink:0, marginTop:1 }}/>
      <div style={{ flex:1 }}>
        <div style={{ fontSize:13, fontWeight:700, color:s.text }}>
          {s.label} — {cameraName}
        </div>
        <div style={{ fontSize:12, color:s.text, opacity:.8, marginTop:2 }}>
          Occupancy: {Number(occupancy).toFixed(1)}%
        </div>
      </div>
      <button onClick={dismiss} style={{
        background:'none', border:'none', cursor:'pointer',
        color:s.text, opacity:.6, padding:0, display:'flex',
      }}>
        <X size={14}/>
      </button>
    </div>
  )
}
