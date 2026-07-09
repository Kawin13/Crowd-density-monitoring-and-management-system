import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, LogIn, Radio, Shield } from 'lucide-react'
import toast from 'react-hot-toast'
import { useAuth } from '../context/AuthContext'
import api from '../services/api'

export default function LoginPage() {
  const [form, setForm] = useState({ usernameOrEmail: '', password: '' })
  const [showPw, setShowPw] = useState(false)
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async e => {
    e.preventDefault()
    if (!form.usernameOrEmail || !form.password) { toast.error('Please fill in all fields'); return }
    setLoading(true)
    try {
      const res = await api.post('/auth/login', form)
      login(res.data.data)
      toast.success('Welcome back!')
      navigate('/dashboard')
    } catch {
      toast.error('Invalid credentials. Please try again.')
    } finally { setLoading(false) }
  }

  return (
    <div style={{
      minHeight: '100vh', background: 'var(--bg)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 24,
      backgroundImage: 'radial-gradient(ellipse 80% 60% at 50% -10%, rgba(37,99,235,.12), transparent)',
    }}>
      <div style={{ width: '100%', maxWidth: 420 }} className="slide-up">
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 36 }}>
          <div style={{
            width: 60, height: 60, borderRadius: 16,
            background: 'linear-gradient(135deg,#2563EB,#7C3AED)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            marginBottom: 20, boxShadow: '0 8px 24px rgba(37,99,235,.35)',
          }}>
            <Radio size={28} color="#fff" />
          </div>
          <h1 style={{ fontSize: 26, fontWeight: 800, color: 'var(--t1)', letterSpacing: '-.5px', marginBottom: 6 }}>
            Crowd Density Monitoring
          </h1>
          <p style={{ fontSize: 14, color: 'var(--t2)' }}>and Management System</p>
        </div>

        {/* Card */}
        <div style={{
          background: 'var(--surface)', border: '1px solid var(--border)',
          borderRadius: 'var(--r-2xl)', padding: 36,
          boxShadow: 'var(--sh-xl)',
        }}>
          <div style={{ marginBottom: 24 }}>
            <h2 style={{ fontSize: 20, fontWeight: 700, color: 'var(--t1)', marginBottom: 4 }}>Sign in</h2>
            <p style={{ fontSize: 13, color: 'var(--t2)' }}>Enter your credentials to continue</p>
          </div>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="form-group">
              <label className="form-label" style={{ fontSize: 13, fontWeight: 500, color: 'var(--t2)', marginBottom: 6, display: 'block' }}>
                Username or Email
              </label>
              <input
                className="form-control"
                placeholder="Enter username or email"
                value={form.usernameOrEmail}
                onChange={e => setForm({ ...form, usernameOrEmail: e.target.value })}
                autoComplete="username"
                autoFocus
              />
            </div>

            <div className="form-group">
              <label className="form-label" style={{ fontSize: 13, fontWeight: 500, color: 'var(--t2)', marginBottom: 6, display: 'block' }}>
                Password
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  className="form-control"
                  type={showPw ? 'text' : 'password'}
                  placeholder="Enter your password"
                  value={form.password}
                  onChange={e => setForm({ ...form, password: e.target.value })}
                  autoComplete="current-password"
                  style={{ paddingRight: 44 }}
                />
                <button
                  type="button"
                  onClick={() => setShowPw(!showPw)}
                  style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', color: 'var(--t3)', display: 'flex' }}
                >
                  {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <div style={{ textAlign: 'right', marginTop: -8 }}>
              <Link to="/forgot-password" style={{ fontSize: 13, color: 'var(--primary)', fontWeight: 500 }}>
                Forgot password?
              </Link>
            </div>

            <button
              type="submit"
              className="btn btn-primary btn-xl"
              disabled={loading}
              style={{ width: '100%', justifyContent: 'center', marginTop: 4 }}
            >
              {loading
                ? <><span className="spinner" style={{ width: 16, height: 16 }} /> Signing in…</>
                : <><LogIn size={16} /> Sign In</>
              }
            </button>
          </form>

          <div style={{ textAlign: 'center', marginTop: 22, fontSize: 13, color: 'var(--t2)' }}>
            Don't have an account?{' '}
            <Link to="/register" style={{ color: 'var(--primary)', fontWeight: 600 }}>Create account</Link>
          </div>
        </div>

        {/* Security note */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 20, color: 'var(--t3)', fontSize: 12 }}>
          <Shield size={13} /> Secured with end-to-end encryption
        </div>
      </div>
    </div>
  )
}
