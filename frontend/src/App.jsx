import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ToastProvider } from './components/Toast/ToastProvider'
import { PortalLayout } from './components/Layout/PortalLayout'
import { DashboardPage } from './pages/DashboardPage'
import { PostCommissionPage } from './pages/PostCommissionPage'
import { MyCommissionsPage } from './pages/MyCommissionsPage'
import { ReportsReceivedPage } from './pages/ReportsReceivedPage'
import { ReportDetailPage } from './pages/ReportDetailPage'
import { MyAccountPage } from './pages/MyAccountPage'
import { ExceptionsQueuePage } from './pages/ExceptionsQueuePage'

export default function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<PortalLayout />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="post-commission" element={<PostCommissionPage />} />
            <Route path="my-commissions" element={<MyCommissionsPage />} />
            <Route path="reports-received" element={<ReportsReceivedPage />} />
            <Route path="reports-received/:reportId" element={<ReportDetailPage />} />
            <Route path="exceptions-queue" element={<ExceptionsQueuePage />} />
            <Route path="account" element={<MyAccountPage />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  )
}
