import { useState } from 'react'
import { Save, Sun, Moon, Bell, Info } from 'lucide-react'
import { useTheme } from '../context/ThemeContext'
import toast from 'react-hot-toast'

export default function SettingsPage() {
  const { theme, toggleTheme } = useTheme()
  const [alerts, setAlerts] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('alertPrefs') ||
        '{"medium":true,"high":true,"critical":true,"overcrowded":true}')
    } catch { return { medium:true, high:true, critical:true, overcrowded:true } }
  })

  const save = () => {
    localStorage.setItem('alertPrefs', JSON.stringify(alerts))
    toast.success('Settings saved')
  }

  const Toggle = ({ checked, onChange }) => (
    <button type="button" onClick={() => onChange(!checked)} style={{
      position:'relative', width:44, height:24, borderRadius:12,
      cursor:'pointer', border:'none', flexShrink:0,
      background: checked ? 'var(--primary)' : 'var(--border)',
      transition:'background .2s',
    }}>
      <span style={{
        position:'absolute', top:3, left: checked ? 22 : 2,
        width:18, height:18, borderRadius:'50%', background:'#fff',
        transition:'left .2s', boxShadow:'0 1px 3px rgba(0,0,0,.2)', display:'block',
      }}/>
    </button>
  )

  const Section = ({ icon:Icon, title, children }) => (
    <div className="card card-pad">
      <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:20 }}>
        <div style={{ width:36, height:36, borderRadius:10, background:'#EFF6FF',
          display:'flex', alignItems:'center', justifyContent:'center' }}>
          <Icon size={18} color="var(--primary)"/>
        </div>
        <span style={{ fontSize:15, fontWeight:600, color:'var(--t1)' }}>{title}</span>
      </div>
      {children}
    </div>
  )

  return (
    <div className="slide-up">
      <div className="page-header">
        <h1 className="page-title">Settings</h1>
      </div>

      <div style={{ maxWidth:680, display:'flex', flexDirection:'column', gap:18 }}>

        {/* Appearance */}
        <Section icon={Sun} title="Appearance">
          <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between' }}>
            <div>
              <div style={{ fontWeight:500, color:'var(--t1)' }}>Theme</div>
              <div style={{ fontSize:13, color:'var(--t3)', marginTop:2 }}>
                Currently using <strong>{theme}</strong> mode
              </div>
            </div>
            <button className="btn btn-secondary" onClick={toggleTheme} style={{ gap:8 }}>
              {theme === 'dark' ? <Sun size={15}/> : <Moon size={15}/>}
              Switch to {theme === 'dark' ? 'Light' : 'Dark'}
            </button>
          </div>
        </Section>

        {/* Notifications */}
        <Section icon={Bell} title="Alert Notifications">
          <div style={{ display:'flex', flexDirection:'column', gap:16 }}>
            {[
              { key:'medium',      label:'Medium Alerts',     desc:'26–50% occupancy — Blue notification',   color:'#3B82F6' },
              { key:'high',        label:'High Alerts',       desc:'51–75% occupancy — Orange notification', color:'#F97316' },
              { key:'critical',    label:'Critical Alerts',   desc:'76–100% occupancy — Red notification',   color:'#EF4444' },
              { key:'overcrowded', label:'Overcrowded Alarm', desc:'Above 100% — Red popup with alarm sound',color:'#7C3AED' },
            ].map(({ key, label, desc, color }) => (
              <div key={key} style={{ display:'flex', alignItems:'center', justifyContent:'space-between' }}>
                <div style={{ display:'flex', alignItems:'center', gap:10 }}>
                  <div style={{ width:10, height:10, borderRadius:'50%', background:color, flexShrink:0 }}/>
                  <div>
                    <div style={{ fontWeight:500, fontSize:14, color:'var(--t1)' }}>{label}</div>
                    <div style={{ fontSize:12, color:'var(--t3)' }}>{desc}</div>
                  </div>
                </div>
                <Toggle checked={alerts[key]} onChange={v => setAlerts(a => ({ ...a, [key]:v }))}/>
              </div>
            ))}
          </div>
        </Section>

        {/* About */}
        <Section icon={Info} title="About">
          <div style={{ display:'flex', flexDirection:'column', gap:1 }}>
            {[
              ['Application', 'Crowd Density Monitoring and Management System'],
              ['Version',     '1.0.0'],
              ['Release',     'Stable'],
              ['Developer',   'Project Team'],
              ['Support',     'contact@crowdmonitor.app'],
            ].map(([label, value]) => (
              <div key={label} style={{
                display:'flex', justifyContent:'space-between', alignItems:'center',
                padding:'11px 0', borderBottom:'1px solid var(--border)'
              }}>
                <span style={{ fontSize:13, color:'var(--t2)' }}>{label}</span>
                <span style={{ fontSize:13, fontWeight:500, color:'var(--t1)' }}>{value}</span>
              </div>
            ))}
          </div>
          <p style={{ fontSize:12, color:'var(--t3)', marginTop:14, lineHeight:1.6 }}>
            AI-powered real-time crowd monitoring system that detects and counts people
            for occupancy monitoring and safety management.
          </p>
        </Section>

        <div style={{ display:'flex', justifyContent:'flex-end' }}>
          <button className="btn btn-primary" onClick={save}>
            <Save size={15}/> Save Settings
          </button>
        </div>
      </div>
    </div>
  )
}
