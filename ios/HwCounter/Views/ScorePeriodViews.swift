import SwiftUI

struct ScorePeriodBanner: View {
    let c: SchoolClass
    var body: some View {
        if let reset = c.activeScoreReset {
            VStack(alignment: .leading, spacing: 4) {
                Text("本轮积分起点：\(reset.assignmentTitle)（含本次）").font(.callout.bold())
                Text("之前的作业、未交次数及积分流水完整保留").font(.caption)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(12)
            .foregroundStyle(.orange).background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
        }
    }
}

struct ScorePeriodDivider: View {
    let reset: ScoreReset
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Rectangle().fill(.orange).frame(height: 3)
            Text("积分重置分界 · \(reset.assignmentTitle)（含本次）").font(.callout.bold())
            Text("以下为历史作业，仅保留记录与次数，不计入本轮积分").font(.caption)
        }.foregroundStyle(.orange).accessibilityElement(children: .combine)
    }
}

struct ScorePeriodSheet: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let classId: String
    @State private var selected = ""
    @State private var keepManual = true

    var body: some View {
        NavigationStack {
            if let c = store.data.classes.first(where: { $0.id == classId }) {
                Form {
                    Section {
                        Text("选中的作业及之后的作业重新计分，基准为 \(fmtNum(store.data.rules.initial)) 分。作业、未交次数和积分流水全部保留。")
                        Picker("计分起点作业", selection: $selected) {
                            ForEach(c.assignments.sorted { $0.createdAt > $1.createdAt }) { a in
                                Text("\(fmtDate(a.date)) · \(a.title)").tag(a.id)
                            }
                        }
                        Toggle("保留起点日期之后的手动加减分", isOn: $keepManual)
                        Text(keepManual ? "起点之前的手动分和之前的重置调整不再计入。" : "现有手动分全部转为历史记录，重置后的新加减分继续累计。")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Section {
                        Button("应用积分起点") {
                            guard !store.readOnly, c.assignments.contains(where: { $0.id == selected }) else { return }
                            store.startScorePeriod(selected, keepManualSinceStart: keepManual, classId: classId)
                            store.say("已设置积分起点，作业及未交次数已保留"); dismiss()
                        }.disabled(store.readOnly || selected.isEmpty)
                        if c.activeScoreReset != nil {
                            Button("恢复全部历史计分") {
                                guard !store.readOnly else { return }
                                store.restoreScoreHistory(classId: classId)
                                store.say("已恢复全部历史计分"); dismiss()
                            }.disabled(store.readOnly)
                        }
                    }
                }
                .navigationTitle("按作业重置积分")
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } } }
                .onAppear {
                    selected = c.activeScoreReset?.assignmentId.flatMap { id in c.assignments.contains(where: { $0.id == id }) ? id : nil } ?? c.assignments.max(by: { $0.createdAt < $1.createdAt })?.id ?? ""
                    keepManual = c.activeScoreReset?.keepManualSinceStart ?? true
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
