import { useState, useEffect } from 'react'
import { FileText, Download, FileSpreadsheet, Calendar } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'

export default function ReportsPage() {
  const [cameras, setCameras] = useState([])
  const [form, setForm] = useState({
    cameraId: '',
    startDate: new Date(Date.now() - 7*86400000).toISOString().slice(0,16),
    endDate: new Date().toISOString().slice(0,16)
  })
  const [loading, setLoading] = useState({ pdf:false, excel:false })
  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }))

  useEffect(() => { api.get('/cameras').then(r => setCameras(r.data.data||[])).catch(()=>{}) }, [])

  const download = async fmt => {
    const start = new Date(form.startDate).toISOString()
    const end   = new Date(form.endDate).toISOString()
    if (new Date(start) >= new Date(end)) { toast.error('Start date must be before end date'); return }
    setLoading(l => ({ ...l, [fmt]:true }))
    try {
      const params = new URLSearchParams({ start, end })
      if (form.cameraId) params.set('cameraId', form.cameraId)
      const res = await api.get(`/reports/${fmt}?${params}`, { responseType:'blob' })
      const url = URL.createObjectURL(new Blob([res.data]))
      const a = document.createElement('a')
      a.href = url; a.download = `crowd_report_${new Date().toISOString().slice(0,10)}.${fmt==='excel'?'xlsx':'pdf'}`
      document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url)
      toast.success(`${fmt.toUpperCase()} report downloaded`)
    } catch { toast.error('Report generation failed. Please try again.') }
    finally { setLoading(l => ({ ...l, [fmt]:false })) }
  }

  const presets = [
    { label:'Today', days:0 }, { label:'Last 7 Days', days:7 },
    { label:'Last 30 Days', days:30 }, { label:'Last 90 Days', days:90 }
  ]
  const applyPreset = days => {
    const end = new Date(); const start = new Date(Date.now()-days*86400000)
    setForm(f => ({ ...f, startDate:start.toISOString().slice(0,16), endDate:end.toISOString().slice(0,16) }))
  }

  return (
    <div className="slide-up">
      <div className="page-header">
        <div>
          <h1 className="page-title">Reports</h1>
          <p style={{ fontSize:13, color:'var(--t2)', marginTop:3 }}>Generate and download crowd density reports</p>
        </div>
      </div>

      <div className="grid-2" style={{ alignItems:'start' }}>
        <div className="card card-pad">
          <div className="card-title">Report Configuration</div>
          <div style={{ display:'flex', flexDirection:'column', gap:16 }}>
            <div>
              <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Camera</label>
              <select className="form-control" value={form.cameraId} onChange={set('cameraId')}>
                <option value="">All Cameras</option>
                {cameras.map(c => <option key={c.id} value={c.id}>{c.cameraName} — {c.locationName}</option>)}
              </select>
            </div>
            <div>
              <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Quick Presets</label>
              <div style={{ display:'flex', gap:8, flexWrap:'wrap' }}>
                {presets.map(({ label, days }) => (
                  <button key={label} className="btn btn-secondary btn-sm" onClick={() => applyPreset(days)}>
                    <Calendar size={12}/> {label}
                  </button>
                ))}
              </div>
            </div>
            <div className="grid-2">
              <div>
                <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Start Date</label>
                <input className="form-control" type="datetime-local" value={form.startDate} onChange={set('startDate')}/>
              </div>
              <div>
                <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>End Date</label>
                <input className="form-control" type="datetime-local" value={form.endDate} onChange={set('endDate')}/>
              </div>
            </div>
            <div style={{ display:'flex', gap:12 }}>
              <button className="btn btn-primary" style={{ flex:1, justifyContent:'center' }} onClick={() => download('pdf')} disabled={loading.pdf}>
                {loading.pdf ? <><span className="spinner" style={{width:14,height:14}}/> Generating…</> : <><FileText size={15}/> PDF Report</>}
              </button>
              <button className="btn btn-success" style={{ flex:1, justifyContent:'center' }} onClick={() => download('excel')} disabled={loading.excel}>
                {loading.excel ? <><span className="spinner" style={{width:14,height:14}}/> Generating…</> : <><FileSpreadsheet size={15}/> Excel Report</>}
              </button>
            </div>
          </div>
        </div>

        <div style={{ display:'flex', flexDirection:'column', gap:16 }}>
          {[
            { icon:FileText, color:'#EF4444', bg:'#FEF2F2', title:'PDF Report', sub:'Professional printable format',
              items:['Crowd data summary table','Alert history with timestamps','Camera information & capacity','Occupancy percentages','Crowd level classifications'] },
            { icon:FileSpreadsheet, color:'#16A34A', bg:'#F0FDF4', title:'Excel Report', sub:'Data analysis ready format',
              items:['Crowd Data sheet with all records','Alerts sheet with full history','Sortable & filterable columns','Up to 10,000 data rows','Auto-sized columns'] },
          ].map(({ icon:Icon, color, bg, title, sub, items }) => (
            <div key={title} className="card card-pad">
              <div style={{ display:'flex', alignItems:'center', gap:14, marginBottom:14 }}>
                <div style={{ width:44, height:44, borderRadius:12, background:bg, display:'flex', alignItems:'center', justifyContent:'center' }}>
                  <Icon size={22} color={color}/>
                </div>
                <div>
                  <div style={{ fontWeight:600, fontSize:15, color:'var(--t1)' }}>{title}</div>
                  <div style={{ fontSize:12, color:'var(--t3)' }}>{sub}</div>
                </div>
              </div>
              <ul style={{ paddingLeft:18, color:'var(--t2)', fontSize:13, lineHeight:2 }}>
                {items.map(i => <li key={i} style={{ listStyle:'disc' }}>{i}</li>)}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
