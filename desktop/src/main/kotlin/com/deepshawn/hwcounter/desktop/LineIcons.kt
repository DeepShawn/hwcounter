package com.deepshawn.hwcounter.desktop

import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.AlarmClock
import com.composables.icons.lucide.Armchair
import com.composables.icons.lucide.Backpack
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Book
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Calculator
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Cat
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleQuestionMark
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.ClipboardList
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Clover
import com.composables.icons.lucide.Contact
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Dices
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Flower
import com.composables.icons.lucide.Flower2
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.Grape
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Key
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Medal
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Microscope
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.MoonStar
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Notebook
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Paintbrush
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PartyPopper
import com.composables.icons.lucide.PenLine
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rainbow
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Ruler
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.School
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share
import com.composables.icons.lucide.Smile
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.SquareCheck
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Sunset
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.ThumbsUp
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TreePalm
import com.composables.icons.lucide.TreePine
import com.composables.icons.lucide.TrendingDown
import com.composables.icons.lucide.TrendingUp
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.Turtle
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.WifiOff
import com.composables.icons.lucide.Zap

/**
 * 纯线条图标（Lucide）。界面里不再用 emoji：原来写 emoji 的地方都换成这里的图标，
 * 对照表与 iPad（SF Symbols 描边款）、网页（js/icons.js）一致。
 */
fun lineIcon(key: String): ImageVector = when (key.replace("\uFE0F", "")) {
        "✅" -> Lucide.CircleCheck
        "❌" -> Lucide.CircleX
        "🌟" -> Lucide.Star
        "⭐" -> Lucide.Star
        "🐢" -> Lucide.Turtle
        "🏖" -> Lucide.TreePalm
        "⏳" -> Lucide.Hourglass
        "🎒" -> Lucide.Backpack
        "👥" -> Lucide.Users
        "👤" -> Lucide.User
        "🔍" -> Lucide.Search
        "📭" -> Lucide.Inbox
        "📝" -> Lucide.NotebookPen
        "✏" -> Lucide.Pencil
        "📚" -> Lucide.BookOpen
        "📘" -> Lucide.Book
        "📋" -> Lucide.ClipboardList
        "📊" -> Lucide.ChartColumn
        "📈" -> Lucide.TrendingUp
        "📉" -> Lucide.TrendingDown
        "➖" -> Lucide.Minus
        "📤" -> Lucide.Share
        "📥" -> Lucide.Download
        "📄" -> Lucide.FileText
        "🖼" -> Lucide.Image
        "💬" -> Lucide.MessageCircle
        "🎲" -> Lucide.Dices
        "🧹" -> Lucide.Eraser
        "👍" -> Lucide.ThumbsUp
        "🏅" -> Lucide.Medal
        "🥇" -> Lucide.Medal
        "🥈" -> Lucide.Medal
        "🥉" -> Lucide.Medal
        "🏆" -> Lucide.Trophy
        "🎯" -> Lucide.Target
        "⛔" -> Lucide.Ban
        "🕵" -> Lucide.Copy
        "📇" -> Lucide.Contact
        "⏰" -> Lucide.AlarmClock
        "☁" -> Lucide.Cloud
        "⌨" -> Lucide.Keyboard
        "🪑" -> Lucide.Armchair
        "🗑" -> Lucide.Trash2
        "☑" -> Lucide.SquareCheck
        "🗂" -> Lucide.Folder
        "🏫" -> Lucide.School
        "📷" -> Lucide.Camera
        "📸" -> Lucide.Camera
        "🤖" -> Lucide.Bot
        "⚠" -> Lucide.TriangleAlert
        "➕" -> Lucide.Plus
        "📏" -> Lucide.Ruler
        "📐" -> Lucide.Ruler
        "💡" -> Lucide.Lightbulb
        "🔬" -> Lucide.Microscope
        "✍" -> Lucide.PenLine
        "🏷" -> Lucide.Tag
        "🧾" -> Lucide.Receipt
        "🗓" -> Lucide.CalendarDays
        "📅" -> Lucide.CalendarDays
        "🎉" -> Lucide.PartyPopper
        "📒" -> Lucide.Notebook
        "🕘" -> Lucide.History
        "⚙" -> Lucide.Settings
        "👀" -> Lucide.Eye
        "📦" -> Lucide.Package
        "✨" -> Lucide.Sparkles
        "⚡" -> Lucide.Zap
        "🔄" -> Lucide.RefreshCw
        "🎤" -> Lucide.Mic
        "🎙" -> Lucide.Mic
        "🧮" -> Lucide.Calculator
        "💧" -> Lucide.Droplet
        "🌿" -> Lucide.Leaf
        "🎓" -> Lucide.GraduationCap
        "🌸" -> Lucide.Flower2
        "🌅" -> Lucide.Sunset
        "🌃" -> Lucide.MoonStar
        "🌲" -> Lucide.TreePine
        "🍇" -> Lucide.Grape
        "🖌" -> Lucide.Paintbrush
        "🎨" -> Lucide.Palette
        "🚀" -> Lucide.Rocket
        "🦄" -> Lucide.Sparkles
        "🐼" -> Lucide.Smile
        "🌈" -> Lucide.Rainbow
        "🍀" -> Lucide.Clover
        "🐯" -> Lucide.Cat
        "🌻" -> Lucide.Flower
        "🧪" -> Lucide.FlaskConical
        "🌏" -> Lucide.Globe
        "⚽" -> Lucide.Circle
        "🎵" -> Lucide.Music
        "🦊" -> Lucide.Cat
        "🔥" -> Lucide.Flame
        "❓" -> Lucide.CircleQuestionMark
        "💾" -> Lucide.Save
        "🔑" -> Lucide.Key
        "🔒" -> Lucide.Lock
        "📶" -> Lucide.WifiOff
        "🏠" -> Lucide.House
        else -> Lucide.Circle
}

/** 纯线条图标：key 是原来的 emoji（或同一张对照表里的写法） */
@Composable
fun LineIcon(key: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, description: String? = null) =
    Icon(lineIcon(key), description, modifier.size(size), tint = tint)
