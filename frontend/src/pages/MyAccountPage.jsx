import { useEffect, useState } from 'react'
import { getAccount } from '../services/portalService'
import { useToast } from '../components/Toast/ToastProvider'
import './MyAccountPage.css'

export function MyAccountPage() {
  const toast = useToast()
  const [account, setAccount] = useState(null)
  const [passwordForm, setPasswordForm] = useState({ current: '', next: '', confirm: '' })

  useEffect(() => {
    let cancelled = false
    getAccount().then(({ data }) => {
      if (!cancelled) setAccount(data)
    })
    return () => {
      cancelled = true
    }
  }, [])

  function updatePasswordField(field, value) {
    setPasswordForm((current) => ({ ...current, [field]: value }))
  }

  function handlePasswordSubmit(event) {
    event.preventDefault()
    if (!passwordForm.current || !passwordForm.next) {
      toast.error('Enter your current and new password.')
      return
    }
    if (passwordForm.next !== passwordForm.confirm) {
      toast.error('New password and confirmation do not match.')
      return
    }
    toast.success('Password updated.')
    setPasswordForm({ current: '', next: '', confirm: '' })
  }

  if (!account) return null

  return (
    <div className="my-account-page">
      <h1>My account</h1>
      <p className="my-account-page__subtitle">[Council 1] &middot; Council portal</p>

      <div className="page-card page-card--wide my-account-page__profile">
        <div className="my-account-page__profile-header">
          <span className="my-account-page__avatar">{account.initials}</span>
          <div>
            <span className="my-account-page__profile-name">{account.fullName}</span>
            <span className="my-account-page__profile-role">
              {account.role} &middot; {account.organisation}
            </span>
          </div>
          <button type="button" className="my-account-page__edit" onClick={() => toast.info('Editing is coming soon.')}>
            Edit
          </button>
        </div>

        <dl className="my-account-page__details">
          <div>
            <dt>Full name</dt>
            <dd>{account.fullName}</dd>
          </div>
          <div>
            <dt>Email address</dt>
            <dd>{account.email}</dd>
          </div>
          <div>
            <dt>Phone number</dt>
            <dd>{account.phone}</dd>
          </div>
          <div>
            <dt>Organisation</dt>
            <dd>{account.organisation}</dd>
          </div>
          <div>
            <dt>Role</dt>
            <dd>{account.role}</dd>
          </div>
        </dl>
      </div>

      <div className="page-card page-card--wide">
        <h2>Change password</h2>
        <form onSubmit={handlePasswordSubmit} className="my-account-page__password-form">
          <div className="form-field">
            <label htmlFor="current-password">Current password</label>
            <input
              id="current-password"
              type="password"
              placeholder="Enter current password"
              value={passwordForm.current}
              onChange={(event) => updatePasswordField('current', event.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="new-password">New password</label>
            <input
              id="new-password"
              type="password"
              placeholder="At least 8 characters"
              value={passwordForm.next}
              onChange={(event) => updatePasswordField('next', event.target.value)}
            />
          </div>
          <div className="form-field">
            <label htmlFor="confirm-password">Confirm new password</label>
            <input
              id="confirm-password"
              type="password"
              placeholder="Repeat new password"
              value={passwordForm.confirm}
              onChange={(event) => updatePasswordField('confirm', event.target.value)}
            />
          </div>
          <div className="my-account-page__password-actions">
            <button type="submit" className="btn btn--primary">
              Update password
            </button>
          </div>
        </form>
      </div>

      <div className="page-card page-card--wide">
        <h2>Security settings</h2>
        <p className="my-account-page__placeholder">Two-factor authentication and session management settings will appear here.</p>
      </div>

      <div className="page-card page-card--wide my-account-page__signout">
        <h2>Sign out</h2>
        <div className="my-account-page__signout-row">
          <span>Sign out of this device</span>
          <button type="button" className="btn btn--secondary" onClick={() => toast.success('Signed out.')}>
            Sign out
          </button>
        </div>
      </div>
    </div>
  )
}
