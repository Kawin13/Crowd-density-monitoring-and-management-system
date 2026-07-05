import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, Camera, Activity, Bell, BarChart2,
  FileText, Users, Settings, LogOut, X, Radio, ChevronRight
} from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import toast from 'react-hot-toast'

const NAV = [
  { to: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/cameras',   icon: Camera,          label: 'Cameras',   roles: ['ADMIN','OPERATOR'] },
  { to: '/monitoring',icon: Activity,         label: 'Monitoring' },
  { to: '/alerts',    icon: Bell,             label: 'Alerts' },
  { to: '/analytics', icon: BarChart2,        label: 'Analytics' },
  { to: '/reports',   icon: FileText,         label: 'Reports' },
  { to: '/users',     icon: Users,            label: 'Users',     roles: ['ADMIN'] },
  { to: '/settings',  icon: Settings,         label: 'Settings' },
]

const ROLE_COLOR = { ADMIN: '#3B82F6', OPERATOR: '#F59E0B', VIEWER: '#10B981' }

export default function Sidebar({ open, onClose }) {
  const { user, logout, hasRole } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    toast.success('Signed out successfully')
    navigate('/login')
  }

  const visible = NAV.filter(n => !n.roles || hasRole(...n.roles))

  return (
    <>
      {/* Mobile overlay */}
      {open && (
        <div
          onClick={onClose}
          style={{
            display: typeof window !== 'undefined' && window.innerWidth < 1024 ? 'block' : 'none',
            position: 'fixed', inset: 0, zIndex: 99,
            background: 'rgba(0,0,0,.5)', backdropFilter: 'blur(4px)'
          }}
        />
      )}

      <aside style={{
        position: 'fixed', top: 0, left: 0, bottom: 0,
        width: 'var(--sidebar-w)',
        background: 'var(--sidebar-bg)',
        display: 'flex', flexDirection: 'column',
        zIndex: 100,
        transform: open ? 'translateX(0)' : 'translateX(-100%)',
        transition: 'transform 0.25s cubic-bezier(.4,0,.2,1)',
        borderRight: '1px solid rgba(255,255,255,.06)',
      }}>

        {/* Brand */}
        <div style={{
          padding: '20px 20px 16px',
          borderBottom: '1px solid rgba(255,255,255,.06)',
          display: 'flex', alignItems: 'center', gap: 12
        }}>
          <div style={{
            width: 38, height: 38, borderRadius: 10,
            background: 'linear-gradient(135deg,#2563EB,#7C3AED)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0, boxShadow: '0 4px 12px rgba(37,99,235,.4)'
          }}>
            <Radio size={19} color="#fff" />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 700, fontSize: 13, color: '#F1F5F9', lineHeight: 1.2 }}>
              Crowd Density
            </div>
            <div style={{ fontSize: 10, color: '#64748B', marginTop: 2, lineHeight: 1 }}>
              Monitoring System
            </div>
          </div>
          <button
            onClick={onClose}
            style={{
              display: typeof window !== 'undefined' && window.innerWidth < 1024 ? 'flex' : 'none',
              alignItems: 'center', justifyContent: 'center',
              width: 28, height: 28, borderRadius: 6,
              background: 'rgba(255,255,255,.06)', color: '#64748B', flexShrink: 0
            }}
          >
            <X size={15} />
          </button>
        </div>

        {/* Nav section */}
        <nav style={{ flex: 1, padding: '16px 10px 12px', overflowY: 'auto' }}>
          {visible.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => window.innerWidth < 1024 && onClose()}
              style={({ isActive }) => ({
                display: 'flex', alignItems: 'center', gap: 10,
                padding: '9px 12px', borderRadius: 8, marginBottom: 2,
                fontSize: 13, fontWeight: 500,
                textDecoration: 'none',
                transition: 'all .15s',
                background: isActive ? 'rgba(37,99,235,.2)' : 'transparent',
                color: isActive ? '#93C5FD' : '#64748B',
                borderLeft: isActive ? '2px solid #3B82F6' : '2px solid transparent',
              })}
            >
              <Icon size={16} style={{ flexShrink: 0 }} />
              <span style={{ flex: 1 }}>{label}</span>
              {/* subtle arrow on hover — done via CSS-in-JS would need state, skip */}
            </NavLink>
          ))}
        </nav>

        {/* User footer */}
        <div style={{ padding: '10px', borderTop: '1px solid rgba(255,255,255,.06)' }}>
          <NavLink
            to="/profile"
            style={{
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '10px 12px', borderRadius: 8, marginBottom: 4,
              textDecoration: 'none',
              transition: 'background .15s',
            }}
            onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,.04)'}
            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          >
            <div style={{
              width: 34, height: 34, borderRadius: '50%', flexShrink: 0,
              background: `${ROLE_COLOR[user?.role] || '#2563EB'}33`,
              border: `2px solid ${ROLE_COLOR[user?.role] || '#2563EB'}`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: ROLE_COLOR[user?.role] || '#2563EB',
              fontWeight: 700, fontSize: 13
            }}>
              {(user?.fullName || user?.username || 'U')[0].toUpperCase()}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#E2E8F0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {user?.fullName || user?.username}
              </div>
              <div style={{ fontSize: 10, color: ROLE_COLOR[user?.role] || '#64748B', fontWeight: 600, marginTop: 1 }}>
                {user?.role}
              </div>
            </div>
            <ChevronRight size={13} color="#334155" />
          </NavLink>

          <button
            onClick={handleLogout}
            style={{
              display: 'flex', alignItems: 'center', gap: 10,
              width: '100%', padding: '9px 12px', borderRadius: 8,
              background: 'transparent', color: '#EF4444',
              fontSize: 13, fontWeight: 500,
              transition: 'background .15s',
            }}
            onMouseEnter={e => e.currentTarget.style.background = 'rgba(239,68,68,.1)'}
            onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
          >
            <LogOut size={15} /> Sign Out
          </button>
        </div>
      </aside>
    </>
  )
}
