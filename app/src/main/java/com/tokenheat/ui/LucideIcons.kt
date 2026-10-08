package com.tokenheat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.tokenheat.R

/**
 * Lucide Icons collection tailored for TokenHeat.
 * Consistent 24dp viewBox, 2dp stroke width, rounded caps and joins.
 * Replaces Material Icons with Shadcn-aligned minimalist Lucide line icons.
 */
object Lucide {
    val ArrowLeft: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_arrow_left)
    val ArrowBack: ImageVector @Composable get() = ArrowLeft

    val Plus: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_plus)
    val Add: ImageVector @Composable get() = Plus

    val Archive: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_archive)

    val Paperclip: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_paperclip)
    val AttachFile: ImageVector @Composable get() = Paperclip

    val Sparkles: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_sparkles)
    val AutoAwesome: ImageVector @Composable get() = Sparkles

    val MessageSquare: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_message_square)
    val ChatBubble: ImageVector @Composable get() = MessageSquare

    val Check: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_check)

    val ChevronRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right)
    val ChevronDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_down)
    val ChevronUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_up)

    val Eraser: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_eraser)
    val CleaningServices: ImageVector @Composable get() = Eraser

    val X: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_x)
    val Close: ImageVector @Composable get() = X

    val Copy: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_copy)
    val ContentCopy: ImageVector @Composable get() = Copy

    val Moon: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_moon)
    val DarkMode: ImageVector @Composable get() = Moon

    val LayoutDashboard: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_layout_dashboard)
    val Dashboard: ImageVector @Composable get() = LayoutDashboard

    val Trash2: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_trash_2)
    val Delete: ImageVector @Composable get() = Trash2

    val FileText: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_file_text)
    val Description: ImageVector @Composable get() = FileText

    val Pencil: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_pencil)
    val Edit: ImageVector @Composable get() = Pencil

    val AlertCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_alert_circle)
    val Error: ImageVector @Composable get() = AlertCircle

    val Download: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_download)
    val FileDownload: ImageVector @Composable get() = Download

    val Upload: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_upload)
    val FileUpload: ImageVector @Composable get() = Upload

    val Wrench: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_wrench)
    val Handyman: ImageVector @Composable get() = Wrench

    val HelpCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_help_circle)
    val Help: ImageVector @Composable get() = HelpCircle

    val History: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_history)

    val Network: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_network)
    val Hub: ImageVector @Composable get() = Network

    val Image: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_image)

    val Key: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_key)

    val Sun: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_sun)
    val LightMode: ImageVector @Composable get() = Sun

    val Lightbulb: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_lightbulb)

    val Menu: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_menu)

    val MoreVertical: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_more_vertical)
    val MoreVert: ImageVector @Composable get() = MoreVertical

    val ExternalLink: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_external_link)
    val OpenInBrowser: ImageVector @Composable get() = ExternalLink

    val Pause: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_pause)
    val Play: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_play)

    val Brain: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_brain)
    val Psychology: ImageVector @Composable get() = Brain

    val Pin: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_pin)
    val PushPin: ImageVector @Composable get() = Pin

    val RefreshCw: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_refresh_cw)
    val Refresh: ImageVector @Composable get() = RefreshCw

    val Search: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_search)

    val Send: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_send)

    val Bot: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_bot)
    val SmartToy: ImageVector @Composable get() = Bot

    val Square: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_square)
    val Stop: ImageVector @Composable get() = Square

    val SlidersHorizontal: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_sliders_horizontal)
    val Tune: ImageVector @Composable get() = SlidersHorizontal

    val AlertTriangle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lucide_alert_triangle)
    val WarningAmber: ImageVector @Composable get() = AlertTriangle
}
