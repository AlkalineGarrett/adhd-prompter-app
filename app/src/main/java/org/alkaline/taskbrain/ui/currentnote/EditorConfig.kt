package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized configuration for the editor components.
 * Consolidates all styling constants used across the currentnote package.
 */
object EditorConfig {
    // ============================================
    // Typography
    // ============================================

    /** Default font size for editor text */
    val FontSize = 16.sp

    // ============================================
    // Padding
    // ============================================

    /** Default horizontal padding inside the editor */
    val HorizontalPadding = 8.dp

    /** Default vertical padding inside the editor */
    val VerticalPadding = 8.dp

    /** Default gutter start padding */
    val GutterStartPadding = 4.dp

    /** Default gutter end padding */
    val GutterEndPadding = 8.dp

    // ============================================
    // Gutter
    // ============================================

    /** Width of the line number gutter */
    val GutterWidth: Dp = 21.dp

    /** Background color for the gutter */
    val GutterBackgroundColor = Color(0xFFE0E0E0) // Light gray

    /** Color for gutter border lines */
    val GutterLineColor = Color(0xFF9E9E9E) // Dark gray

    /** Color for selected lines in the gutter */
    val GutterSelectionColor = Color(0xFF90CAF9) // Light blue

    // ============================================
    // Selection
    // ============================================

    /** Standard selection highlight color */
    val SelectionColor = Color(0xFF338FFF).copy(alpha = 0.3f)

    /** Lighter selection color for newline characters */
    val NewlineSelectionColor = Color(0xFF338FFF).copy(alpha = 0.15f)

    // ============================================
    // Selection Handles
    // ============================================

    /** Width and height of the selection handle */
    val HandleSize: Dp = 22.dp

    /** Color of the selection handle */
    val HandleColor: Color = Color(0xFF2196F3)
}

/**
 * Backward compatibility alias for EditorDefaults.
 * New code should use EditorConfig directly.
 */
@Deprecated(
    "Use EditorConfig instead",
    ReplaceWith("EditorConfig", "org.alkaline.taskbrain.ui.currentnote.EditorConfig")
)
typealias EditorDefaults = EditorConfig

/**
 * Backward compatibility aliases for gutter constants.
 */
@Deprecated(
    "Use EditorConfig.GutterWidth instead",
    ReplaceWith("EditorConfig.GutterWidth")
)
val GutterWidth: Dp get() = EditorConfig.GutterWidth

/**
 * Backward compatibility aliases for selection constants.
 */
@Deprecated(
    "Use EditorConfig.SelectionColor instead",
    ReplaceWith("EditorConfig.SelectionColor")
)
val SelectionColor: Color get() = EditorConfig.SelectionColor

@Deprecated(
    "Use EditorConfig.NewlineSelectionColor instead",
    ReplaceWith("EditorConfig.NewlineSelectionColor")
)
val NewlineSelectionColor: Color get() = EditorConfig.NewlineSelectionColor

/**
 * Backward compatibility alias for SelectionHandleDefaults.
 */
@Deprecated(
    "Use EditorConfig instead",
    ReplaceWith("EditorConfig", "org.alkaline.taskbrain.ui.currentnote.EditorConfig")
)
object SelectionHandleDefaults {
    val HandleSize: Dp get() = EditorConfig.HandleSize
    val HandleColor: Color get() = EditorConfig.HandleColor
}
