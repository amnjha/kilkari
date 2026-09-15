package com.kilkari.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kilkari.R
import com.kilkari.ui.theme.Display
import com.kilkari.ui.theme.KC
import com.kilkari.ui.theme.Sans

/**
 * Branded hand-off from the system splash. It fills the time the database takes to answer
 * rather than delaying on purpose, and repeats the launcher artwork so the launch reads as
 * one continuous motion.
 */
@Composable
fun SplashScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(KC.Screen),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(152.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Kilkari",
                    fontFamily = Display, fontWeight = FontWeight.ExtraBold,
                    fontSize = 40.sp, color = KC.Ink, letterSpacing = (-0.8).sp,
                )
                Text(
                    "Everything for your baby, on your phone.",
                    modifier = Modifier.padding(horizontal = 40.dp),
                    fontFamily = Sans, fontSize = 14.sp, color = KC.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
