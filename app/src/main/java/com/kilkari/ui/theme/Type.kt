package com.kilkari.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.kilkari.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun family(name: String, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(GoogleFont(name), provider, it, FontStyle.Normal) }
)

/** Display face — headings and numbers. Falls back to the platform sans if unavailable. */
val Display = family("Bricolage Grotesque", FontWeight.Medium, FontWeight.Bold, FontWeight.ExtraBold)

/** UI face — everything else. */
val Sans = family(
    "Plus Jakarta Sans",
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
    FontWeight.Bold, FontWeight.ExtraBold,
)

/** Screen titles: 26sp extra-bold display with the design's tight tracking. */
val ScreenTitle = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.ExtraBold,
    fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-0.52).sp,
)

/** Detail-screen top bar title: 20sp bold display. */
val BarTitle = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp,
)

/** Big numbers — money totals, countdowns. */
fun displayNumber(size: Int) = TextStyle(
    fontFamily = Display, fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp, lineHeight = (size * 1.05).sp, letterSpacing = (size * -0.02).sp,
)

val KilkariTypography = Typography(
    displayLarge = displayNumber(40),
    headlineLarge = ScreenTitle,
    titleLarge = BarTitle,
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)
