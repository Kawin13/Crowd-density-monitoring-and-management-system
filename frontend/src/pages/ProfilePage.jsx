import { useState } from 'react'
import { Lock, Save, User } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'
import { parseServerDate } from '../utils/date'

const ROLE_COLOR = { ADMIN:'#2563EB', OPERATOR:'#F59E0B', VIEWER:'#10B981' }

export default function ProfilePage() {
  const { user } = useAuth()
  const [pw, setPw] = useState({ currentPassword:'', newPassword:'', confirm:'' })
  const [saving, setSaving] = useState(false)
  const set = k => e => setPw(p => ({ ...p, [k]:e.target.value }))

  const changePw = async e => {
    e.preventDefault()
    if (pw.newPassword.length < 8) { toast.error('Password must be at least 8 characters'); return }
    if (pw.newPassword !== pw.confirm) { toast.error('Passwords do not match'); return }
    setSaving(true)
    try {
      await api.put('/users/me/change-password', { currentPassword:pw.currentPassword, newPassword:pw.newPassword })
      toast.success('Password changed successfully')
      setPw({ currentPassword:'', newPassword:'', confirm:'' })
    } catch { toast.error('Incorrect current password') }
    finally { setSaving(false) }
  }

  const color = ROLE_COLOR[user?.role] || '#2563EB'

  return (
    <div className="slide-up">
      <div className="page-header">
        <h1 className="page-title">My Profile</h1>
      </div>

      <div style={{ maxWidth:640, display:'flex', flexDirection:'column', gap:18 }}>
        {/* Profile card */}
        <div className="card card-pad">
          <div style={{ display:'flex', alignItems:'center', gap:20, marginBottom:24 }}>
            <div style={{
              width:72, height:72, borderRadius:'50%', flexShrink:0,
              background:`linear-gradient(135deg,${color},${color}88)`,
              display:'flex', alignItems:'center', justifyContent:'center',
              fontSize:28, fontWeight:700, color:'#fff',
              boxShadow:`0 4px 14px ${color}44`,
            }}>
              {(user?.fullName||user?.username||'U')[0].toUpperCase()}
            </div>
            <div>
              <div style={{ fontSize:20, fontWeight:700, color:'var(--t1)' }}>{user?.fullName||user?.username}</div>
              <div style={{ fontSize:13, color:'var(--t3)', marginTop:2 }}>@{user?.username}</div>
              <span style={{ display:'inline-block', marginTop:8, background:`${color}18`, color, padding:'2px 12px', borderRadius:99, fontSize:11, fontWeight:600 }}>
                {user?.role}
              </span>
            </div>
          </div>

          <div style={{ display:'flex', flexDirection:'column', gap:1 }}>
            {[
              ['Email',          user?.email],
              ['Account Status', user?.isActive ? 'Active' : 'Inactive'],
              ['Member Since',   user?.createdAt ? parseServerDate(user.createdAt).toLocaleDateString('en-GB',{day:'2-digit',month:'short',year:'numeric'}) : '—'],
            ].map(([label, value]) => (
              <div key={label} style={{
                display:'flex', justifyContent:'space-between', alignItems:'center',
                padding:'12px 0', borderBottom:'1px solid var(--border)'
              }}>
                <span style={{ fontSize:13, color:'var(--t2)' }}>{label}</span>
                <span style={{ fontSize:13, fontWeight:500, color:'var(--t1)' }}>{value}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Change password */}
        <div className="card card-pad">
          <div style={{ display:'flex', alignItems:'center', gap:10, marginBottom:20 }}>
            <div style={{ width:36, height:36, borderRadius:10, background:'#EFF6FF', display:'flex', alignItems:'center', justifyContent:'center' }}>
              <Lock size={17} color="var(--primary)"/>
            </div>
            <span style={{ fontSize:15, fontWeight:600, color:'var(--t1)' }}>Change Password</span>
          </div>
          <form onSubmit={changePw} style={{ display:'flex', flexDirection:'column', gap:14 }}>
            {[
              ['Current Password','currentPassword','Enter current password'],
              ['New Password',    'newPassword',    'Min 8 characters'],
              ['Confirm Password','confirm',        'Repeat new password'],
            ].map(([label, key, ph]) => (
              <div key={key}>
                <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>{label}</label>
                <input className="form-control" type="password" placeholder={ph} value={pw[key]} onChange={set(key)}/>
              </div>
            ))}
            <div style={{ display:'flex', justifyContent:'flex-end', marginTop:4 }}>
              <button type="submit" className="btn btn-primary" disabled={saving}>
                <Save size={15}/> {saving ? 'Saving…' : 'Update Password'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  )
}
