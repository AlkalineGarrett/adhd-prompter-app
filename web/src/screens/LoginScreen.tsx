import { useAuth } from '@/hooks/useAuth'
import { APP_NAME, LOGIN_SUBTITLE, SIGNING_IN, SIGN_IN_WITH_GOOGLE } from '@/strings'
import styles from './LoginScreen.module.css'

export function LoginScreen() {
  const { signIn, error, signingIn } = useAuth()

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>{APP_NAME}</h1>
      <p className={styles.subtitle}>{LOGIN_SUBTITLE}</p>
      {error && <p className={styles.error}>{error}</p>}
      <button
        className={styles.signInButton}
        onClick={signIn}
        disabled={signingIn}
      >
        {signingIn ? SIGNING_IN : SIGN_IN_WITH_GOOGLE}
      </button>
    </div>
  )
}
