import { useNavigate } from 'react-router-dom'
import { Menu, Bell, Sun, Moon, Search } from 'lucide-react'
import { useTheme } from '../../context/ThemeContext'
import { useAuth } from '../../context/AuthContext'
import { useState, useEffect } from 'react'
import api from '../../services/api'

export default function Topbar({ onMenuClick }) {
  const { theme, toggleTheme } = useTheme()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [alertCount, setAlertCount] = useState(0)

  useEffect(() => {
    const fetch = async () => {
      try { const r = await api.get('/alerts/count'); setAlertCount(r.data.data || 0) } catch {}
    }
    fetch()
    const iv = setInterval(fetch, 30000)
    return () => clearInterval(iv)
  }, [])

  return (
    <header style={{
      height: 'var(--topbar-h)',
      background: 'var(--surface)',
      borderBottom: '1px solid var(--border)',
      display: 'flex', alignItems: 'center',
      padding: '0 24px', gap: 12,
      position: 'sticky', top: 0, zIndex: 50,
      boxShadow: 'var(--sh-xs)',
    }}>
      <button
        onClick={onMenuClick}
        className="btn-icon"
        style={{ flexShrink: 0 }}
        aria-label="Toggle menu"
      >
        <Menu size={18} />
      </button>

      <div style={{ flex: 1 }} />

      {/* Right actions */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <button
          onClick={toggleTheme}
          className="btn-icon"
          aria-label="Toggle theme"
          title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
        >
          {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
        </button>

        <button
          onClick={() => navigate('/alerts')}
          className="btn-icon"
          style={{ position: 'relative', color: alertCount > 0 ? 'var(--danger)' : undefined }}
          aria-label="Alerts"
          title="View alerts"
        >
          <Bell size={16} />
          {alertCount > 0 && (
            <span style={{
              position: 'absolute', top: -4, right: -4,
              background: 'var(--danger)', color: '#fff',
              fontSize: 9, fontWeight: 700, borderRadius: 99,
              minWidth: 16, height: 16, display: 'flex',
              alignItems: 'center', justifyContent: 'center', padding: '0 3px',
              border: '2px solid var(--surface)',
            }}>
              {alertCount > 99 ? '99+' : alertCount}
            </span>
          )}
        </button>

        <div style={{ width: 1, height: 24, background: 'var(--border)', margin: '0 4px' }} />

        <button
          onClick={() => navigate('/profile')}
          style={{
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '4px 10px 4px 4px',
            borderRadius: 'var(--r-md)',
            background: 'transparent',
            border: '1px solid transparent',
            transition: 'all .15s',
            cursor: 'pointer',
          }}
          onMouseEnter={e => { e.currentTarget.style.background = 'var(--surface-2)'; e.currentTarget.style.borderColor = 'var(--border)' }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = 'transparent' }}
        >
          <div style={{
            width: 30, height: 30, borderRadius: '50%',
            background: 'linear-gradient(135deg,#2563EB,#7C3AED)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#fff', fontWeight: 700, fontSize: 12,
          }}>
            {(user?.fullName || user?.username || 'U')[0].toUpperCase()}
          </div>
          <div style={{ textAlign: 'left' }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--t1)', lineHeight: 1.2 }}>
              {user?.fullName || user?.username}
            </div>
            <div style={{ fontSize: 10, color: 'var(--t3)', lineHeight: 1 }}>{user?.role}</div>
          </div>
        </button>
      </div>
    </header>
  )
}
