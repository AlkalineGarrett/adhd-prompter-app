package org.alkaline.adhdprompter.ui.home

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.alkaline.adhdprompter.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val agentCommandText: EditText = binding.agentCommandText
        agentCommandText.movementMethod = ScrollingMovementMethod()

        val userContent: EditText = binding.userContent
        userContent.movementMethod = ScrollingMovementMethod()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}