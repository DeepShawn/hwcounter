import Foundation

struct ScoreReset: Codable, Identifiable, Equatable {
    var id = newId()
    var assignmentId: String? = nil
    var assignmentTitle = ""
    var startAt: Int64 = 0
    var time: Int64 = nowMillis()
    var excludedAssignmentIds: [String] = []
    var ignoredScoreIds: [String] = []
    var keepManualSinceStart = true

    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? newId()
        assignmentId = try c.decodeIfPresent(String.self, forKey: .assignmentId)
        assignmentTitle = try c.decodeIfPresent(String.self, forKey: .assignmentTitle) ?? ""
        startAt = try c.decodeIfPresent(Int64.self, forKey: .startAt) ?? 0
        time = try c.decodeIfPresent(Int64.self, forKey: .time) ?? 0
        excludedAssignmentIds = try c.decodeIfPresent([String].self, forKey: .excludedAssignmentIds) ?? []
        ignoredScoreIds = try c.decodeIfPresent([String].self, forKey: .ignoredScoreIds) ?? []
        keepManualSinceStart = try c.decodeIfPresent(Bool.self, forKey: .keepManualSinceStart) ?? true
    }
    func includes(_ a: Assignment) -> Bool { a.createdAt >= startAt && !excludedAssignmentIds.contains(a.id) }
    func includes(_ e: ScoreEntry) -> Bool {
        !ignoredScoreIds.contains(e.id) && !(e.isResetAdjustment && e.time < time) &&
            e.time >= (keepManualSinceStart ? startAt : time)
    }
}

extension ScoreEntry {
    var isResetAdjustment: Bool {
        kind == "reset" || reason.range(of: "^重置分数至 [+-]?[0-9]+(?:\\.[0-9]+)?$", options: .regularExpression) != nil
    }
}

extension SchoolClass {
    var activeScoreReset: ScoreReset? {
        let latest = scoreResets.max { $0.time == $1.time ? $0.id < $1.id : $0.time < $1.time }
        return latest?.assignmentId == nil ? nil : latest
    }
    func countsTowardScore(_ a: Assignment) -> Bool { activeScoreReset?.includes(a) ?? true }
    func countsTowardScore(_ e: ScoreEntry) -> Bool { activeScoreReset?.includes(e) ?? true }
    func scoreBoundaryIndex(_ assignments: [Assignment]) -> Int? {
        guard let reset = activeScoreReset else { return nil }
        return assignments.indices.first { i in
            reset.includes(assignments[i]) && (assignments[i].id == reset.assignmentId || (i > 0 && !reset.includes(assignments[i - 1])))
        }
    }

    func startingScorePeriod(_ assignmentId: String, keepManualSinceStart: Bool = true, time: Int64 = nowMillis()) -> SchoolClass {
        let ordered = assignments.sorted { $0.createdAt < $1.createdAt }
        guard let index = ordered.firstIndex(where: { $0.id == assignmentId }) else { return self }
        let a = ordered[index]
        var reset = ScoreReset()
        reset.assignmentId = a.id; reset.assignmentTitle = a.title; reset.startAt = a.createdAt; reset.time = time
        reset.keepManualSinceStart = keepManualSinceStart
        reset.excludedAssignmentIds = ordered.prefix(index).filter { $0.createdAt == a.createdAt }.map(\.id)
        reset.ignoredScoreIds = scores.filter { $0.time >= time && (!keepManualSinceStart || $0.isResetAdjustment) }.map(\.id)
        var result = self; result.scoreResets.append(reset); return result
    }
    func restoringScoreHistory(time: Int64 = nowMillis()) -> SchoolClass {
        var event = ScoreReset(); event.time = time
        var result = self; result.scoreResets.append(event); return result
    }
}
