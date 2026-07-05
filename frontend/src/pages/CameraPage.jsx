import { useState, useEffect } from 'react'
import { Plus, Edit2, Trash2, Play, Square, Camera, MapPin, Users, Wifi, RefreshCw, Zap } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'

const STATUS_DOT = { ACTIVE:'#10B981', MONITORING:'#2563EB', INACTIVE:'#94A3B8', ERROR:'#EF4444' }
const LEVEL_COLORS = { LOW:'#10B981', MEDIUM:'#F59E0B', HIGH:'#F97316', CRITICAL:'#EF4444', OVERCROWDED:'#7C3AED' }
const TYPES = ['MOBILE','CCTV','VIDEO_UPLOAD']

// Supported stream URL formats:
//   rtsp://...                    — CCTV IP cameras
//   http(s)://ip:port[/video|/stream] — IP Webcam, DroidCam, Iriun Webcam (mobile), MJPEG
//   a bare number ("0", "1", ...) — USB webcam device index
const STREAM_URL_PATTERN = /^(rtsp:\/\/\S+|https?:\/\/\S+|\d+)$/i

function isSupportedStreamUrl(url) {
  return STREAM_URL_PATTERN.test((url || '').trim())
}

function CameraModal({ camera, onClose, onSave }) {
  const [form, setForm] = useState({
    cameraName: camera?.cameraName || '',
    cameraType: camera?.cameraType || 'CCTV',
    locationName: camera?.locationName || '',
    maximumCapacity: camera?.maximumCapacity || 100,
    streamUrl: camera?.streamUrl || '',
    description: camera?.description || ''
  })
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }))

  const handleTestConnection = async () => {
    const url = form.streamUrl.trim()
    if (!url) { toast.error('Enter a stream URL first'); return }
    if (!isSupportedStreamUrl(url)) {
      toast.error('Unsupported URL. Use rtsp://, http://, or https:// (e.g. http://192.168.x.x:8080/video)')
      return
    }
    setTesting(true)
    try {
      const res = await api.get('/monitoring/validate-stream', { params: { streamUrl: url } })
      const result = res.data?.data
      if (result?.valid) toast.success(result.message || 'Stream is reachable')
      else toast.error(result?.message || 'Could not connect to stream')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Connection test failed')
    } finally {
      setTesting(false)
    }
  }

  const handleSubmit = async e => {
    e.preventDefault()
    if (!form.cameraName || !form.locationName || !form.maximumCapacity) { toast.error('Fill all required fields'); return }
    if (form.streamUrl.trim() && !isSupportedStreamUrl(form.streamUrl)) {
      toast.error('Unsupported stream URL. Use rtsp://, http://, or https://')
      return
    }
    setSaving(true)
    try {
      const payload = { ...form, maximumCapacity: parseInt(form.maximumCapacity) }
      if (camera?.id) { await api.put(`/cameras/${camera.id}`, payload); toast.success('Camera updated') }
      else { await api.post('/cameras', payload); toast.success('Camera added') }
      onSave()
    } catch (err) { toast.error(err.response?.data?.message || 'Failed to save') }
    finally { setSaving(false) }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <span className="modal-title">{camera?.id ? 'Edit Camera' : 'Add Camera'}</span>
          <button className="btn-icon sm" onClick={onClose}>✕</button>
        </div>
        <form onSubmit={handleSubmit} style={{ display:'flex', flexDirection:'column', gap:14 }}>
          <div>
            <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Camera Name *</label>
            <input className="form-control" placeholder="e.g. Auditorium Cam 1" value={form.cameraName} onChange={set('cameraName')} />
          </div>
          <div className="grid-2">
            <div>
              <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Type *</label>
              <select className="form-control" value={form.cameraType} onChange={set('cameraType')}>
                {TYPES.map(t => <option key={t} value={t}>{t.replace('_',' ')}</option>)}
              </select>
            </div>
            <div>
              <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Max Capacity *</label>
              <input className="form-control" type="number" min="1" value={form.maximumCapacity} onChange={set('maximumCapacity')} />
            </div>
          </div>
          <div>
            <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Location *</label>
            <input className="form-control" placeholder="e.g. College Auditorium" value={form.locationName} onChange={set('locationName')} />
          </div>
          <div>
            <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Stream URL</label>
            <div style={{ display:'flex', gap:8 }}>
              <input className="form-control" style={{ flex:1 }}
                placeholder={form.cameraType === 'MOBILE' ? 'http://192.168.x.x:8080/video' : 'rtsp://ip:554/stream'}
                value={form.streamUrl} onChange={set('streamUrl')} />
              <button type="button" className="btn btn-secondary" onClick={handleTestConnection} disabled={testing}
                style={{ flexShrink:0, gap:6 }}>
                {testing ? <RefreshCw size={13} className="spin"/> : <Zap size={13}/>}
                {testing ? 'Testing…' : 'Test'}
              </button>
            </div>
            <div style={{ fontSize:11, color:'var(--t3)', marginTop:6 }}>
              Supports rtsp:// (CCTV), http:// / https:// (IP Webcam, DroidCam, Iriun Webcam, MJPEG)
            </div>
          </div>
          <div>
            <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Description</label>
            <textarea className="form-control" style={{ height:'auto', padding:'10px 13px', resize:'vertical' }} rows={2} value={form.description} onChange={set('description')} />
          </div>
          <div style={{ display:'flex', gap:10, justifyContent:'flex-end', marginTop:6 }}>
            <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Save Camera'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function CameraPage() {
  const [cameras, setCameras] = useState([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [editing, setEditing] = useState(null)
  const [filter, setFilter] = useState('ALL')

  const fetchCameras = async () => {
    try { const r = await api.get('/cameras'); setCameras(r.data.data || []) }
    catch { toast.error('Unable to fetch data') }
    finally { setLoading(false) }
  }

  useEffect(() => { fetchCameras() }, [])

  const handleDelete = async id => {
    if (!confirm('Delete this camera and all its data?')) return
    try { await api.delete(`/cameras/${id}`); toast.success('Camera deleted'); fetchCameras() }
    catch { toast.error('Delete failed') }
  }

  const handleToggle = async cam => {
    try {
      if (cam.status === 'MONITORING') { await api.post(`/cameras/${cam.id}/stop`); toast.success('Monitoring stopped') }
      else { await api.post(`/cameras/${cam.id}/start`); toast.success('Monitoring started') }
      fetchCameras()
    } catch { toast.error('Action failed') }
  }

  const FILTERS = ['ALL','MONITORING','ACTIVE','INACTIVE','MOBILE','CCTV','VIDEO_UPLOAD']
  const filtered = filter === 'ALL' ? cameras : cameras.filter(c => c.status === filter || c.cameraType === filter)

  return (
    <div className="slide-up">
      <div className="page-header">
        <div>
          <h1 className="page-title">Camera Management</h1>
          <p style={{ fontSize:13, color:'var(--t2)', marginTop:3 }}>{cameras.length} cameras configured</p>
        </div>
        <div style={{ display:'flex', gap:10 }}>
          <button className="btn btn-secondary btn-sm" onClick={fetchCameras}><RefreshCw size={13} /> Refresh</button>
          <button className="btn btn-primary" onClick={() => { setEditing(null); setShowModal(true) }}>
            <Plus size={15} /> Add Camera
          </button>
        </div>
      </div>

      {/* Filters */}
      <div style={{ display:'flex', gap:8, marginBottom:20, flexWrap:'wrap' }}>
        {FILTERS.map(f => (
          <button key={f} onClick={() => setFilter(f)} style={{
            padding:'5px 14px', borderRadius:'var(--r-full)', fontSize:12, fontWeight:500,
            border:'1px solid var(--border)', cursor:'pointer',
            background: filter === f ? 'var(--primary)' : 'var(--surface)',
            color: filter === f ? '#fff' : 'var(--t2)',
            transition:'all .15s',
          }}>{f.replace('_',' ')}</button>
        ))}
      </div>

      {loading ? (
        <div className="grid-auto">
          {[...Array(4)].map((_,i) => (
            <div key={i} className="card" style={{ padding:20, height:240 }}>
              <div className="skeleton" style={{ height:20, width:'60%', marginBottom:12 }} />
              <div className="skeleton" style={{ height:14, width:'40%', marginBottom:20 }} />
              <div className="skeleton" style={{ height:14, width:'80%', marginBottom:8 }} />
              <div className="skeleton" style={{ height:14, width:'60%', marginBottom:20 }} />
              <div className="skeleton" style={{ height:8, borderRadius:99 }} />
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="card">
          <div className="empty">
            <Camera size={40} /><p>No cameras found</p>
            <span>{filter !== 'ALL' ? 'Try a different filter' : 'Add your first camera to get started'}</span>
            {filter === 'ALL' && (
              <button className="btn btn-primary" style={{ marginTop:8 }} onClick={() => { setEditing(null); setShowModal(true) }}>
                <Plus size={14} /> Add Camera
              </button>
            )}
          </div>
        </div>
      ) : (
        <div className="grid-auto">
          {filtered.map(cam => {
            const occ = cam.currentOccupancy || 0
            const level = cam.currentCrowdLevel || 'LOW'
            const color = LEVEL_COLORS[level] || '#10B981'
            return (
              <div key={cam.id} className="card" style={{ padding:20, display:'flex', flexDirection:'column', gap:14, transition:'box-shadow .2s,transform .2s' }}
                onMouseEnter={e => { e.currentTarget.style.boxShadow='var(--sh-md)'; e.currentTarget.style.transform='translateY(-1px)' }}
                onMouseLeave={e => { e.currentTarget.style.boxShadow='var(--sh-sm)'; e.currentTarget.style.transform='translateY(0)' }}>

                {/* Header */}
                <div style={{ display:'flex', alignItems:'flex-start', justifyContent:'space-between', gap:8 }}>
                  <div style={{ display:'flex', alignItems:'center', gap:10 }}>
                    <div style={{ width:38, height:38, borderRadius:10, background:'#EFF6FF', display:'flex', alignItems:'center', justifyContent:'center' }}>
                      <Camera size={18} color="var(--primary)" />
                    </div>
                    <div>
                      <div style={{ fontWeight:600, fontSize:14, color:'var(--t1)' }}>{cam.cameraName}</div>
                      <div style={{ fontSize:11, color:'var(--t3)', marginTop:1 }}>{cam.cameraType.replace('_',' ')}</div>
                    </div>
                  </div>
                  <div style={{ display:'flex', alignItems:'center', gap:5, flexShrink:0 }}>
                    <div style={{ width:7, height:7, borderRadius:'50%', background: STATUS_DOT[cam.status] || '#94A3B8' }} />
                    <span style={{ fontSize:11, fontWeight:600, color: STATUS_DOT[cam.status] || '#94A3B8' }}>{cam.status}</span>
                  </div>
                </div>

                {/* Info */}
                <div style={{ display:'flex', flexDirection:'column', gap:5 }}>
                  <div style={{ display:'flex', alignItems:'center', gap:6, fontSize:12, color:'var(--t2)' }}>
                    <MapPin size={12} style={{ flexShrink:0 }} /> {cam.locationName}
                  </div>
                  <div style={{ display:'flex', alignItems:'center', gap:6, fontSize:12, color:'var(--t2)' }}>
                    <Users size={12} style={{ flexShrink:0 }} /> Capacity: {cam.maximumCapacity}
                  </div>
                  {cam.streamUrl && (
                    <div style={{ display:'flex', alignItems:'center', gap:6, fontSize:11, color:'var(--t3)', overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>
                      <Wifi size={11} style={{ flexShrink:0 }} /> {cam.streamUrl}
                    </div>
                  )}
                </div>

                {/* Occupancy */}
                <div>
                  <div style={{ display:'flex', justifyContent:'space-between', fontSize:12, marginBottom:5 }}>
                    <span style={{ color:'var(--t2)' }}>Occupancy</span>
                    <span style={{ fontWeight:600, color }}>
                      {cam.currentPeopleCount}/{cam.maximumCapacity} ({occ.toFixed(1)}%)
                    </span>
                  </div>
                  <div className="occ-bar">
                    <div className="occ-fill" style={{ width:`${Math.min(occ,100)}%`, background:color }} />
                  </div>
                  <div style={{ marginTop:7 }}>
                    <span className={`level-badge level-${level}`}>{level}</span>
                  </div>
                </div>

                {/* Actions */}
                <div style={{ display:'flex', gap:8, paddingTop:6, borderTop:'1px solid var(--border)' }}>
                  <button
                    className={`btn btn-sm ${cam.status === 'MONITORING' ? 'btn-danger' : 'btn-success'}`}
                    style={{ flex:1, justifyContent:'center' }}
                    onClick={() => handleToggle(cam)}
                  >
                    {cam.status === 'MONITORING' ? <><Square size={12}/> Stop</> : <><Play size={12}/> Start</>}
                  </button>
                  <button className="btn-icon sm" onClick={() => { setEditing(cam); setShowModal(true) }} title="Edit">
                    <Edit2 size={13} />
                  </button>
                  <button className="btn-icon sm" onClick={() => handleDelete(cam.id)} title="Delete"
                    style={{ color:'var(--danger)' }}
                    onMouseEnter={e => e.currentTarget.style.background='#FEF2F2'}
                    onMouseLeave={e => e.currentTarget.style.background='var(--surface-2)'}>
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showModal && (
        <CameraModal camera={editing} onClose={() => setShowModal(false)}
          onSave={() => { setShowModal(false); fetchCameras() }} />
      )}
    </div>
  )
}
