package io.github.utshabasak.bookstorebd.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/*
 * The website's design tokens (client/src/index.css), light and dark.
 *
 * Where a token is used for text or for a fill under text, the pairing used
 * here meets WCAG AA (4.5:1); ColorContrastTest holds every pairing to it. Two
 * deliberate differences from the website follow from that:
 *
 * - Text on the sunset orange is the deep indigo rather than white, since
 *   white on #ff5c35 is 3.1:1.
 * - Prices use a deeper sunset (#cc3418) in light mode, which reads as the same
 *   colour and meets 4.5:1 on the page and on cards at any size.
 */
internal object Light {
    val Brand = Color(0xFF6D28D9)
    val BrandLight = Color(0xFF8B5CF6)
    val BrandDark = Color(0xFF5B21B6)
    val BrandDeep = Color(0xFF1E1B4B)
    val BrandTint = Color(0xFFF3EFFF)
    val BrandLine = Color(0xFFE4DCFB)
    val Accent = Color(0xFFFF5C35)
    val AccentTint = Color(0xFFFFF1EC)
    val AccentInk = Color(0xFFC2410C)
    val Price = Color(0xFFCC3418)
    val Sun = Color(0xFFFACC15)
    val SunTint = Color(0xFFFEFCE8)
    val SunInk = Color(0xFFA16207)
    val Success = Color(0xFF047857)
    val SuccessTint = Color(0xFFECFDF5)
    val Danger = Color(0xFFDC2626)
    val DangerTint = Color(0xFFFEF2F2)
    val DangerInk = Color(0xFFB91C1C)
    val WarnTint = Color(0xFFFFF7ED)
    val WarnInk = Color(0xFFC2410C)
    val Ink = Color(0xFF111827)
    val InkSoft = Color(0xFF374151)
    val InkMuted = Color(0xFF6B7280)
    val Outline = Color(0xFF7C7896)
    val Line = Color(0xFFECE8F7)
    val Page = Color(0xFFF8F7FC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFFAF9FE)
    val SurfaceMuted = Color(0xFFF3F4F6)
}

internal object Dark {
    val Brand = Color(0xFFA78BFA)
    val BrandLight = Color(0xFFC4B5FD)
    val BrandFill = Color(0xFF7C3AED)
    val BrandDeep = Color(0xFF120F24)
    val BrandTint = Color(0xFF241D3D)
    val BrandLine = Color(0xFF3A3160)
    val Accent = Color(0xFFFF6B47)
    val AccentLight = Color(0xFFFF8A6B)
    val AccentTint = Color(0xFF3A1E17)
    val Sun = Color(0xFFFACC15)
    val SunTint = Color(0xFF332C10)
    val SunInk = Color(0xFFFDE047)
    val Success = Color(0xFF34D399)
    val SuccessTint = Color(0xFF0F2B23)
    val Danger = Color(0xFFF87171)
    val DangerTint = Color(0xFF3B1619)
    val DangerInk = Color(0xFFFCA5A5)
    val WarnTint = Color(0xFF3A2312)
    val WarnInk = Color(0xFFFDBA74)
    val Ink = Color(0xFFF2F0FA)
    val InkSoft = Color(0xFFD4D1E6)
    val InkMuted = Color(0xFFA4A0BD)
    val Outline = Color(0xFF8D88A8)
    val Line = Color(0xFF2D2843)
    val Page = Color(0xFF0F0D18)
    val Surface = Color(0xFF19162A)
    val SurfaceAlt = Color(0xFF1E1A31)
    val SurfaceMuted = Color(0xFF24203A)
}
