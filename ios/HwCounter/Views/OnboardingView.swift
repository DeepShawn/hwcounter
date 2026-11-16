import SwiftUI

/// 新手引导：第一次打开时显示，可左右滑动，随时“跳过”；设置 → 帮助 可再看
struct GuideStep: Identifiable {
    let id = UUID()
    let emoji: String, title: String, body: String, tips: [String], colors: [Color]
}

let guideSteps: [GuideStep] = [
    GuideStep(emoji: "📚", title: "欢迎使用作业通", body: "收作业、记积分、看统计。安卓、iPad、网页三端数据自动同步。",
              tips: ["左侧栏：作业、热力图、积分、名单、设置", "随时可以在 设置 → 帮助 再看一遍"], colors: [Color(red: 0.20, green: 0.51, blue: 1), Color(red: 0.43, green: 0.36, blue: 1)]),
    GuideStep(emoji: "✅", title: "登记作业", body: "在“作业”里新建一次作业，然后登记每位同学的情况。",
              tips: ["点一下同学：已交 ↔ 未登记", "长按或右键同学：优秀、迟交、未交、请假、复审不合格", "“多选”可以一次登记一批；也能识别统计表、语音登记"], colors: [Color(red: 0.07, green: 0.76, blue: 0.63), Color(red: 0.18, green: 0.56, blue: 0.92)]),
    GuideStep(emoji: "🧮", title: "积分自动计算", body: "登记完积分自动算好，与安卓、网页完全一致。",
              tips: ["未交连续加倍扣分，复审不合格再翻倍", "连续按时交满 5 次后每次 +1；考试按得分率加减", "组长奖惩、小组排名按周期自动计入"], colors: [Color(red: 1, green: 0.65, blue: 0.17), Color(red: 1, green: 0.37, blue: 0.38)]),
    GuideStep(emoji: "👥", title: "名单与小组", body: "在“名单”里添加同学、填写学号和小组。",
              tips: ["积分页可以手动加减分、积分重置", "小组对抗分析、股票模式、结算在安卓上使用", "计分规则在 设置 里调整，三端同步"], colors: [Color(red: 0.61, green: 0.35, blue: 0.71), Color(red: 0.20, green: 0.60, blue: 0.86)]),
    GuideStep(emoji: "☁️", title: "同步与设置", body: "在 设置 里登录账号，改动会自动上传，多台设备之间同步。",
              tips: ["网页版只能查看，适合分享给同学和家长", "可以备份全部数据（JSON，与安卓、网页通用）"], colors: [Color(red: 0.22, green: 0.28, blue: 0.31), Color(red: 0.56, green: 0.64, blue: 0.68)]),
]

struct OnboardingView: View {
    var onDone: () -> Void
    @State private var page = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("新手引导 \(page + 1)/\(guideSteps.count)").font(.subheadline).foregroundStyle(.secondary)
                Spacer()
                Button("跳过", action: onDone)
            }
            .padding(.horizontal, 24).padding(.top, 20)
            TabView(selection: $page) {
                ForEach(Array(guideSteps.enumerated()), id: \.offset) { i, st in
                    VStack(spacing: 16) {
                        Text(st.emoji).font(.system(size: 46))
                            .frame(width: 96, height: 96)
                            .background(LinearGradient(colors: st.colors, startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 28))
                        Text(st.title).font(.title2.bold())
                        Text(st.body).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(st.tips, id: \.self) { t in
                                HStack(alignment: .top, spacing: 10) {
                                    Circle().fill(st.colors[0]).frame(width: 7, height: 7).padding(.top, 7)
                                    Text(t).font(.callout)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 32).padding(.top, 12)
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))
            HStack(spacing: 12) {
                if page > 0 {
                    Button("上一步") { withAnimation { page -= 1 } }.buttonStyle(.bordered).frame(maxWidth: .infinity)
                }
                Button(page == guideSteps.count - 1 ? "开始使用" : "下一步") {
                    if page == guideSteps.count - 1 { onDone() } else { withAnimation { page += 1 } }
                }
                .buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
            }
            .padding(24)
        }
        .frame(minHeight: 560)
    }
}
