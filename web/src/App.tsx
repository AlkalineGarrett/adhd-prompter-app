import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from '@/hooks/useAuth'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { NoteListScreen } from '@/screens/NoteListScreen'
import { NoteEditorScreen } from '@/screens/NoteEditorScreen'

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ProtectedRoute>
          <Routes>
            <Route path="/" element={<NoteListScreen />} />
            <Route path="/note/:noteId" element={<NoteEditorScreen />} />
          </Routes>
        </ProtectedRoute>
      </AuthProvider>
    </BrowserRouter>
  )
}
