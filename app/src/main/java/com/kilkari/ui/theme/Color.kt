package com.kilkari.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette sampled from the Kilkari mark: the badge's coral field and cream S-curve, the gold
 * sound waves, and the teal and burnt-orange of the quadrants the logo was designed against.
 *
 * Four hue families carry the app — coral for the brand and anything clinical, teal for health
 * and growth, gold for money and milestones, clay for medicines and rest — over warm cream
 * neutrals rather than the cool greys a default palette would give. [Danger] is deliberately
 * darker and less orange than [Coral] so lateness cannot be mistaken for a brand accent.
 *
 * Text pairings were checked against WCAG AA: [Ink] on [Screen] is 14.1:1, [Muted] 4.8:1,
 * and white on [Coral] 4.7:1.
 */
object KC {
    // Ground & surfaces — warm cream, taken from the badge's S-curve.
    val Screen = Color(0xFFFBF5EC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceWarm = Color(0xFFF6EFE3)
    val Border = Color(0xFFF0E3D4)
    val BorderStrong = Color(0xFFE5D3BE)
    val Divider = Color(0xFFF5EBDE)

    /** What a raised surface casts on the cream: warm brown, never grey. */
    val ClayShadow = Color(0xFF8C6A4A)

    // Ink — the deep brown of the mark's eyes.
    val Ink = Color(0xFF332420)
    val Muted = Color(0xFF7E6B61)
    val MutedStrong = Color(0xFF5E4C44)
    val Faint = Color(0xFFA89588)

    // Coral — the badge field. Primary, and anything clinical.
    val Coral = Color(0xFFC94A30)
    val CoralDeep = Color(0xFFA93A24)
    val CoralLight = Color(0xFFE2664C)
    val CoralPale = Color(0xFFF3C4B5)
    val CoralPaler = Color(0xFFF8DCD2)
    val CoralBg = Color(0xFFFCEDE7)
    val CoralRing = Color(0xFFF2C6B8)

    /**
     * Lilac — the one hue not taken from the mark.
     *
     * Coral carries everything the app asks of a parent: doses due, vaccines overdue, money
     * going out. A second warm accent beside it would blend into that; a cool one gives the
     * gentler half of the app — the welcome, the moments, the playful surfaces — somewhere to
     * live without competing with the brand. Checked against the cream ground at 4.9:1, and
     * white on it at 5.3:1.
     */
    val Lilac = Color(0xFF6C57D6)
    val LilacDeep = Color(0xFF4F3DAE)
    val LilacLight = Color(0xFF9C8BEA)
    val LilacBg = Color(0xFFEFEBFD)
    val LilacRing = Color(0xFFD8CEFA)

    // Clay — the burnt orange quadrant. Medicines, sleep, documents.
    val Clay = Color(0xFFC4703A)
    val ClayDeep = Color(0xFF9C5526)
    val ClayLight = Color(0xFFDD9059)
    val ClayBg = Color(0xFFFBEEE0)
    val ClayBg2 = Color(0xFFF6E2CC)

    // Gold — the sound waves. Money, events, milestones.
    val Gold = Color(0xFFB07C11)
    val GoldDeep = Color(0xFF8A5F09)
    val GoldLight = Color(0xFFE8B94A)
    val GoldBg = Color(0xFFFCF2DC)
    val GoldRing = Color(0xFFEFDCA8)

    // Teal — the cool quadrant. Health, growth, anything complete.
    val Teal = Color(0xFF347A73)
    val TealDeep = Color(0xFF26605A)
    val TealLight = Color(0xFF4E9E96)
    val TealBg = Color(0xFFE4F2F0)
    val TealRing = Color(0xFFBCDFDA)

    // Sea — a lighter, bluer teal, so "general" money reads apart from "medical".
    val Sea = Color(0xFF2F7B80)
    val SeaLight = Color(0xFF6FB9BC)
    val SeaMid = Color(0xFF3E8F94)
    val SeaDeep = Color(0xFF235F63)
    val SeaBg = Color(0xFFE3F1F2)
    val SeaRing = Color(0xFFBBDFE2)

    /**
     * Leaf, Sky and Rose — the three hues the mark does not supply.
     *
     * Six families was enough while colour only tinted an icon. Once whole surfaces are
     * painted, a screen needs a hue of its own or every screen looks like the last one, and
     * warm-only families (coral, clay, gold) blur into each other at tile size. These three
     * fill the gaps in the wheel: green for anything that grows or completes, blue for the
     * calm half — sleep, paperwork, records — and pink for the keepsakes.
     *
     * Each [main] was chosen as the darkest step that still reads as its hue while carrying
     * white text at 4.5:1 — Leaf 4.8, Sky 5.0, Rose 5.4 — and each [Deep] step clears 4.5:1
     * as text on the cream ground.
     */
    val Leaf = Color(0xFF487F3C)
    val LeafDeep = Color(0xFF35622C)
    val LeafLight = Color(0xFF79AE6B)
    val LeafBg = Color(0xFFECF4E7)
    val LeafRing = Color(0xFFBEDCAF)

    val Sky = Color(0xFF2A72B8)
    val SkyDeep = Color(0xFF1F568D)
    val SkyLight = Color(0xFF619FD6)
    val SkyBg = Color(0xFFE7F1FA)
    val SkyRing = Color(0xFFB6D6F0)

    val Rose = Color(0xFFB93B6B)
    val RoseDeep = Color(0xFF96294F)
    val RoseLight = Color(0xFFDE7399)
    val RoseBg = Color(0xFFFBE9F0)
    val RoseRing = Color(0xFFF3BED1)

    /**
     * Washes — the grounds a coloured surface is actually painted with.
     *
     * The `Bg` steps above are near-white: right behind a 20dp icon, invisible across a whole
     * tile, which is what made the app read as one colour on cream. These sit a long way
     * further up the ramp — the same hue at 72% saturation — while [Ink] on them still clears
     * 8.3:1 everywhere.
     *
     * The warm three are darker than the cool six on purpose. The ground is warm cream, so a
     * pale coral, clay or gold is the ground; a pale teal or lilac is already a different
     * colour. Lightness was lowered on those three until each cleared 1.35:1 against the
     * cream, which is where a tile stops looking like a shadow and starts looking painted.
     */
    val CoralWash = Color(0xFFF3BFB4)
    val ClayWash = Color(0xFFF1C4A7)
    val GoldWash = Color(0xFFEED196)
    val LeafWash = Color(0xFFC3F4B9)
    val TealWash = Color(0xFFB9F4EE)
    val SeaWash = Color(0xFFB9F0F4)
    val SkyWash = Color(0xFFB9D7F4)
    val LilacWash = Color(0xFFC4BBF4)
    val RoseWash = Color(0xFFF4B9CF)
    val StoneWash = Color(0xFFE2C9BB)

    // Danger — deeper and browner than coral, so lateness is never read as brand.
    val Danger = Color(0xFF8C2F22)

    // ── Chart marks ─────────────────────────────────────────────────────────
    //
    // Validated with the dataviz palette validator (OKLCH band, chroma floor, CVD separation
    // under protanopia and deuteranopia, normal-vision floor, 3:1 against the white card),
    // not picked by eye. Two findings shaped these:
    //  - Sea (#2F7B80) reads as grey in a chart, chroma 0.074 against a 0.10 floor. It cannot
    //    get more saturated at its own lightness inside sRGB, so it was moved up the band to
    //    the nearest passing step instead. The app's Sea is left alone; this is marks only.
    //  - Coral, gold and clay are all warm and fail colour-blind separation against each
    //    other in every adjacent pairing. Sea is the only cool hue, so it sits between them.
    //
    // Categorical theme, fixed order, assigned in sequence and never cycled:
    val ChartCoral = Color(0xFFC94A30)
    val ChartSea = Color(0xFF05959D)
    val ChartGold = Color(0xFFB07C11)
    val chartCategorical = listOf(ChartCoral, ChartSea, ChartGold)

    /** The unfilled part of a meter: a light step of the fill's own ramp, not a grey track. */
    val ChartSeaTrack = Color(0xFFD9EFF0)

    // Two steps of the one hue for the growth chart's percentile bands: the inner band has to
    // read as clearly darker than the outer, while both stay behind the lines drawn over them.
    val ChartBandOuter = Color(0xFFEAF4F5)
    val ChartBandInner = Color(0xFFC4E1E4)

    /** Gridlines and axis rules: hairline, solid, one step off the card. */
    val ChartGrid = Color(0xFFEFE4D8)
    val DangerLight = Color(0xFFB04234)
    val DangerDeep = Color(0xFF6E2118)
    val DangerBg = Color(0xFFFBE8E4)
    val DangerBg2 = Color(0xFFF6D9D2)
    val DangerRing = Color(0xFFEDC3B9)

    // Warm neutrals for the quietest surfaces.
    val Stone = Color(0xFF6B5B52)
    val StoneMid = Color(0xFF8B7A6F)
    val StoneLight = Color(0xFFB3A296)
    val StoneBg = Color(0xFFF2EBE1)
    val Track = Color(0xFFD9CBBC)
}
