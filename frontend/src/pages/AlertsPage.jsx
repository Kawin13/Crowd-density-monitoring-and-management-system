import { useState, useEffect } from 'react'
import { Bell, Check, CheckCheck, RefreshCw, AlertTriangle, Filter } from 'lucide-react'
import toast from 'react-hot-toast'
import { format } from 'date-fns'
import api from '../services/api'
import { parseServerDate } from '../utils/date'

const LEVEL_COLORS = { LOW:'#10B981', MEDIUM:'#F59E0B', HIGH:'#F97316', CRITICAL:'#EF4444', OVERCROWDED:'#7C3AED' }
const PAGE_SIZE = 20

export default function AlertsPage() {
  const [alerts, setAlerts] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('ALL')
  const [page, setPage] = useState(0)
  const [total, setTotal] = useState(0)

  const fetchAlerts = async () => {
    setLoading(true)
    try {
      const r = await api.get(`/alerts?page=${page}&size=${PAGE_SIZE}`)
      setAlerts(r.data.data?.content || [])
      setTotal(r.data.data?.totalElements || 0)
    } catch { toast.error('Unable to fetch data') }
    finally { setLoading(false) }
  }

  useEffect(() => { fetchAlerts() }, [page])

  const acknowledge = async id => {
    try { await api.put(`/alerts/${id}/acknowledge`); toast.success('Alert acknowledged'); fetchAlerts() }
    catch { toast.error('Action failed') }
  }

  const acknowledgeAll = async () => {
    if (!confirm('Acknowledge all active alerts?')) return
    try { await api.put('/alerts/acknowledge-all'); toast.success('All alerts acknowledged'); fetchAlerts() }
    catch { toast.error('Action failed') }
  }

  const unacked = alerts.filter(a => !a.isAcknowledged).length
  const FILTERS = ['ALL','ACTIVE','OVERCROWDED','CRITICAL','HIGH','MEDIUM','LOW']
  const filtered = filter === 'ALL' ? alerts
    : filter === 'ACTIVE' ? alerts.filter(a => !a.isAcknowledged)
    : alerts.filter(a => a.alertType === filter)

  const summary = ['CRITICAL','HIGH','MEDIUM','OVERCROWDED'].map(t => ({
    type: t, count: alerts.filter(a => a.alertType === t && !a.isAcknowledged).length
  }))

  return (
    <div className="slide-up">
      <div className="page-header">
        <div>
          <h1 className="page-title">Alerts</h1>
          <p style={{ fontSize:13, color:'var(--t2)', marginTop:3 }}>{unacked} unacknowledged alert{unacked !== 1 ? 's' : ''}</p>
        </div>
        <div style={{ display:'flex', gap:10 }}>
          <button className="btn btn-secondary btn-sm" onClick={fetchAlerts}><RefreshCw size={13}/> Refresh</button>
          {unacked > 0 && (
            <button className="btn btn-primary btn-sm" onClick={acknowledgeAll}>
              <CheckCheck size={13}/> Acknowledge All
            </button>
          )}
        </div>
      </div>

      {/* Summary pills */}
      <div style={{ display:'flex', gap:12, marginBottom:22, flexWrap:'wrap' }}>
        {summary.map(({ type, count }) => (
          <div key={type} style={{
            background:'var(--surface)', border:`1px solid ${LEVEL_COLORS[type]}33`,
            borderRadius:'var(--r-lg)', padding:'12px 20px', minWidth:110, textAlign:'center',
            boxShadow:'var(--sh-sm)',
          }}>
            <div style={{ fontSize:24, fontWeight:700, color: LEVEL_COLORS[type] }}>{count}</div>
            <div style={{ fontSize:11, color:'var(--t3)', marginTop:2, fontWeight:500 }}>{type}</div>
          </div>
        ))}
      </div>

      {/* Filter tabs */}
      <div style={{ display:'flex', gap:8, marginBottom:18, flexWrap:'wrap' }}>
        {FILTERS.map(f => (
          <button key={f} onClick={() => setFilter(f)} style={{
            padding:'5px 14px', borderRadius:'var(--r-full)', fontSize:12, fontWeight:500,
            border:'1px solid var(--border)', cursor:'pointer',
            background: filter === f ? 'var(--primary)' : 'var(--surface)',
            color: filter === f ? '#fff' : 'var(--t2)', transition:'all .15s',
          }}>{f}</button>
        ))}
      </div>

      <div className="card" style={{ overflow:'hidden', padding:0 }}>
        {loading ? (
          <div style={{ padding:24 }}>
            {[...Array(5)].map((_,i) => (
              <div key={i} style={{ display:'flex', gap:12, padding:'14px 0', borderBottom:'1px solid var(--border)' }}>
                <div className="skeleton" style={{ width:70, height:22 }} />
                <div className="skeleton" style={{ width:120, height:22 }} />
                <div className="skeleton" style={{ flex:1, height:22 }} />
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty"><Bell size={40}/><p>No alerts found</p><span>System is operating normally</span></div>
        ) : (
          <div className="tw">
            <table>
              <thead>
                <tr>
                  <th>Level</th><th>Camera</th><th>Location</th>
                  <th>People</th><th>Occupancy</th><th>Time</th>
                  <th>Status</th><th>Action</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(a => (
                  <tr key={a.id} style={{ opacity: a.isAcknowledged ? .6 : 1 }}>
                    <td>
                      <div style={{ display:'flex', alignItems:'center', gap:6 }}>
                        <AlertTriangle size={13} color={LEVEL_COLORS[a.alertType]} />
                        <span className={`level-badge level-${a.alertType}`}>{a.alertType}</span>
                      </div>
                    </td>
                    <td style={{ fontWeight:500 }}>{a.cameraName}</td>
                    <td style={{ color:'var(--t2)' }}>{a.locationName}</td>
                    <td style={{ fontWeight:600 }}>{a.peopleCount}</td>
                    <td>
                      <span style={{ fontWeight:600, color: LEVEL_COLORS[a.alertType] }}>
                        {Number(a.occupancyPercentage).toFixed(1)}%
                      </span>
                    </td>
                    <td style={{ fontSize:12, color:'var(--t3)', whiteSpace:'nowrap' }}>
                      {a.createdAt ? format(parseServerDate(a.createdAt), 'dd MMM HH:mm') : '—'}
                    </td>
                    <td>
                      {a.isAcknowledged
                        ? <span style={{ fontSize:12, color:'var(--success)', display:'flex', alignItems:'center', gap:4 }}><Check size={12}/> Done</span>
                        : <span style={{ fontSize:12, color:'var(--danger)', fontWeight:600 }}>Active</span>
                      }
                    </td>
                    <td>
                      {!a.isAcknowledged && (
                        <button className="btn btn-secondary btn-sm" onClick={() => acknowledge(a.id)}>
                          <Check size={12}/> Ack
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {total > PAGE_SIZE && (
        <div style={{ display:'flex', alignItems:'center', justifyContent:'center', gap:12, marginTop:18 }}>
          <button className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage(p => p-1)}>Previous</button>
          <span style={{ fontSize:13, color:'var(--t2)' }}>Page {page+1} of {Math.ceil(total/PAGE_SIZE)}</span>
          <button className="btn btn-secondary btn-sm" disabled={(page+1)*PAGE_SIZE >= total} onClick={() => setPage(p => p+1)}>Next</button>
        </div>
      )}
    </div>
  )
}
