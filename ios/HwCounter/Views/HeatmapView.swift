import SwiftUI

/// 全班作业热力图：每行一位同学，每格一次作业（最近 30 次）
struct HeatmapView: View {
    @EnvironmentObject var store: Store
    private let cell: CGFloat = 24

    var body: some View {
        Group {
            if let c = store.data.currentClass, !c.assignments.isEmpty, !c.students.isEmpty {
                let recent = Array(c.assignments.sorted { $0.createdAt < $1.createdAt }.suffix(30))
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        legend
                        Text("每行一位同学，每格一次作业，点格子进入该次作业").font(.caption).foregroundStyle(.secondary)
                        ScrollView(.horizontal, showsIndicators: true) {
                            Grid(alignment: .leading, horizontalSpacing: 4, verticalSpacing: 4) {
                                GridRow {
                                    Text("")
                                    ForEach(recent) { a in
                                        NavigationLink(value: a.id) {
                                            Text(md(a.date)).font(.system(size: 9)).foregroundStyle(.secondary).frame(width: cell)
                                        }
                                    }
                                    Text("交齐率").font(.caption2).foregroundStyle(.secondary)
                                }
                                ForEach(c.students) { st in
                                    GridRow {
                                        Text(st.name).font(.caption).lineLimit(1).frame(width: 72, alignment: .leading)
                                        ForEach(recent) { a in
                                            let s = a.status(st.id)
                                            NavigationLink(value: a.id) {
                                                RoundedRectangle(cornerRadius: 5)
                                                    .fill(s.color.opacity(s == .NONE ? 0.25 : 0.9))
                                                    .frame(width: cell, height: cell)
                                            }
                                            .buttonStyle(.plain)
                                        }
                                        rateText(st, recent)
                                    }
                                }
                            }
                            .padding(.bottom, 8)
                        }
                    }
                    .padding()
                    .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
                    .padding()
                }
                .navigationDestination(for: String.self) { id in AssignmentDetailView(assignmentId: id) }
            } else {
                EmptyHint(emoji: "📈", title: "还没有数据", detail: "布置并登记几次作业后，这里会出现全班热力图")
            }
        }
        .navigationTitle("🔥 作业热力图")
    }

    private var legend: some View {
        HStack(spacing: 12) {
            ForEach(HwStatus.allCases) { s in
                HStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 3).fill(s.color.opacity(s == .NONE ? 0.3 : 0.9)).frame(width: 12, height: 12)
                    Text(s.label).font(.caption)
                }
            }
        }
    }

    private func rateText(_ st: Student, _ recent: [Assignment]) -> some View {
        var got = 0, need = 0
        for a in recent {
            let s = a.status(st.id)
            if s == .LEAVE || s == .NONE { continue }
            need += 1
            if s.handedIn { got += 1 }
        }
        let r = need == 0 ? nil : Int((Double(got) / Double(need) * 100).rounded())
        return Text(r.map { "\($0)%" } ?? "—").font(.caption.bold().monospacedDigit())
            .foregroundStyle(r.map { $0 < 80 ? Color.red : Color.primary } ?? Color.secondary)
    }

    private func md(_ d: Date) -> String {
        let f = DateFormatter(); f.dateFormat = "M/d"
        return f.string(from: d)
    }
}
