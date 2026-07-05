import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserPlus, Radio } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuth } from '../context/AuthContext'
import api from '../services/api'

export default function RegisterPage() {
  const [form, setForm] = useState({ username:'', email:'', password:'', fullName:'', role:'VIEWER' })
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()
  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }))

  const handleSubmit = async e => {
    e.preventDefault()
    if (!form.username || !form.email || !form.password) { toast.error('Please fill all required fields'); return }
    setLoading(true)
    try {
      const res = await api.post('/auth/register', form)
      login(res.data.data)
      toast.success('Account created!')
      navigate('/dashboard')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Registration failed')
    } finally { setLoading(false) }
  }

  return (
    <div style={{
      minHeight:'100vh', background:'var(--bg)',
      display:'flex', alignItems:'center', justifyContent:'center', padding:24,
      backgroundImage:'radial-gradient(ellipse 80% 60% at 50% -10%, rgba(37,99,235,.12), transparent)',
    }}>
      <div style={{ width:'100%', maxWidth:440 }} className="slide-up">
        <div style={{ textAlign:'center', marginBottom:32 }}>
          <div style={{
            width:56, height:56, borderRadius:14,
            background:'linear-gradient(135deg,#2563EB,#7C3AED)',
            display:'inline-flex', alignItems:'center', justifyContent:'center',
            marginBottom:16, boxShadow:'0 8px 24px rgba(37,99,235,.35)',
          }}>
            <Radio size={26} color="#fff" />
          </div>
          <h1 style={{ fontSize:22, fontWeight:800, color:'var(--t1)', marginBottom:4 }}>Create Account</h1>
          <p style={{ fontSize:13, color:'var(--t2)' }}>Join the monitoring platform</p>
        </div>

        <div style={{
          background:'var(--surface)', border:'1px solid var(--border)',
          borderRadius:'var(--r-2xl)', padding:32, boxShadow:'var(--sh-xl)',
        }}>
          <form onSubmit={handleSubmit} style={{ display:'flex', flexDirection:'column', gap:14 }}>
            {[
              { label:'Full Name', key:'fullName', type:'text', placeholder:'John Doe' },
              { label:'Username *', key:'username', type:'text', placeholder:'johndoe' },
              { label:'Email *', key:'email', type:'email', placeholder:'john@example.com' },
              { label:'Password *', key:'password', type:'password', placeholder:'Min 8 characters' },
            ].map(({ label, key, type, placeholder }) => (
              <div key={key}>
                <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>{label}</label>
                <input className="form-control" type={type} placeholder={placeholder} value={form[key]} onChange={set(key)} />
              </div>
            ))}
            <div>
              <label style={{ fontSize:13, fontWeight:500, color:'var(--t2)', marginBottom:6, display:'block' }}>Role</label>
              <select className="form-control" value={form.role} onChange={set('role')}>
                <option value="VIEWER">Viewer — Read-only access</option>
                <option value="OPERATOR">Operator — Camera management</option>
              </select>
            </div>
            <button type="submit" className="btn btn-primary btn-lg" disabled={loading}
              style={{ width:'100%', justifyContent:'center', marginTop:6 }}>
              {loading ? <><span className="spinner" style={{width:15,height:15}} /> Creating…</>
                : <><UserPlus size={16}/> Create Account</>}
            </button>
          </form>
          <p style={{ textAlign:'center', marginTop:20, fontSize:13, color:'var(--t2)' }}>
            Already have an account?{' '}
            <Link to="/login" style={{ color:'var(--primary)', fontWeight:600 }}>Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
