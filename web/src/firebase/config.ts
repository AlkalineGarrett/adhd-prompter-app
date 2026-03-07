import { initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

const firebaseConfig = {
  apiKey: "AIzaSyDbbjG7ynlks5DodHoATVjJLHu_K_JX3KI",
  authDomain: "adhd-prompter.firebaseapp.com",
  projectId: "adhd-prompter",
  storageBucket: "adhd-prompter.firebasestorage.app",
  messagingSenderId: "613948682660",
  appId: "1:613948682660:web:placeholder",
}

const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const db = getFirestore(app)
