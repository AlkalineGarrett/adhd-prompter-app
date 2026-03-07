import type { ReactNode } from 'react'
import { useAuth } from '@/hooks/useAuth'
import { LoginScreen } from '@/screens/LoginScreen'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()

  if (loading) {
    return <div className="loading">Loading...</div>
  }

  if (!user) {
    return <LoginScreen />
  }

  return <>{children}</>
}
