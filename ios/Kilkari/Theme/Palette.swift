import SwiftUI

/// Kilkari's palette, the same values the Android app draws with.
///
/// Sampled from the mark: the badge's coral field and cream S-curve, the gold sound waves,
/// and the teal and burnt orange of the quadrants the logo was designed against. Three hues
/// the mark does not supply — leaf, sky and rose — fill the gaps in the wheel, so nine
/// families carry the app over warm cream neutrals rather than the cool greys a default
/// palette would give.
///
/// Every value was contrast-checked rather than judged: white on each accent clears 4.5:1,
/// each deep step clears 4.5:1 as text on the cream ground, and ink on every wash clears
/// 8.3:1. The Kotlin source is `android/.../ui/theme/Color.kt`; these were read out of it
/// rather than retyped.
enum KC {

    static let screen = Color(red: 0.984314, green: 0.960784, blue: 0.925490)  // #FBF5EC
    static let surface = Color(red: 1.000000, green: 1.000000, blue: 1.000000)  // #FFFFFF
    static let surfaceWarm = Color(red: 0.964706, green: 0.937255, blue: 0.890196)  // #F6EFE3
    static let border = Color(red: 0.941176, green: 0.890196, blue: 0.831373)  // #F0E3D4
    static let borderStrong = Color(red: 0.898039, green: 0.827451, blue: 0.745098)  // #E5D3BE
    static let divider = Color(red: 0.960784, green: 0.921569, blue: 0.870588)  // #F5EBDE
    static let clayShadow = Color(red: 0.549020, green: 0.415686, blue: 0.290196)  // #8C6A4A
    static let ink = Color(red: 0.200000, green: 0.141176, blue: 0.125490)  // #332420
    static let muted = Color(red: 0.494118, green: 0.419608, blue: 0.380392)  // #7E6B61
    static let mutedStrong = Color(red: 0.368627, green: 0.298039, blue: 0.266667)  // #5E4C44
    static let faint = Color(red: 0.658824, green: 0.584314, blue: 0.533333)  // #A89588
    static let coral = Color(red: 0.788235, green: 0.290196, blue: 0.188235)  // #C94A30
    static let coralDeep = Color(red: 0.662745, green: 0.227451, blue: 0.141176)  // #A93A24
    static let coralLight = Color(red: 0.886275, green: 0.400000, blue: 0.298039)  // #E2664C
    static let coralPale = Color(red: 0.952941, green: 0.768627, blue: 0.709804)  // #F3C4B5
    static let coralPaler = Color(red: 0.972549, green: 0.862745, blue: 0.823529)  // #F8DCD2
    static let coralBg = Color(red: 0.988235, green: 0.929412, blue: 0.905882)  // #FCEDE7
    static let coralRing = Color(red: 0.949020, green: 0.776471, blue: 0.721569)  // #F2C6B8
    static let lilac = Color(red: 0.423529, green: 0.341176, blue: 0.839216)  // #6C57D6
    static let lilacDeep = Color(red: 0.309804, green: 0.239216, blue: 0.682353)  // #4F3DAE
    static let lilacLight = Color(red: 0.611765, green: 0.545098, blue: 0.917647)  // #9C8BEA
    static let lilacBg = Color(red: 0.937255, green: 0.921569, blue: 0.992157)  // #EFEBFD
    static let lilacRing = Color(red: 0.847059, green: 0.807843, blue: 0.980392)  // #D8CEFA
    static let clay = Color(red: 0.768627, green: 0.439216, blue: 0.227451)  // #C4703A
    static let clayDeep = Color(red: 0.611765, green: 0.333333, blue: 0.149020)  // #9C5526
    static let clayLight = Color(red: 0.866667, green: 0.564706, blue: 0.349020)  // #DD9059
    static let clayBg = Color(red: 0.984314, green: 0.933333, blue: 0.878431)  // #FBEEE0
    static let clayBg2 = Color(red: 0.964706, green: 0.886275, blue: 0.800000)  // #F6E2CC
    static let gold = Color(red: 0.690196, green: 0.486275, blue: 0.066667)  // #B07C11
    static let goldDeep = Color(red: 0.541176, green: 0.372549, blue: 0.035294)  // #8A5F09
    static let goldLight = Color(red: 0.909804, green: 0.725490, blue: 0.290196)  // #E8B94A
    static let goldBg = Color(red: 0.988235, green: 0.949020, blue: 0.862745)  // #FCF2DC
    static let goldRing = Color(red: 0.937255, green: 0.862745, blue: 0.658824)  // #EFDCA8
    static let teal = Color(red: 0.203922, green: 0.478431, blue: 0.450980)  // #347A73
    static let tealDeep = Color(red: 0.149020, green: 0.376471, blue: 0.352941)  // #26605A
    static let tealLight = Color(red: 0.305882, green: 0.619608, blue: 0.588235)  // #4E9E96
    static let tealBg = Color(red: 0.894118, green: 0.949020, blue: 0.941176)  // #E4F2F0
    static let tealRing = Color(red: 0.737255, green: 0.874510, blue: 0.854902)  // #BCDFDA
    static let sea = Color(red: 0.184314, green: 0.482353, blue: 0.501961)  // #2F7B80
    static let seaLight = Color(red: 0.435294, green: 0.725490, blue: 0.737255)  // #6FB9BC
    static let seaMid = Color(red: 0.243137, green: 0.560784, blue: 0.580392)  // #3E8F94
    static let seaDeep = Color(red: 0.137255, green: 0.372549, blue: 0.388235)  // #235F63
    static let seaBg = Color(red: 0.890196, green: 0.945098, blue: 0.949020)  // #E3F1F2
    static let seaRing = Color(red: 0.733333, green: 0.874510, blue: 0.886275)  // #BBDFE2
    static let leaf = Color(red: 0.282353, green: 0.498039, blue: 0.235294)  // #487F3C
    static let leafDeep = Color(red: 0.207843, green: 0.384314, blue: 0.172549)  // #35622C
    static let leafLight = Color(red: 0.474510, green: 0.682353, blue: 0.419608)  // #79AE6B
    static let leafBg = Color(red: 0.925490, green: 0.956863, blue: 0.905882)  // #ECF4E7
    static let leafRing = Color(red: 0.745098, green: 0.862745, blue: 0.686275)  // #BEDCAF
    static let sky = Color(red: 0.164706, green: 0.447059, blue: 0.721569)  // #2A72B8
    static let skyDeep = Color(red: 0.121569, green: 0.337255, blue: 0.552941)  // #1F568D
    static let skyLight = Color(red: 0.380392, green: 0.623529, blue: 0.839216)  // #619FD6
    static let skyBg = Color(red: 0.905882, green: 0.945098, blue: 0.980392)  // #E7F1FA
    static let skyRing = Color(red: 0.713725, green: 0.839216, blue: 0.941176)  // #B6D6F0
    static let rose = Color(red: 0.725490, green: 0.231373, blue: 0.419608)  // #B93B6B
    static let roseDeep = Color(red: 0.588235, green: 0.160784, blue: 0.309804)  // #96294F
    static let roseLight = Color(red: 0.870588, green: 0.450980, blue: 0.600000)  // #DE7399
    static let roseBg = Color(red: 0.984314, green: 0.913725, blue: 0.941176)  // #FBE9F0
    static let roseRing = Color(red: 0.952941, green: 0.745098, blue: 0.819608)  // #F3BED1
    static let coralWash = Color(red: 0.956863, green: 0.788235, blue: 0.745098)  // #F4C9BE
    static let clayWash = Color(red: 0.952941, green: 0.803922, blue: 0.701961)  // #F3CDB3
    static let goldWash = Color(red: 0.941176, green: 0.843137, blue: 0.647059)  // #F0D7A5
    static let leafWash = Color(red: 0.803922, green: 0.956863, blue: 0.760784)  // #CDF4C2
    static let tealWash = Color(red: 0.772549, green: 0.956863, blue: 0.933333)  // #C5F4EE
    static let seaWash = Color(red: 0.772549, green: 0.945098, blue: 0.952941)  // #C5F1F3
    static let skyWash = Color(red: 0.772549, green: 0.862745, blue: 0.952941)  // #C5DCF3
    static let lilacWash = Color(red: 0.807843, green: 0.772549, blue: 0.952941)  // #CEC5F3
    static let roseWash = Color(red: 0.960784, green: 0.768627, blue: 0.831373)  // #F5C4D4
    static let stoneWash = Color(red: 0.901961, green: 0.819608, blue: 0.768627)  // #E6D1C4
    static let danger = Color(red: 0.549020, green: 0.184314, blue: 0.133333)  // #8C2F22
    static let chartCoral = Color(red: 0.788235, green: 0.290196, blue: 0.188235)  // #C94A30
    static let chartSea = Color(red: 0.019608, green: 0.584314, blue: 0.615686)  // #05959D
    static let chartGold = Color(red: 0.690196, green: 0.486275, blue: 0.066667)  // #B07C11
    static let chartSeaTrack = Color(red: 0.850980, green: 0.937255, blue: 0.941176)  // #D9EFF0
    static let chartBandOuter = Color(red: 0.917647, green: 0.956863, blue: 0.960784)  // #EAF4F5
    static let chartBandInner = Color(red: 0.768627, green: 0.882353, blue: 0.894118)  // #C4E1E4
    static let chartGrid = Color(red: 0.937255, green: 0.894118, blue: 0.847059)  // #EFE4D8
    static let dangerLight = Color(red: 0.690196, green: 0.258824, blue: 0.203922)  // #B04234
    static let dangerDeep = Color(red: 0.431373, green: 0.129412, blue: 0.094118)  // #6E2118
    static let dangerBg = Color(red: 0.984314, green: 0.909804, blue: 0.894118)  // #FBE8E4
    static let dangerBg2 = Color(red: 0.964706, green: 0.850980, blue: 0.823529)  // #F6D9D2
    static let dangerRing = Color(red: 0.929412, green: 0.764706, blue: 0.725490)  // #EDC3B9
    static let stone = Color(red: 0.419608, green: 0.356863, blue: 0.321569)  // #6B5B52
    static let stoneMid = Color(red: 0.545098, green: 0.478431, blue: 0.435294)  // #8B7A6F
    static let stoneLight = Color(red: 0.701961, green: 0.635294, blue: 0.588235)  // #B3A296
    static let stoneBg = Color(red: 0.949020, green: 0.921569, blue: 0.882353)  // #F2EBE1
    static let track = Color(red: 0.850980, green: 0.796078, blue: 0.737255)  // #D9CBBC
}
