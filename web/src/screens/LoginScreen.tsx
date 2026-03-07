import { useAuth } from '@/hooks/useAuth'
import styles from './LoginScreen.module.css'

export function LoginScreen() {
  const { signIn, error, signingIn } = useAuth()

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>TaskBrain</h1>
      <p className={styles.subtitle}>ADHD-friendly task management</p>
      {error && <p className={styles.error}>{error}</p>}
      <button
        className={styles.signInButton}
        onClick={signIn}
        disabled={signingIn}
      >
        {signingIn ? 'Signing in...' : 'Sign in with Google'}
      </button>
    </div>
  )
}
