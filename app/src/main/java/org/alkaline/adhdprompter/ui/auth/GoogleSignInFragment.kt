package org.alkaline.adhdprompter.ui.auth

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import org.alkaline.adhdprompter.R
import org.alkaline.adhdprompter.databinding.FragmentGoogleBinding
import java.util.concurrent.Executors

/**
 * Demonstrate Firebase Authentication using a Google ID Token.
 */
class GoogleSignInFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private var _binding: FragmentGoogleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGoogleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Credential Manager
        credentialManager = CredentialManager.create(requireContext())

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Button listeners
        binding.signInButton.setOnClickListener { 
            signIn() 
        }

        // Display Credential Manager Bottom Sheet if user isn't logged in
        if (auth.currentUser == null) {
            showBottomSheet()
        } else {
             // If already signed in, navigate to home
             findNavController().navigate(R.id.navigation_home)
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            findNavController().navigate(R.id.navigation_home)
        }
    }

    override fun onStop() {
        super.onStop()
        hideProgressBar()
    }

    private fun signIn() {
        // Create the dialog configuration for the Credential Manager request
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
            requireContext().getString(R.string.default_web_client_id)
        ).build()

        // Create the Credential Manager request using the configuration created above
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        launchCredentialManager(request)
    }

    private fun showBottomSheet() {
        // Create the bottom sheet configuration for the Credential Manager request
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) 
            .setServerClientId(requireContext().getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()

        // Create the Credential Manager request using the configuration created above
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        launchCredentialManager(request)
    }

    private fun launchCredentialManager(request: GetCredentialRequest) {
        credentialManager.getCredentialAsync(
            requireContext(),
            request,
            CancellationSignal(),
            Executors.newSingleThreadExecutor(),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    // Extract credential from the result returned by Credential Manager
                    createGoogleIdToken(result.credential)
                }

                override fun onError(e: GetCredentialException) {
                    Log.e(TAG, "Credential Manager error", e)
                    
                    requireActivity().runOnUiThread {
                        if (e is NoCredentialException) {
                            Snackbar.make(binding.mainLayout, "No credentials found.", Snackbar.LENGTH_LONG).show()
                        }
                        hideProgressBar()
                    }
                }
            }
        )
    }

    private fun createGoogleIdToken(credential: Credential) {
        // Update UI to show progress bar while response is being processed
        requireActivity().runOnUiThread { showProgressBar() }

        // Check if credential is of type Google ID
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            // Create Google ID Token
            val credentialData = credential.data
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialData)
                // Sign in to Firebase with using the token
                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: Exception) {
                 Log.e(TAG, "Error parsing Google ID Token", e)
                 requireActivity().runOnUiThread { hideProgressBar() }
            }
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
            requireActivity().runOnUiThread { hideProgressBar() }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    // Navigate to home on success
                    findNavController().navigate(R.id.navigation_home)
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Snackbar.make(binding.mainLayout, "Authentication Failed.", Snackbar.LENGTH_SHORT).show()
                }

                hideProgressBar()
            }
    }

    private fun showProgressBar() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        binding.progressBar.visibility = View.GONE
    }

    private fun hideKeyboard(view: View) {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "GoogleFragment"
    }
}