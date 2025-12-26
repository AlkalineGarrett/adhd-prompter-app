package org.alkaline.adhdprompter.ui.home

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.alkaline.adhdprompter.R
import org.alkaline.adhdprompter.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel
    private var isSaved = true
    private var isUpdatingText = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val agentCommandText: EditText = binding.agentCommandText
        agentCommandText.movementMethod = ScrollingMovementMethod()

        val userContent: EditText = binding.userContent
        userContent.movementMethod = ScrollingMovementMethod()

        // Initialize UI with Saved state (optimistic)
        updateSaveStatus(true)

        // Add TextWatcher to detect changes
        userContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingText && isSaved) {
                    updateSaveStatus(false)
                }
            }
        })

        // Set up Save Button
        binding.saveButton.setOnClickListener {
            // Trigger save operation in ViewModel
            val content = userContent.text.toString()
            homeViewModel.saveContent(content)
        }

        // Observe Save Status from ViewModel
        homeViewModel.saveStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is SaveStatus.Saving -> {
                    // Optional: Show loading state (e.g., disable button, show progress)
                }
                is SaveStatus.Success -> {
                    updateSaveStatus(true)
                }
                is SaveStatus.Error -> {
                    Toast.makeText(context, "Save failed: ${status.message}", Toast.LENGTH_SHORT).show()
                    // Keep unsaved state so user can try again
                    updateSaveStatus(false) 
                }
            }
        }

        // Observe Load Status from ViewModel
        homeViewModel.loadStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is LoadStatus.Loading -> {
                    // Optional: show loading indicator
                }
                is LoadStatus.Success -> {
                    isUpdatingText = true
                    userContent.setText(status.content)
                    isUpdatingText = false
                    updateSaveStatus(true)
                }
                is LoadStatus.Error -> {
                    Toast.makeText(context, "Error loading content: ${status.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Load content initially
        homeViewModel.loadContent()

        return root
    }

    private fun updateSaveStatus(saved: Boolean) {
        isSaved = saved
        if (saved) {
            binding.saveStatusText.text = getString(R.string.status_saved)
            binding.saveStatusIcon.setImageResource(R.drawable.ic_check_circle)
            binding.saveStatusIcon.setColorFilter(Color.parseColor("#4CAF50")) // Green
        } else {
            binding.saveStatusText.text = getString(R.string.status_unsaved)
            binding.saveStatusIcon.setImageResource(R.drawable.ic_warning)
            binding.saveStatusIcon.setColorFilter(Color.parseColor("#FFC107")) // Amber/Warning color
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}