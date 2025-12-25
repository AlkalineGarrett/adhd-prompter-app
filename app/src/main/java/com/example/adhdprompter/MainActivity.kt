package com.example.adhdprompter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.adhdprompter.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set the toolbar as the Support Action Bar
        setSupportActionBar(binding.appToolbar)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        // setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (isKeyboardVisible) {
                navView.visibility = View.GONE
                // When keyboard is visible, add bottom padding equal to IME height
                // but subtract the system bars if they are also accounted for.
                // However, since we are hiding navView, the fragment will take full height.
                // We need to apply padding to the container to push content up.
                v.setPadding(0, systemBars.top, 0, imeInsets.bottom)
            } else {
                navView.visibility = View.VISIBLE
                // Reset padding when keyboard is hidden
                v.setPadding(0, systemBars.top, 0, 0)
            }
            
            insets
        }
    }
}