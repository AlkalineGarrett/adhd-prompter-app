package org.alkaline.taskbrain.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.ui.Dimens

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = colorResource(R.color.action_button_background),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(Dimens.StatusBarButtonCornerRadius),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Dimens.StatusBarButtonHorizontalPadding, vertical = 0.dp),
        modifier = modifier.height(Dimens.StatusBarButtonHeight)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Dimens.StatusBarButtonIconSize),
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(Dimens.StatusBarButtonIconTextSpacing))
        Text(
            text = text,
            fontSize = Dimens.StatusBarButtonTextSize
        )
    }
}

@Composable
fun ActionButtonBar(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE0E0E0))
            .padding(vertical = Dimens.StatusBarPaddingVertical, horizontal = Dimens.StatusBarPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
