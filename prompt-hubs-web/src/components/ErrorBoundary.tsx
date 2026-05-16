import { Component, type ErrorInfo, type ReactNode } from "react"
import { AlertTriangle, RefreshCw } from "lucide-react"
import { Button } from "@/components/ui/button"

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[ErrorBoundary]", error, info)
  }

  reset = () => {
    this.setState({ hasError: false, error: null })
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-[60vh] items-center justify-center p-6">
          <div className="w-full max-w-md rounded-lg border border-destructive/30 bg-destructive/5 p-6 text-center">
            <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-destructive/10">
              <AlertTriangle className="h-6 w-6 text-destructive" />
            </div>
            <h2 className="mb-2 text-lg font-semibold text-slate-900">
              화면을 그릴 수 없습니다
            </h2>
            <p className="mb-4 text-sm text-slate-600">
              {this.state.error?.message || "예상치 못한 오류가 발생했습니다."}
            </p>
            <div className="flex justify-center gap-2">
              <Button variant="outline" onClick={this.reset}>
                <RefreshCw className="mr-2 h-4 w-4" />
                다시 시도
              </Button>
              <Button onClick={() => window.location.reload()}>새로고침</Button>
            </div>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
