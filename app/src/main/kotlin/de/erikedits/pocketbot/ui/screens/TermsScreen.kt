package de.erikedits.pocketbot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.erikedits.pocketbot.R

@Composable
fun TermsScreen(onAccept: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            InitialAvatar("P", size = 72.dp)
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyLarge)
            SectionCard(title = stringResource(R.string.welcome_point_build_title)) { Text(stringResource(R.string.welcome_point_build)) }
            SectionCard(title = stringResource(R.string.welcome_point_phone_title)) { Text(stringResource(R.string.welcome_point_phone)) }
            SectionCard(title = stringResource(R.string.welcome_point_safe_title)) { Text(stringResource(R.string.welcome_point_safe)) }
            SectionCard(title = stringResource(R.string.terms_title)) {
                Text(stringResource(R.string.terms_text), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text(stringResource(R.string.terms_checkbox), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.terms_continue))
            }
        }
    }
}
