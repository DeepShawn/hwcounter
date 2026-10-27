import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// 与安卓版、网页版同名的配色主题
struct Palette: Identifiable {
    let id: String
    let name: String
    let a: UInt32
    let b: UInt32
    var primary: Color { Color(hex: a) }
    var secondary: Color { Color(hex: b) }
    var gradient: LinearGradient { LinearGradient(colors: [primary, secondary], startPoint: .topLeading, endPoint: .bottomTrailing) }

    static let all: [Palette] = [
        Palette(id: "mint", name: "🌿 薄荷清新", a: 0x12C2A0, b: 0x2F8FEA),
        Palette(id: "academy", name: "🎓 学院蓝", a: 0x3A6BFF, b: 0x6A3DF0),
        Palette(id: "sakura", name: "🌸 樱花粉", a: 0xFF7EA5, b: 0xB57BFF),
        Palette(id: "sunset", name: "🌅 暖阳橙", a: 0xFFA62B, b: 0xFF5E62),
        Palette(id: "neon", name: "🌃 赛博霓虹", a: 0x7F5CFF, b: 0x00D4FF),
        Palette(id: "forest", name: "🌲 森林物语", a: 0x4C8C3F, b: 0x1F5E54),
        Palette(id: "paper", name: "📒 纸本手账", a: 0xC79A63, b: 0x8B5E34),
        Palette(id: "grape", name: "🍇 葡萄汽水", a: 0x9B59B6, b: 0x3498DB),
        Palette(id: "ink", name: "🖌️ 水墨极简", a: 0x37474F, b: 0x90A4AE),
    ]
    static func of(_ id: String) -> Palette { all.first { $0.id == id } ?? all[0] }
}

extension HwStatus {
    var color: Color { Color(hex: hex) }
}

/// 圆环进度
struct Ring: View {
    var value: Double
    var color: Color
    var line: CGFloat = 6
    var body: some View {
        ZStack {
            Circle().stroke(color.opacity(0.15), lineWidth: line)
            Circle().trim(from: 0, to: max(0.001, min(1, value)))
                .stroke(color, style: StrokeStyle(lineWidth: line, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text("\(Int((value * 100).rounded()))%").font(.system(size: 11, weight: .bold, design: .rounded))
        }
    }
}

struct Chip: View {
    var text: String
    var color: Color
    var body: some View {
        Text(text).font(.caption.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(color.opacity(0.15), in: Capsule())
            .foregroundStyle(color)
    }
}

/// 未登录时底部的预览提示条
struct PreviewBanner: View {
    @EnvironmentObject var store: Store
    var onLogin: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Text("👀").font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text("预览模式").font(.subheadline.bold())
                Text(store.sync.previewState == "none" ? "老师还没有公开数据" : store.sync.previewState == "error" ? "暂时连不上云端，显示的是上次的数据" : "只能查看，登录后才能修改")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button("登录", action: onLogin).buttonStyle(.borderedProminent)
        }
        .padding(12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal).padding(.bottom, 8)
    }
}

struct EmptyHint: View {
    var emoji: String
    var title: String
    var detail: String
    var body: some View {
        VStack(spacing: 10) {
            Text(emoji).font(.system(size: 56))
            Text(title).font(.title3.bold())
            Text(detail).font(.callout).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
