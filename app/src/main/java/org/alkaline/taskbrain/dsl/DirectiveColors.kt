package org.alkaline.taskbrain.dsl

import androidx.compose.ui.graphics.Color

/**
 * Centralized color definitions for directive UI components.
 *
 * These colors are used across DirectiveChip, DirectiveLineRenderer, and DirectiveEditRow.
 */
object DirectiveColors {
    // Success/computed state - green palette
    val SuccessBackground = Color(0xFFE8F5E9)   // Light green (chip background)
    val SuccessContent = Color(0xFF2E7D32)      // Dark green (chip text)
    val SuccessBorder = Color(0xFF4CAF50)       // Medium green (box borders, confirm button)

    // Error state - red palette
    val ErrorBackground = Color(0xFFFFEBEE)     // Light red (chip background)
    val ErrorContent = Color(0xFFC62828)        // Dark red (chip text)
    val ErrorBorder = Color(0xFFF44336)         // Medium red (box borders)
    val ErrorText = Color(0xFFD32F2F)           // Dark red (inline error text)

    // Edit row - neutral palette
    val EditRowBackground = Color(0xFFF5F5F5)   // Light gray
    val EditIndicator = Color(0xFF9E9E9E)       // Medium gray
    val CancelButton = Color(0xFF757575)        // Darker gray
}
