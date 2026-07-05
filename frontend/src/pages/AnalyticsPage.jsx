import { useState, useEffect } from 'react'
import { LineChart, Line, BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { RefreshCw, TrendingUp, BarChart2, Clock } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'

const LEVEL_COLORS = { LOW:'#10B981', MEDIUM:'#F59E0B', HIGH:'#F97316', CRITICAL:'#EF4444', OVERCROWDED:'#7C3AED' }

const Tip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  return (
    <div style={{ background:'var(--surface)', border:'1px solid var(--border)', borderRadius:10, padding:'10px 14px', boxShadow:'var(--sh-md)', fontSize:13 }}>
      <div style={{ fontWeight:600, color:'var(--t1)', marginBottom:4 }}>{label}</div>
      {payload.map(p => <div key={p.name} style={{ color:p.color||'var(--t2)' }}>{p.name}: <strong>{typeof p.value === 'number' ? p.value.toFixed(1) : p.value}</strong></div>)}
    </div>
  )
}

export default function AnalyticsPage() {
  const [cameras, setCameras] = useState([])
  const [cam, setCam] = useState('')
  const [period, setPeriod] = useState('daily')
  const [data, setData] = useState(null)
  const [peaks, setPeaks] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api.get('/cameras').then(r => {
      const list = r.data.data || []
      setCameras(list)
      if (list.length) setCam(list[0].id.toString())
    }).catch(() => {})
  }, [])

  useEffect(() => { if (cam) fetch() }, [cam, period])

  const fetch = async () => {
    setLoading(true)
    try {
      const p = cam ? `?cameraId=${cam}` : ''
      const [ar, pr] = await Promise.allSettled([
        api.get(`/analytics/${period}${p}`),
        cam ? api.get(`/analytics/peak-hours?cameraId=${cam}`) : Promise.resolve(null)
      ])
      if (ar.status === 'fulfilled') setData(ar.value.data.data)
      if (pr.status === 'fulfilled' && pr.value) setPeaks(pr.value.data.data)
    } catch { toast.error('Unable to fetch data') }
    finally { setLoading(false) }
  }

  const hourly = data?.hourlyTrend?.map(h => ({ hour:`${h.hour}:00`, Avg: Math.round(h.avgCount||0) })) || []
  const levelData = data?.crowdLevelDistribution ? Object.entries(data.crowdLevelDistribution).map(([k,v]) => ({ name:k, value:Number(v) })) : []
  const peakData = peaks?.peakHours?.map(h => ({ hour:`${h.hour}:00`, Avg: Math.round(h.avgCount||0) })) || []
  const selCam = cameras.find(c => c.id.toString() === cam)

  return (
    <div className="slide-up">
      <div className="page-header">
        <div>
          <h1 className="page-title">Analytics</h1>
          <p style={{ fontSize:13, color:'var(--t2)', marginTop:3 }}>Occupancy trends and crowd insights</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={fetch} disabled={loading}>
          <RefreshCw size={13} className={loading?'spin':''}/> Refresh
        </button>
      </div>

      {/* Controls */}
      <div style={{ display:'flex', gap:12, marginBottom:24, flexWrap:'wrap', alignItems:'center' }}>
        <select className="form-control" style={{ maxWidth:220 }} value={cam} onChange={e => setCam(e.target.value)}>
          <option value="">All Cameras</option>
          {cameras.map(c => <option key={c.id} value={c.id}>{c.cameraName}</option>)}
        </select>
        <div style={{ display:'flex', gap:4, background:'var(--surface)', border:'1px solid var(--border)', borderRadius:'var(--r-md)', padding:4 }}>
          {['daily','weekly','monthly'].map(p => (
            <button key={p} onClick={() => setPeriod(p)} style={{
              padding:'5px 16px', borderRadius:'var(--r-sm)', fontSize:13, fontWeight:500,
              background: period===p ? 'var(--primary)' : 'transparent',
              color: period===p ? '#fff' : 'var(--t2)',
              border:'none', cursor:'pointer', transition:'all .15s', textTransform:'capitalize'
            }}>{p}</button>
          ))}
        </div>
      </div>

      {/* Summary stats */}
      {data && (
        <div className="stat-grid" style={{ marginBottom:22 }}>
          {[
            { icon:TrendingUp, label:'Avg People', value: Math.round(data.averagePeopleCount||0), color:'#2563EB' },
            { icon:BarChart2, label:'Peak Count', value: data.maxPeopleCount||0, color:'#10B981' },
            { icon:Clock, label:'Data Points', value: data.totalRecords||0, color:'#F59E0B' },
            selCam && { icon:TrendingUp, label:'Avg Occupancy', color:'#7C3AED',
              value: selCam.maximumCapacity > 0 ? `${((data.averagePeopleCount/selCam.maximumCapacity)*100).toFixed(1)}%` : 'N/A' },
          ].filter(Boolean).map(({ icon:Icon, label, value, color }) => (
            <div key={label} className="stat-card">
              <div className="stat-icon" style={{ background:color+'18' }}><Icon size={20} color={color}/></div>
              <div><div className="stat-value">{value}</div><div className="stat-label">{label}</div></div>
            </div>
          ))}
        </div>
      )}

      {loading ? (
        <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:18 }}>
          {[...Array(2)].map((_,i) => <div key={i} className="card card-pad"><div className="skeleton" style={{ height:240 }}/></div>)}
        </div>
      ) : (
        <>
          <div className="grid-2" style={{ marginBottom:18 }}>
            <div className="card card-pad">
              <div className="card-title">Hourly People Count</div>
              {hourly.length > 0 ? (
                <ResponsiveContainer width="100%" height={240}>
                  <LineChart data={hourly}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)"/>
                    <XAxis dataKey="hour" tick={{ fontSize:11, fill:'var(--t3)' }}/>
                    <YAxis tick={{ fontSize:11, fill:'var(--t3)' }}/>
                    <Tooltip content={<Tip/>}/>
                    <Line type="monotone" dataKey="Avg" stroke="#2563EB" strokeWidth={2.5} dot={{ r:3, fill:'#2563EB' }}/>
                  </LineChart>
                </ResponsiveContainer>
              ) : <div className="empty" style={{ height:240 }}><BarChart2 size={32}/><p>No data for this period</p></div>}
            </div>
            <div className="card card-pad">
              <div className="card-title">Crowd Level Distribution</div>
              {levelData.some(d => d.value > 0) ? (
                <ResponsiveContainer width="100%" height={240}>
                  <PieChart>
                    <Pie data={levelData} cx="50%" cy="50%" outerRadius={90} dataKey="value" nameKey="name"
                      label={({ name, percent }) => `${name} ${(percent*100).toFixed(0)}%`} labelLine={false}>
                      {levelData.map(e => <Cell key={e.name} fill={LEVEL_COLORS[e.name]||'#94A3B8'}/>)}
                    </Pie>
                    <Tooltip content={<Tip/>}/><Legend wrapperStyle={{ fontSize:12 }}/>
                  </PieChart>
                </ResponsiveContainer>
              ) : <div className="empty" style={{ height:240 }}><TrendingUp size={32}/><p>No level data yet</p></div>}
            </div>
          </div>
          {peakData.length > 0 && (
            <div className="card card-pad">
              <div className="card-title">Peak Hours (Last 7 Days)</div>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={peakData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)"/>
                  <XAxis dataKey="hour" tick={{ fontSize:11, fill:'var(--t3)' }}/>
                  <YAxis tick={{ fontSize:11, fill:'var(--t3)' }}/>
                  <Tooltip content={<Tip/>}/>
                  <Bar dataKey="Avg" fill="#F59E0B" radius={[4,4,0,0]}/>
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </>
      )}
    </div>
  )
}
