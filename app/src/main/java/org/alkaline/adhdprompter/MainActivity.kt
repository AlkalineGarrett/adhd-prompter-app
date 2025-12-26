package org.alkaline.adhdprompter

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.ClearCredentialException
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import org.alkaline.adhdprompter.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set the toolbar as the Support Action Bar
        setSupportActionBar(binding.appToolbar)

        val navView: BottomNavigationView = binding.navView

        navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        // setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        
        auth = FirebaseAuth.getInstance()

        // Check if user is signed in
        val user = auth.currentUser
        if (user == null) {
            // User is not signed in, navigate to GoogleSignInFragment
            navController.navigate(R.id.navigation_auth)
            
            // Hide bottom nav when in auth screen
            navView.visibility = View.GONE
        } else {
             // Ensure bottom nav is visible if we are logged in
             navView.visibility = View.VISIBLE
        }
        
        // Listener to show/hide bottom nav and action bar based on destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.navigation_auth) {
                navView.visibility = View.GONE
                supportActionBar?.hide()
            } else {
                navView.visibility = View.VISIBLE
                supportActionBar?.show()
                // Update the options menu to show/hide sign out button
                invalidateOptionsMenu() 
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            
            val currentDest = navController.currentDestination?.id
            
            if (isKeyboardVisible) {
                if (currentDest != R.id.navigation_auth) {
                   navView.visibility = View.GONE
                }
                v.setPadding(0, systemBars.top, 0, imeInsets.bottom)
            } else {
                if (currentDest != R.id.navigation_auth) {
                    navView.visibility = View.VISIBLE
                }
                v.setPadding(0, systemBars.top, 0, 0)
            }
            
            insets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val signOutItem = menu?.findItem(R.id.action_sign_out)
        // Only show sign out button if we are NOT on the auth screen
        val isAuthScreen = navController.currentDestination?.id == R.id.navigation_auth
        signOutItem?.isVisible = !isAuthScreen
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        Log.d("MainActivity", "Signing out...")
        auth.signOut()

        // Clear Credential Manager state
        val credentialManager = CredentialManager.create(this)
        val clearRequest = ClearCredentialStateRequest()
        credentialManager.clearCredentialStateAsync(
            clearRequest,
            android.os.CancellationSignal(),
            Executors.newSingleThreadExecutor(),
            object : CredentialManagerCallback<Void?, ClearCredentialException> {
                override fun onResult(result: Void?) {
                    Log.d("MainActivity", "Credential state cleared")
                     runOnUiThread {
                        // Navigate back to auth screen
                        navController.navigate(R.id.navigation_auth)
                    }
                }

                override fun onError(e: ClearCredentialException) {
                     Log.e("MainActivity", "Error clearing credential state", e)
                     runOnUiThread {
                        // Navigate back to auth screen even if clearing credentials failed
                        navController.navigate(R.id.navigation_auth)
                    }
                }
            }
        )
    }
}