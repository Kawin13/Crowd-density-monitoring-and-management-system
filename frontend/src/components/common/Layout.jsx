import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(true)

  useEffect(() => {
    const check = () => {
      if (window.innerWidth < 1024) setSidebarOpen(false)
      else setSidebarOpen(true)
    }
    check()
    window.addEventListener('resize', check)
    return () => window.removeEventListener('resize', check)
  }, [])

  return (
    <div className="app-shell">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div
        className="main-content"
        style={{ marginLeft: sidebarOpen && window.innerWidth >= 1024 ? 'var(--sidebar-w)' : 0 }}
      >
        <Topbar onMenuClick={() => setSidebarOpen(o => !o)} />
        <div className="page-container fade-in">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
