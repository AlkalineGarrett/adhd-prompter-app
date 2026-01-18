package org.alkaline.taskbrain.ui.currentnote

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.alkaline.taskbrain.R
import org.alkaline.taskbrain.ui.Dimens
import org.alkaline.taskbrain.ui.components.ActionButtonBar

/**
 * A status bar showing save status and a save button.
 */
@Composable
fun StatusBar(
    isSaved: Boolean,
    onSaveClick: () -> Unit
) {
    ActionButtonBar {
        Button(
            onClick = onSaveClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(R.color.action_button_background),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(Dimens.StatusBarButtonCornerRadius),
            contentPadding = PaddingValues(horizontal = Dimens.StatusBarButtonHorizontalPadding, vertical = 0.dp),
            modifier = Modifier.height(Dimens.StatusBarButtonHeight)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_save),
                contentDescription = stringResource(id = R.string.action_save),
                modifier = Modifier.size(Dimens.StatusBarButtonIconSize),
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(Dimens.StatusBarButtonIconTextSpacing))
            Text(
                text = stringResource(id = R.string.action_save),
                fontSize = Dimens.StatusBarButtonTextSize
            )
        }

        Spacer(modifier = Modifier.width(Dimens.StatusBarItemSpacing))

        Text(
            text = if (isSaved) stringResource(id = R.string.status_saved) else stringResource(id = R.string.status_unsaved),
            color = Color.Black,
            fontSize = Dimens.StatusTextSize
        )

        Spacer(modifier = Modifier.width(Dimens.StatusTextIconSpacing))

        Icon(
            painter = if (isSaved) painterResource(id = R.drawable.ic_check_circle) else painterResource(id = R.drawable.ic_warning),
            contentDescription = null,
            tint = if (isSaved) Color(0xFF4CAF50) else Color(0xFFFFC107),
            modifier = Modifier.size(Dimens.StatusIconSize)
        )
    }
}
