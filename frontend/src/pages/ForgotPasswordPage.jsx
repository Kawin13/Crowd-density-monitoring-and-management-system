import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Mail, ArrowLeft } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../services/api'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [sent, setSent] = useState(false)

  const handleSubmit = async e => {
    e.preventDefault()
    if (!email) { toast.error('Enter your email'); return }
    setLoading(true)
    try {
      await api.post('/auth/forgot-password', { email })
      setSent(true)
    } catch { toast.error('Could not send reset link. Please try again.') }
    finally { setLoading(false) }
  }

  return (
    <div style={{
      minHeight:'100vh', background:'var(--bg)',
      display:'flex', alignItems:'center', justifyContent:'center', padding:24,
    }}>
      <div style={{ width:'100%', maxWidth:400 }} className="slide-up">
        <div style={{
          background:'var(--surface)', border:'1px solid var(--border)',
          borderRadius:'var(--r-2xl)', padding:36, boxShadow:'var(--sh-xl)',
        }}>
          <div style={{ textAlign:'center', marginBottom:28 }}>
            <div style={{
              width:52, height:52, borderRadius:14, background:'#EFF6FF',
              display:'inline-flex', alignItems:'center', justifyContent:'center', marginBottom:14,
            }}>
              <Mail size={24} color="var(--primary)" />
            </div>
            <h2 style={{ fontSize:20, fontWeight:700, color:'var(--t1)', marginBottom:6 }}>Reset Password</h2>
            <p style={{ fontSize:13, color:'var(--t2)' }}>Enter your email to receive a reset link</p>
          </div>
          {sent ? (
            <div style={{ textAlign:'center' }}>
              <div style={{ background:'#F0FDF4', border:'1px solid #BBF7D0', borderRadius:'var(--r-md)', padding:16, marginBottom:20, color:'#166534', fontSize:13 }}>
                Reset link sent to <strong>{email}</strong>. Check your inbox.
              </div>
              <Link to="/login" className="btn btn-secondary" style={{ display:'inline-flex', gap:8 }}>
                <ArrowLeft size={15}/> Back to Sign In
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit} style={{ display:'flex', flexDirection:'column', gap:16 }}>
              <div>
                <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Email Address</label>
                <input className="form-control" type="email" placeholder="your@email.com" value={email} onChange={e => setEmail(e.target.value)} autoFocus />
              </div>
              <button type="submit" className="btn btn-primary btn-lg" disabled={loading} style={{ width:'100%', justifyContent:'center' }}>
                {loading ? 'Sending…' : 'Send Reset Link'}
              </button>
            </form>
          )}
          <div style={{ textAlign:'center', marginTop:20 }}>
            <Link to="/login" style={{ fontSize:13, color:'var(--primary)', display:'inline-flex', alignItems:'center', gap:5 }}>
              <ArrowLeft size={13}/> Back to Sign In
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
