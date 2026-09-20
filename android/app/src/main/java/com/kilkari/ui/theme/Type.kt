package com.kilkari.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kilkari.R

/*
 * The two faces the app is set in, bundled rather than fetched.
 *
 * These came from the Google Fonts provider, which needs Play Services and a network the
 * first time a face is asked for. On a phone with neither — which is a phone this app is
 * meant to work on, since it does everything else offline — Compose fell back to the system
 * sans and the product looked like a different one. The files now ship inside the APK, synced
 * from common/fonts by the syncFonts task so iOS is set in exactly the same ones.
 */

/** Display face — headings and numbers. */
val Display = FontFamily(
    Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extra_bold, FontWeight.ExtraBold),
)

/** UI face — everything else. */
val Sans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semi_bold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extra_bold, FontWeight.ExtraBold),
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
