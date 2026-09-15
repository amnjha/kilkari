package com.kilkari.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The design names icons as Material Symbols Rounded glyphs. This maps those names onto the
 * Compose Material icon set so screen code can keep using the design's vocabulary; a handful
 * with no Compose equivalent are drawn locally below.
 */
object KIcons {

    /** A primary incisor — Material Symbols' `dentistry` has no Compose equivalent. */
    val Tooth: ImageVector = ImageVector.Builder(
        name = "Tooth", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(9.5f, 2f, 8.6f, 3f, 7f, 3f)
            curveTo(5f, 3f, 3.5f, 4.6f, 3.5f, 7.2f)
            curveTo(3.5f, 10.5f, 5f, 12.4f, 5.7f, 15.4f)
            lineTo(6.5f, 19.6f)
            curveTo(6.7f, 21f, 7.3f, 22f, 8.4f, 22f)
            curveTo(9.6f, 22f, 10.1f, 21f, 10.3f, 19.5f)
            lineTo(10.9f, 15.6f)
            curveTo(11f, 14.8f, 11.4f, 14.4f, 12f, 14.4f)
            curveTo(12.6f, 14.4f, 13f, 14.8f, 13.1f, 15.6f)
            lineTo(13.7f, 19.5f)
            curveTo(13.9f, 21f, 14.4f, 22f, 15.6f, 22f)
            curveTo(16.7f, 22f, 17.3f, 21f, 17.5f, 19.6f)
            lineTo(18.3f, 15.4f)
            curveTo(19f, 12.4f, 20.5f, 10.5f, 20.5f, 7.2f)
            curveTo(20.5f, 4.6f, 19f, 3f, 17f, 3f)
            curveTo(15.4f, 3f, 14.5f, 2f, 12f, 2f)
            close()
        }
    }.build()

    private val byName: Map<String, ImageVector> = mapOf(
        // Navigation
        "sunny" to Icons.Filled.WbSunny,
        "edit_note" to Icons.Filled.EditNote,
        "favorite" to Icons.Filled.Favorite,
        "payments" to Icons.Filled.Payments,
        "grid_view" to Icons.Filled.GridView,
        // Logging
        "water_drop" to Icons.Filled.WaterDrop,
        "bedtime" to Icons.Filled.Bedtime,
        "baby_changing_station" to Icons.Filled.BabyChangingStation,
        "pill" to Icons.Filled.Medication,
        "monitor_weight" to Icons.Filled.MonitorWeight,
        "dentistry" to Tooth,
        // Health
        "vaccines" to Icons.Filled.Vaccines,
        "stethoscope" to Icons.Filled.MedicalInformation,
        "medical_services" to Icons.Filled.MedicalServices,
        // Life
        "photo_camera" to Icons.Filled.PhotoCamera,
        "cake" to Icons.Filled.Cake,
        "event" to Icons.Filled.Event,
        "restaurant" to Icons.Filled.Restaurant,
        "celebration" to Icons.Filled.Celebration,
        "child_care" to Icons.Filled.ChildCare,
        "home" to Icons.Filled.Home,
        "auto_awesome" to Icons.Filled.AutoAwesome,
        // Chrome
        "arrow_back" to Icons.AutoMirrored.Filled.ArrowBack,
        "add" to Icons.Filled.Add,
        "close" to Icons.Filled.Close,
        "delete" to Icons.Filled.Delete,
        "edit" to Icons.Filled.Edit,
        "expand_more" to Icons.Filled.ExpandMore,
        "chevron_right" to Icons.Filled.ChevronRight,
        "check" to Icons.Filled.Check,
        "check_circle" to Icons.Filled.CheckCircle,
        "priority_high" to Icons.Filled.PriorityHigh,
        "schedule" to Icons.Filled.Schedule,
        "event_upcoming" to Icons.Filled.EventAvailable,
        // Documents & sharing
        "receipt_long" to Icons.AutoMirrored.Filled.ReceiptLong,
        "picture_as_pdf" to Icons.Filled.PictureAsPdf,
        "share" to Icons.Filled.Share,
        "document_scanner" to Icons.Filled.DocumentScanner,
        "open_in_new" to Icons.AutoMirrored.Filled.OpenInNew,
        "add_link" to Icons.Filled.AddLink,
        "folder_open" to Icons.Filled.FolderOpen,
        "photo_library" to Icons.Filled.PhotoLibrary,
        "table_view" to Icons.Filled.TableView,
        // More
        "timeline" to Icons.Filled.Timeline,
        "notifications" to Icons.Filled.Notifications,
        "notifications_active" to Icons.Filled.NotificationsActive,
        "backup" to Icons.Filled.Backup,
        "cloud_done" to Icons.Filled.CloudDone,
        "restore" to Icons.Filled.Restore,
        "settings" to Icons.Filled.Settings,
        // Money
        "shopping_bag" to Icons.Filled.ShoppingBag,
        "checkroom" to Icons.Filled.Checkroom,
    )

    operator fun get(name: String): ImageVector = byName[name] ?: Icons.Filled.AutoAwesome
}
