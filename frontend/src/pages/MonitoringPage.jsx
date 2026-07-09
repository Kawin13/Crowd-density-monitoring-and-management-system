import { useState, useEffect, useRef } from 'react'
import { Upload, Play, Square, RefreshCw, Camera, Activity, AlertTriangle, Wifi } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'
import { useAnalysis } from '../context/AnalysisContext'

const LEVEL_COLORS = { LOW:'#10B981', MEDIUM:'#F59E0B', HIGH:'#F97316', CRITICAL:'#EF4444', OVERCROWDED:'#7C3AED' }

function FeedCard({ camera, latest }) {
  // Global session state (see AnalysisContext) — survives navigating away
  // from and back to Monitoring, since it lives above the router instead
  // of inside this component.
  const { isAnalyzing, startAnalysis, stopAnalysis, syncStatus } = useAnalysis()
  const analyzing = isAnalyzing(camera.id)

  // On first mount of this card, reconcile with the AI service's real
  // session status (covers a hard page reload, where in-memory context
  // state was lost but the backend session may still be running).
  useEffect(() => { syncStatus(camera.id) }, [camera.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const occ = latest?.occupancyPercentage ? Number(latest.occupancyPercentage) : 0
  const level = latest?.crowdLevel || 'LOW'
  const color = LEVEL_COLORS[level] || '#10B981'

  return (
    <div className="card" style={{ overflow:'hidden', display:'flex', flexDirection:'column',
      transition:'box-shadow .2s,transform .2s' }}
      onMouseEnter={e => { e.currentTarget.style.boxShadow='var(--sh-md)'; e.currentTarget.style.transform='translateY(-1px)' }}
      onMouseLeave={e => { e.currentTarget.style.boxShadow='var(--sh-sm)'; e.currentTarget.style.transform='translateY(0)' }}>

      {/* Feed area */}
      <div style={{ position:'relative', background:'#0B1120', aspectRatio:'16/9', display:'flex', alignItems:'center', justifyContent:'center', overflow:'hidden' }}>
        {latest?.frameDataBase64 ? (
          <img src={`data:image/jpeg;base64,${latest.frameDataBase64}`} alt="Live feed"
            style={{ width:'100%', height:'100%', objectFit:'cover' }}/>
        ) : (
          <div style={{ textAlign:'center', color:'#334155' }}>
            <Camera size={32} style={{ margin:'0 auto 8px' }}/>
            <div style={{ fontSize:12 }}>{camera.status==='MONITORING' ? 'Awaiting frames…' : 'Not monitoring'}</div>
          </div>
        )}
        {/* Status badge */}
        <div style={{ position:'absolute', top:8, left:8 }}>
          <span style={{
            background: camera.status==='MONITORING' ? '#10B981' : '#475569',
            color:'#fff', fontSize:10, fontWeight:700, padding:'3px 8px', borderRadius:4
          }}>
            {camera.status==='MONITORING' ? '● LIVE' : camera.status}
          </span>
        </div>
        {level !== 'LOW' && (
          <div style={{ position:'absolute', top:8, right:8 }}>
            <span className={`level-badge level-${level}`}>{level}</span>
          </div>
        )}
      </div>

      {/* Info */}
      <div style={{ padding:'14px 16px', display:'flex', flexDirection:'column', gap:10 }}>
        <div>
          <div style={{ fontWeight:600, fontSize:14, color:'var(--t1)' }}>{camera.cameraName}</div>
          <div style={{ fontSize:12, color:'var(--t3)', marginTop:2 }}>{camera.locationName} · Cap: {camera.maximumCapacity}</div>
        </div>

        <div style={{ display:'flex', gap:10 }}>
          <div style={{ flex:1, background:'var(--surface-2)', borderRadius:'var(--r-md)', padding:'8px 10px', textAlign:'center' }}>
            <div style={{ fontSize:20, fontWeight:700, color:'var(--t1)' }}>{latest?.peopleCount ?? 0}</div>
            <div style={{ fontSize:11, color:'var(--t3)' }}>People</div>
          </div>
          <div style={{ flex:1, background:'var(--surface-2)', borderRadius:'var(--r-md)', padding:'8px 10px', textAlign:'center' }}>
            <div style={{ fontSize:20, fontWeight:700, color }}>{occ.toFixed(1)}%</div>
            <div style={{ fontSize:11, color:'var(--t3)' }}>Occupancy</div>
          </div>
        </div>

        <div className="occ-bar">
          <div className="occ-fill" style={{ width:`${Math.min(occ,100)}%`, background:color }}/>
        </div>

        <div style={{ display:'flex', gap:8 }}>
          <button className="btn btn-secondary btn-sm" style={{ flex:1, justifyContent:'center' }}
            onClick={() => startAnalysis(camera)}>
            {analyzing ? <><span className="spinner" style={{width:12,height:12}}/> Analyzing…</> : <><Activity size={12}/> Analyze Now</>}
          </button>
          <button className="btn btn-secondary btn-sm" style={{ flex:1, justifyContent:'center' }}
            onClick={() => stopAnalysis(camera)} disabled={!analyzing}>
            <Square size={12}/> Stop Analysis
          </button>
        </div>
      </div>
    </div>
  )
}

export default function MonitoringPage() {
  const [cameras, setCameras] = useState([])
  const [latestData, setLatestData] = useState({})
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [selectedCam, setSelectedCam] = useState('')
  const fileRef = useRef()

  const fetchAll = async () => {
    try {
      const r = await api.get('/cameras')
      const cams = r.data.data || []
      setCameras(cams)
      const map = {}
      await Promise.all(cams.map(async c => {
        try { const d = await api.get(`/monitoring/cameras/${c.id}/latest`); map[c.id] = d.data.data } catch {}
      }))
      setLatestData(map)
    } catch { toast.error('Unable to fetch data') }
    finally { setLoading(false) }
  }

  useEffect(() => {
    fetchAll()
    const iv = setInterval(fetchAll, 15000)
    return () => clearInterval(iv)
  }, [])

  const handleVideoUpload = async e => {
    const file = e.target.files[0]
    if (!file || !selectedCam) { toast.error('Select a camera first'); return }
    setUploading(true)
    try {
      const fd = new FormData()
      fd.append('video', file)
      await api.post(`/monitoring/cameras/${selectedCam}/upload-video`, fd, { headers:{ 'Content-Type':'multipart/form-data' } })
      toast.success('Video uploaded — analysis running in background')
    } catch { toast.error('Upload failed. Please try again.') }
    finally { setUploading(false); fileRef.current.value = '' }
  }

  const live = cameras.filter(c => c.status === 'MONITORING')
  const other = cameras.filter(c => c.status !== 'MONITORING')

  return (
    <div className="slide-up">
      <div className="page-header">
        <div>
          <h1 className="page-title">Live Monitoring</h1>
          <p style={{ fontSize:13, color:'var(--t2)', marginTop:3 }}>{live.length} camera{live.length!==1?'s':''} actively monitoring</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={fetchAll}><RefreshCw size={13}/> Refresh</button>
      </div>

      {/* Video upload panel */}
      <div className="card card-pad" style={{ marginBottom:24, display:'flex', alignItems:'center', gap:16, flexWrap:'wrap' }}>
        <div style={{ display:'flex', alignItems:'center', gap:10, flex:1, minWidth:180 }}>
          <div style={{ width:38, height:38, borderRadius:10, background:'#EFF6FF', display:'flex', alignItems:'center', justifyContent:'center' }}>
            <Upload size={18} color="var(--primary)"/>
          </div>
          <div>
            <div style={{ fontWeight:600, fontSize:14, color:'var(--t1)' }}>Upload Video for Analysis</div>
            <div style={{ fontSize:12, color:'var(--t3)' }}>MP4, AVI, MOV supported</div>
          </div>
        </div>
        <select className="form-control" style={{ maxWidth:220 }} value={selectedCam} onChange={e => setSelectedCam(e.target.value)}>
          <option value="">Select camera…</option>
          {cameras.map(c => <option key={c.id} value={c.id}>{c.cameraName}</option>)}
        </select>
        <button className="btn btn-primary" onClick={() => fileRef.current?.click()} disabled={!selectedCam || uploading}>
          {uploading ? <><span className="spinner" style={{width:14,height:14}}/> Uploading…</> : <><Upload size={14}/> Choose Video</>}
        </button>
        <input ref={fileRef} type="file" accept="video/*" style={{ display:'none' }} onChange={handleVideoUpload}/>
      </div>

      {loading ? (
        <div className="grid-auto">
          {[...Array(4)].map((_,i) => (
            <div key={i} className="card" style={{ overflow:'hidden' }}>
              <div className="skeleton" style={{ aspectRatio:'16/9' }}/>
              <div style={{ padding:16 }}>
                <div className="skeleton" style={{ height:16, width:'60%', marginBottom:8 }}/>
                <div className="skeleton" style={{ height:12, width:'40%' }}/>
              </div>
            </div>
          ))}
        </div>
      ) : cameras.length === 0 ? (
        <div className="card"><div className="empty"><Camera size={40}/><p>No cameras configured</p><span>Go to Camera Management to add cameras</span></div></div>
      ) : (
        <>
          {live.length > 0 && (
            <>
              <div style={{ display:'flex', alignItems:'center', gap:8, marginBottom:14 }}>
                <div style={{ width:8, height:8, borderRadius:'50%', background:'#10B981' }}/>
                <span style={{ fontSize:14, fontWeight:600, color:'var(--t1)' }}>Active Monitoring</span>
              </div>
              <div className="grid-auto" style={{ marginBottom:28 }}>
                {live.map(c => <FeedCard key={c.id} camera={c} latest={latestData[c.id]}/>)}
              </div>
            </>
          )}
          {other.length > 0 && (
            <>
              <div style={{ fontSize:14, fontWeight:600, color:'var(--t2)', marginBottom:14 }}>Other Cameras</div>
              <div className="grid-auto">
                {other.map(c => <FeedCard key={c.id} camera={c} latest={latestData[c.id]}/>)}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
