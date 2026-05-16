import { BrowserRouter, Routes, Route, Navigate, Outlet } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { useAuthStore } from "./store/authStore"
import LoginPage from "./pages/auth/LoginPage"
import SignupPage from "./pages/auth/SignupPage"
import OAuthCallbackPage from "./pages/auth/OAuthCallbackPage"
import AppLayout from "./components/layout/AppLayout"
import ErrorBoundary from "./components/ErrorBoundary"
import WorkspaceSelectPage from "./pages/workspace/WorkspaceSelectPage"
import WorkspaceNewPage from "./pages/workspace/WorkspaceNewPage"
import DashboardPage from "./pages/workspace/DashboardPage"
import LogListPage from "./pages/logs/LogListPage"
import LogNewPage from "./pages/logs/LogNewPage"
import LogDetailPage from "./pages/logs/LogDetailPage"
import LogPromotePage from "./pages/logs/LogPromotePage"
import DocListPage from "./pages/docs/DocListPage"
import DocEditPage from "./pages/docs/DocEditPage"
import PromptListPage from "./pages/prompts/PromptListPage"
import PromptNewPage from "./pages/prompts/PromptNewPage"
import PromptDetailPage from "./pages/prompts/PromptDetailPage"
import PromptReviewPage from "./pages/prompts/PromptReviewPage"
import VersionDiffPage from "./pages/prompts/VersionDiffPage"
import VersionNewPage from "./pages/prompts/VersionNewPage"
import ReviewListPage from "./pages/reviews/ReviewListPage"
import WorkspaceSettingsPage from "./pages/workspace/WorkspaceSettingsPage"

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

function ProtectedRoute() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <Outlet />
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ErrorBoundary>
          <Routes>
            {/* Public Routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/oauth/callback" element={<OAuthCallbackPage />} />

            {/* Protected Routes */}
            <Route element={<ProtectedRoute />}>
              <Route index element={<Navigate to="/workspace/select" replace />} />
              <Route path="/workspace/select" element={<WorkspaceSelectPage />} />
              <Route path="/workspace/new" element={<WorkspaceNewPage />} />

              {/* Workspace-scoped routes (Sidebar shell) */}
              <Route path="/w/:slug" element={<AppLayout />}>
                <Route index element={<DashboardPage />} />
                <Route path="prompts">
                  <Route index element={<PromptListPage />} />
                  <Route path="new" element={<PromptNewPage />} />
                  <Route path=":promptId" element={<PromptDetailPage />} />
                  <Route path=":promptId/review" element={<PromptReviewPage />} />
                  <Route path=":promptId/version/new" element={<VersionNewPage />} />
                  <Route path=":promptId/diff/:v1/:v2" element={<VersionDiffPage />} />
                </Route>
                <Route path="logs">
                  <Route index element={<LogListPage />} />
                  <Route path="new" element={<LogNewPage />} />
                  <Route path=":logId" element={<LogDetailPage />} />
                  <Route path=":logId/promote" element={<LogPromotePage />} />
                </Route>
                <Route path="docs">
                  <Route index element={<DocListPage />} />
                  <Route path="new" element={<DocEditPage />} />
                  <Route path=":docId" element={<DocEditPage />} />
                </Route>
                <Route path="reviews" element={<ReviewListPage />} />
                <Route path="settings" element={<WorkspaceSettingsPage />} />
              </Route>
            </Route>

            {/* Fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ErrorBoundary>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
