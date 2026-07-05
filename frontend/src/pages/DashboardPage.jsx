import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts'
import {
  Camera, Users, AlertTriangle, Activity, TrendingUp,
  Bell, RefreshCw, ArrowRight
} from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'

const LEVEL_COLORS = {
  LOW:'#10B981', MEDIUM:'#F59E0B', HIGH:'#F97316', CRITICAL:'#EF4444', OVERCROWDED:'#7C3AED'
}

function StatCard({ icon: Icon, label, value, color, sub }) {
  return (
    <div className="stat-card">
      <div className="stat-icon" style={{ background: color + '18' }}>
        <Icon size={20} color={color} />
      </div>
      <div>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
        {sub && <div className="stat-sub">{sub}</div>}
      </div>
    </div>
  )
}

function OccBar({ pct, level }) {
  const color = LEVEL_COLORS[level] || '#10B981'
  return (
    <div className="occ-bar" style={{ marginTop: 6 }}>
      <div className="occ-fill" style={{ width: `${Math.min(pct, 100)}%`, background: color }} />
    </div>
  )
}

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  return (
    <div style={{
      background: 'var(--surface)', border: '1px solid var(--border)',
      borderRadius: 10, padding: '10px 14px', boxShadow: 'var(--sh-md)', fontSize: 13
    }}>
      <div style={{ fontWeight: 600, color: 'var(--t1)', marginBottom: 4 }}>{label}</div>
      {payload.map(p => (
        <div key={p.name} style={{ color: p.color || 'var(--t2)' }}>
          {p.name}: <strong>{p.value}</strong>
        </div>
      ))}
    </div>
  )
}

export default function DashboardPage() {
  const [data, setData] = useState(null)
  const [analytics, setAnalytics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const navigate = useNavigate()

  const fetchDashboard = useCallback(async (silent = false) => {
    if (!silent) setLoading(true)
    else setRefreshing(true)
    try {
      const [dashResult, analyticsResult] = await Promise.allSettled([
        api.get('/dashboard'),
        api.get('/analytics/daily')
      ])
      if (dashResult.status === 'fulfilled') setData(dashResult.value.data.data)
      else if (!silent) toast.error('Unable to fetch data')
      if (analyticsResult.status === 'fulfilled') setAnalytics(analyticsResult.value.data.data)
    } catch {
      if (!silent) toast.error('Unable to fetch data')
    } finally { setLoading(false); setRefreshing(false) }
  }, [])

  useEffect(() => {
    fetchDashboard()
    const iv = setInterval(() => fetchDashboard(true), 30000)
    return () => clearInterval(iv)
  }, [fetchDashboard])

  const hourlyData = analytics?.hourlyTrend?.map(h => ({ hour: `${h.hour}:00`, People: Math.round(h.avgCount || 0) })) || []
  const crowdPie = data ? Object.entries(data.crowdLevelCounts || {}).filter(([,v]) => v > 0).map(([k,v]) => ({ name: k, value: v })) : []

  if (loading) return (
    <div>
      <div style={{ marginBottom: 28 }}>
        <div className="skeleton" style={{ height: 28, width: 200, marginBottom: 8 }} />
        <div className="skeleton" style={{ height: 16, width: 280 }} />
      </div>
      <div className="stat-grid">
        {[...Array(5)].map((_, i) => (
          <div key={i} className="stat-card" style={{ height: 96 }}>
            <div className="skeleton" style={{ width: 46, height: 46, borderRadius: 10 }} />
            <div style={{ flex: 1 }}>
              <div className="skeleton" style={{ height: 26, width: 60, marginBottom: 8 }} />
              <div className="skeleton" style={{ height: 14, width: 100 }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  )

  return (
    <div className="slide-up">
      {/* Header */}
      <div className="page-header">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <p style={{ fontSize: 13, color: 'var(--t2)', marginTop: 3 }}>
            Real-time crowd density overview
          </p>
        </div>
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => fetchDashboard(true)}
          disabled={refreshing}
        >
          <RefreshCw size={13} className={refreshing ? 'spin' : ''} />
          {refreshing ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {/* Stats */}
      <div className="stat-grid">
        <StatCard icon={Camera} label="Total Cameras" value={data?.totalCameras ?? 0}
          color="#2563EB" sub={`${data?.monitoringCameras ?? 0} monitoring`} />
        <StatCard icon={Users} label="People Detected" value={data?.totalPeopleCount ?? 0}
          color="#10B981" sub="across all cameras" />
        <StatCard icon={Activity} label="Avg Occupancy" value={`${(data?.averageOccupancy ?? 0).toFixed(1)}%`}
          color="#F59E0B" sub="last 1 hour" />
        <StatCard icon={Bell} label="Active Alerts" value={data?.unacknowledgedAlerts ?? 0}
          color="#EF4444" sub="unacknowledged" />
        <StatCard icon={TrendingUp} label="Active Cameras" value={data?.activeCameras ?? 0}
          color="#7C3AED" sub="online now" />
      </div>

      {/* Charts row */}
      <div className="grid-2" style={{ marginBottom: 22 }}>
        <div className="card card-pad">
          <div className="card-title">Today's Hourly Trend</div>
          {hourlyData.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={hourlyData}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="hour" tick={{ fontSize: 11, fill: 'var(--t3)' }} />
                <YAxis tick={{ fontSize: 11, fill: 'var(--t3)' }} />
                <Tooltip content={<CustomTooltip />} />
                <Line type="monotone" dataKey="People" stroke="#2563EB" strokeWidth={2.5} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="empty" style={{ height: 220 }}>
              <Activity size={32} />
              <p>No data yet</p>
              <span>Start monitoring cameras to see trends</span>
            </div>
          )}
        </div>

        <div className="card card-pad">
          <div className="card-title">Crowd Level Distribution</div>
          {crowdPie.length > 0 ? (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={crowdPie} cx="50%" cy="50%" innerRadius={58} outerRadius={90} dataKey="value" nameKey="name">
                  {crowdPie.map(e => <Cell key={e.name} fill={LEVEL_COLORS[e.name] || '#94A3B8'} />)}
                </Pie>
                <Tooltip content={<CustomTooltip />} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="empty" style={{ height: 220 }}>
              <TrendingUp size={32} />
              <p>No data yet</p>
            </div>
          )}
        </div>
      </div>

      {/* Camera status + recent alerts */}
      <div className="grid-2">
        <div className="card card-pad">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
            <div className="card-title" style={{ marginBottom: 0 }}>Camera Status</div>
            <button className="btn btn-ghost btn-sm" onClick={() => navigate('/cameras')} style={{ gap: 4 }}>
              View all <ArrowRight size={13} />
            </button>
          </div>
          {data?.recentCameraData?.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {data.recentCameraData.slice(0, 5).map(cam => (
                <div key={cam.id}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div style={{
                        width: 7, height: 7, borderRadius: '50%', flexShrink: 0,
                        background: cam.status === 'MONITORING' ? '#10B981' : cam.status === 'ACTIVE' ? '#2563EB' : '#94A3B8'
                      }} />
                      <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--t1)' }}>{cam.cameraName}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ fontSize: 12, color: 'var(--t3)' }}>{cam.currentPeopleCount}/{cam.maximumCapacity}</span>
                      <span className={`level-badge level-${cam.currentCrowdLevel}`}>
                        {(cam.currentOccupancy || 0).toFixed(0)}%
                      </span>
                    </div>
                  </div>
                  <OccBar pct={cam.currentOccupancy || 0} level={cam.currentCrowdLevel} />
                </div>
              ))}
            </div>
          ) : (
            <div className="empty" style={{ padding: '30px 0' }}>
              <Camera size={28} /><p>No cameras configured</p>
            </div>
          )}
        </div>

        <div className="card card-pad">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
            <div className="card-title" style={{ marginBottom: 0 }}>Recent Alerts</div>
            <button className="btn btn-ghost btn-sm" onClick={() => navigate('/alerts')} style={{ gap: 4 }}>
              View all <ArrowRight size={13} />
            </button>
          </div>
          {data?.recentAlerts?.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {data.recentAlerts.slice(0, 5).map(a => (
                <div key={a.id} style={{
                  display: 'flex', alignItems: 'flex-start', gap: 10,
                  padding: '10px 12px', background: 'var(--surface-2)',
                  borderRadius: 'var(--r-md)', border: '1px solid var(--border)',
                }}>
                  <AlertTriangle size={15} color={LEVEL_COLORS[a.alertType] || '#F59E0B'} style={{ flexShrink: 0, marginTop: 1 }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--t1)', marginBottom: 2 }}>{a.cameraName}</div>
                    <div style={{ fontSize: 12, color: 'var(--t2)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {a.message}
                    </div>
                  </div>
                  <span className={`level-badge level-${a.alertType}`}>{a.alertType}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty" style={{ padding: '30px 0' }}>
              <Bell size={28} /><p>No active alerts</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
