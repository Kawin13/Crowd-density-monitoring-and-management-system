import { useState } from 'react'
import { Link, useSearchParams, useNavigate } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [form, setForm] = useState({ newPassword:'', confirm:'' })
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = async e => {
    e.preventDefault()
    if (form.newPassword.length < 8) { toast.error('Password must be at least 8 characters'); return }
    if (form.newPassword !== form.confirm) { toast.error('Passwords do not match'); return }
    setLoading(true)
    try {
      await api.post('/auth/reset-password', { token, newPassword: form.newPassword })
      toast.success('Password reset successfully!')
      navigate('/login')
    } catch { toast.error('Reset failed. The link may have expired.') }
    finally { setLoading(false) }
  }

  return (
    <div style={{ minHeight:'100vh', background:'var(--bg)', display:'flex', alignItems:'center', justifyContent:'center', padding:24 }}>
      <div style={{ width:'100%', maxWidth:400 }} className="slide-up">
        <div style={{ background:'var(--surface)', border:'1px solid var(--border)', borderRadius:'var(--r-2xl)', padding:36, boxShadow:'var(--sh-xl)' }}>
          <div style={{ textAlign:'center', marginBottom:28 }}>
            <div style={{ width:52, height:52, borderRadius:14, background:'#EFF6FF', display:'inline-flex', alignItems:'center', justifyContent:'center', marginBottom:14 }}>
              <KeyRound size={24} color="var(--primary)" />
            </div>
            <h2 style={{ fontSize:20, fontWeight:700, color:'var(--t1)', marginBottom:6 }}>New Password</h2>
            <p style={{ fontSize:13, color:'var(--t2)' }}>Enter and confirm your new password</p>
          </div>
          {!token ? (
            <p style={{ color:'var(--danger)', textAlign:'center', fontSize:13 }}>
              Invalid reset link. <Link to="/forgot-password" style={{ color:'var(--primary)' }}>Request a new one</Link>.
            </p>
          ) : (
            <form onSubmit={handleSubmit} style={{ display:'flex', flexDirection:'column', gap:14 }}>
              <div>
                <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>New Password</label>
                <input className="form-control" type="password" placeholder="Min 8 characters" value={form.newPassword} onChange={e => setForm(f => ({...f, newPassword:e.target.value}))} autoFocus />
              </div>
              <div>
                <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Confirm Password</label>
                <input className="form-control" type="password" placeholder="Repeat password" value={form.confirm} onChange={e => setForm(f => ({...f, confirm:e.target.value}))} />
              </div>
              <button type="submit" className="btn btn-primary btn-lg" disabled={loading} style={{ width:'100%', justifyContent:'center', marginTop:6 }}>
                {loading ? 'Resetting…' : 'Set New Password'}
              </button>
            </form>
          )}
          <div style={{ textAlign:'center', marginTop:20 }}>
            <Link to="/login" style={{ fontSize:13, color:'var(--primary)' }}>Back to Sign In</Link>
          </div>
        </div>
      </div>
    </div>
  )
}
