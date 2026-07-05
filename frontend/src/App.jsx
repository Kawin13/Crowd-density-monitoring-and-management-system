import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { useState, useCallback } from 'react'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import { AnalysisProvider } from './context/AnalysisContext'
import Layout from './components/common/Layout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import DashboardPage from './pages/DashboardPage'
import CameraPage from './pages/CameraPage'
import MonitoringPage from './pages/MonitoringPage'
import AlertsPage from './pages/AlertsPage'
import AnalyticsPage from './pages/AnalyticsPage'
import ReportsPage from './pages/ReportsPage'
import UsersPage from './pages/UsersPage'
import SettingsPage from './pages/SettingsPage'
import ProfilePage from './pages/ProfilePage'
import OvercrowdedAlarm from './components/alerts/OvercrowdedAlarm'
import { CrowdAlertBanner } from './components/alerts/CrowdAlertBanner'
import useCrowdAlerts from './hooks/useCrowdAlerts'

function ProtectedRoute({ children, roles }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="loading">Loading…</div>
  if (!user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to="/dashboard" replace />
  return children
}

function PublicRoute({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="loading">Loading…</div>
  if (user) return <Navigate to="/dashboard" replace />
  return children
}

/** Inner component — only mounts after AuthProvider is ready so useAuth() works */
function AlertSystem() {
  const { user } = useAuth()
  const [overcrowdedCams, setOvercrowdedCams] = useState([])
  const [banners, setBanners] = useState([]) // [{id, level, cameraName, occupancy}]

  const onOvercrowded = useCallback(cam => {
    setOvercrowdedCams(prev => {
      // avoid duplicates
      if (prev.some(c => c.id === cam.id)) return prev
      return [...prev, cam]
    })
  }, [])

  const onBanner = useCallback(({ level, cameraName, occupancy }) => {
    const id = `${cameraName}-${level}-${Date.now()}`
    setBanners(prev => [...prev, { id, level, cameraName, occupancy }])
    // Auto-dismiss banner after 8 seconds
    setTimeout(() => {
      setBanners(prev => prev.filter(b => b.id !== id))
    }, 8000)
  }, [])

  // Only poll when user is logged in
  useCrowdAlerts(user ? { onBanner, onOvercrowded } : { onBanner: null, onOvercrowded: null })

  const dismissAlarm = () => setOvercrowdedCams([])
  const dismissBanner = id => setBanners(prev => prev.filter(b => b.id !== id))

  return (
    <>
      {/* Stacked banner notifications — top-right, below toasts */}
      {banners.length > 0 && (
        <div style={{
          position: 'fixed', top: 80, right: 20, zIndex: 400,
          display: 'flex', flexDirection: 'column', gap: 8,
          maxWidth: 340, width: '100%',
        }}>
          {banners.map(b => (
            <CrowdAlertBanner
              key={b.id}
              level={b.level}
              cameraName={b.cameraName}
              occupancy={b.occupancy}
              onDismiss={() => dismissBanner(b.id)}
            />
          ))}
        </div>
      )}

      {/* Full-screen OVERCROWDED alarm */}
      {overcrowdedCams.length > 0 && (
        <OvercrowdedAlarm
          cameras={overcrowdedCams}
          onDismiss={dismissAlarm}
        />
      )}
    </>
  )
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login"          element={<PublicRoute><LoginPage /></PublicRoute>} />
      <Route path="/register"       element={<PublicRoute><RegisterPage /></PublicRoute>} />
      <Route path="/forgot-password"element={<PublicRoute><ForgotPasswordPage /></PublicRoute>} />
      <Route path="/reset-password" element={<PublicRoute><ResetPasswordPage /></PublicRoute>} />

      <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard"  element={<DashboardPage />} />
        <Route path="cameras"    element={<ProtectedRoute roles={['ADMIN','OPERATOR']}><CameraPage /></ProtectedRoute>} />
        <Route path="monitoring" element={<MonitoringPage />} />
        <Route path="alerts"     element={<AlertsPage />} />
        <Route path="analytics"  element={<AnalyticsPage />} />
        <Route path="reports"    element={<ReportsPage />} />
        <Route path="users"      element={<ProtectedRoute roles={['ADMIN']}><UsersPage /></ProtectedRoute>} />
        <Route path="settings"   element={<SettingsPage />} />
        <Route path="profile"    element={<ProfilePage />} />
      </Route>

      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <AnalysisProvider>
          <BrowserRouter>
            <AppRoutes />
            <AlertSystem />
            <Toaster
              position="top-right"
              toastOptions={{
                style: {
                  background: 'var(--surface)',
                  color: 'var(--t1)',
                  border: '1px solid var(--border)',
                  fontSize: 13,
                },
                duration: 3000,
              }}
            />
          </BrowserRouter>
        </AnalysisProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
