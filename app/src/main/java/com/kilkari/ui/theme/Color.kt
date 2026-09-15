package com.kilkari.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette lifted verbatim from the Kilkari design canvas: an indigo → fuchsia brand ramp on a
 * lilac-tinted neutral ground, with one accent family per domain (health green, money sky,
 * vaccines fuchsia, meds rose, events amber).
 */
object KC {
    // Ground & surfaces
    val Screen = Color(0xFFF7F6FD)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFECE9FB)
    val BorderStrong = Color(0xFFE0E7FF)
    val Divider = Color(0xFFF1EEFB)

    // Ink
    val Ink = Color(0xFF1E1B4B)
    val Muted = Color(0xFF6B7280)
    val MutedStrong = Color(0xFF4B5563)
    val Faint = Color(0xFF9CA3AF)

    // Brand
    val Indigo = Color(0xFF4F46E5)
    val IndigoDeep = Color(0xFF4338CA)
    val Violet = Color(0xFF7C3AED)
    val VioletDeep = Color(0xFF6D28D9)
    val Fuchsia = Color(0xFFC026D3)
    val FuchsiaDeep = Color(0xFFA21CAF)
    val IndigoLight = Color(0xFF818CF8)
    val FuchsiaLight = Color(0xFFE879F9)
    val IndigoPale = Color(0xFFC7D2FE)
    val IndigoPaler = Color(0xFFA5B4FC)

    // Tinted backgrounds
    val IndigoBg = Color(0xFFEEF2FF)
    val VioletBg = Color(0xFFF5F3FF)
    val VioletBg2 = Color(0xFFEDE9FE)
    val FuchsiaBg = Color(0xFFFDF4FF)
    val FuchsiaRing = Color(0xFFF5D0FE)

    // Success / health
    val Green = Color(0xFF059669)
    val GreenBright = Color(0xFF10B981)
    val GreenDeep = Color(0xFF047857)
    val GreenBg = Color(0xFFECFDF5)
    val GreenRing = Color(0xFFA7F3D0)

    // Danger / meds
    val Rose = Color(0xFFE11D48)
    val RoseBright = Color(0xFFF43F5E)
    val RoseDeep = Color(0xFFBE123C)
    val RoseBg = Color(0xFFFFF1F2)
    val RoseBg2 = Color(0xFFFFE4E6)
    val RoseRing = Color(0xFFFECDD3)

    // Sky / money-general
    val Sky = Color(0xFF0EA5E9)
    val SkyBright = Color(0xFF38BDF8)
    val SkyDeep = Color(0xFF0369A1)
    val SkyMid = Color(0xFF0284C7)
    val SkyBg = Color(0xFFE0F2FE)
    val Cyan = Color(0xFF0E7490)
    val CyanBg = Color(0xFFECFEFF)

    // Amber / events
    val Amber = Color(0xFFF59E0B)
    val AmberDeep = Color(0xFFB45309)
    val AmberBg = Color(0xFFFEF3C7)
    val Orange = Color(0xFFC2410C)
    val OrangeBg = Color(0xFFFFF7ED)

    // Slate
    val Slate = Color(0xFF475569)
    val SlateMid = Color(0xFF64748B)
    val SlateLight = Color(0xFF94A3B8)
    val SlateBg = Color(0xFFF1F5F9)
    val Track = Color(0xFFCBD5E1)
}
