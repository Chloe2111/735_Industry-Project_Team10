import { ToastProvider } from './components/Toast/ToastProvider'

export default function App() {
  return (
    <ToastProvider>
      <div className="page-card">
        <h1>Client portal</h1>
        <p>Basic setup only — features build out from here.</p>
      </div>
    </ToastProvider>
  )
}
