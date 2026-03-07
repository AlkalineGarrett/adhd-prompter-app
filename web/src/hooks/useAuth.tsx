import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import {
  onAuthStateChanged,
  signInWithPopup,
  signOut as firebaseSignOut,
  GoogleAuthProvider,
  type User,
} from 'firebase/auth'
import { auth } from '@/firebase/config'

interface AuthState {
  user: User | null
  loading: boolean
  error: string | null
  signingIn: boolean
  signIn: () => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

const googleProvider = new GoogleAuthProvider()

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [signingIn, setSigningIn] = useState(false)

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (firebaseUser) => {
      setUser(firebaseUser)
      setLoading(false)
    })
    return unsubscribe
  }, [])

  const signIn = async () => {
    try {
      setSigningIn(true)
      setError(null)
      await signInWithPopup(auth, googleProvider)
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Sign-in failed'
      // Don't show error for user-cancelled popups
      if (message.includes('popup-closed-by-user') || message.includes('cancelled')) {
        return
      }
      setError(message)
      console.error('Sign-in error:', e)
    } finally {
      setSigningIn(false)
    }
  }

  const signOut = async () => {
    try {
      setError(null)
      await firebaseSignOut(auth)
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Sign-out failed'
      setError(message)
      console.error('Sign-out error:', e)
    }
  }

  return (
    <AuthContext.Provider value={{ user, loading, error, signingIn, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
