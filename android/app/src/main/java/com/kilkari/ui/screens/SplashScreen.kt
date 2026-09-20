package com.kilkari.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.R
import com.kilkari.ui.components.BlobBackdrop
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.AccentScope
import com.kilkari.ui.theme.KAccents
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * Branded hand-off from the system splash, filling the time the database takes to answer
 * rather than delaying on purpose.
 *
 * A parent holding their baby, not a logo: the app's own mark shrinks to a brand stamp beside
 * the name, and the picture carries the screen. The same artwork opens onboarding, so the
 * first two things anyone sees are one continuous moment.
 */
@Composable
fun SplashScreen() {
    // Named rather than inherited: the splash is drawn before there is a route to take a
    // colour from, and it hands over to the welcome, which is lilac.
    AccentScope(KAccents.Quiet) {
    BlobBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.welcome_family),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.82f),
                contentScale = ContentScale.FillWidth,
            )
            Row(
                Modifier.padding(top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    "Kilkari",
                    fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp, color = KC.Ink, letterSpacing = (-0.76).sp,
                )
            }
            Text(
                "Everything for your baby, on your phone.",
                modifier = Modifier.padding(top = 8.dp),
                fontFamily = Sans, fontSize = 14.sp, color = KC.MutedStrong,
                textAlign = TextAlign.Center,
            )
        }
    }
    }
}
