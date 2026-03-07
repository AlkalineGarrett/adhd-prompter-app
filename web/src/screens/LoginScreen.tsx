import { useAuth } from '@/hooks/useAuth'
import styles from './LoginScreen.module.css'

export function LoginScreen() {
  const { signIn } = useAuth()

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>TaskBrain</h1>
      <p className={styles.subtitle}>ADHD-friendly task management</p>
      <button className={styles.signInButton} onClick={signIn}>
        Sign in with Google
      </button>
    </div>
  )
}
