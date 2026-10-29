import Foundation
import Vision
import UIKit

/// 苹果自带的 Vision 文字识别（离线，支持中文）
enum OCR {
    static func recognize(_ image: UIImage) async -> [String] {
        guard let cg = image.cgImage else { return [] }
        let orientation = CGImagePropertyOrientation(image.imageOrientation)
        return await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                let req = VNRecognizeTextRequest()
                req.recognitionLevel = .accurate
                req.recognitionLanguages = ["zh-Hans", "en-US"]
                req.usesLanguageCorrection = false
                try? VNImageRequestHandler(cgImage: cg, orientation: orientation).perform([req])
                let lines = (req.results ?? []).compactMap { $0.topCandidates(1).first?.string }
                cont.resume(returning: lines)
            }
        }
    }
}

extension CGImagePropertyOrientation {
    init(_ o: UIImage.Orientation) {
        switch o {
        case .up: self = .up
        case .down: self = .down
        case .left: self = .left
        case .right: self = .right
        case .upMirrored: self = .upMirrored
        case .downMirrored: self = .downMirrored
        case .leftMirrored: self = .leftMirrored
        case .rightMirrored: self = .rightMirrored
        @unknown default: self = .up
        }
    }
}

extension OCR {
    /// 座位表照片 → 文字片段（像素坐标，左上角为原点），每个字用 Vision 给出的字符框算横向位置
    static func recognizePieces(_ image: UIImage) async -> [SeatingLogic.Piece] {
        guard let cg = image.cgImage else { return [] }
        let orientation = CGImagePropertyOrientation(image.imageOrientation)
        // Vision 的坐标是相对“显示方向”的图片，宽高按方向换算
        let rotated = [CGImagePropertyOrientation.left, .right, .leftMirrored, .rightMirrored].contains(orientation)
        let W = Double(rotated ? cg.height : cg.width), H = Double(rotated ? cg.width : cg.height)
        return await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                let req = VNRecognizeTextRequest()
                req.recognitionLevel = .accurate
                req.recognitionLanguages = ["zh-Hans", "en-US"]
                req.usesLanguageCorrection = false
                try? VNImageRequestHandler(cgImage: cg, orientation: orientation).perform([req])
                var out: [SeatingLogic.Piece] = []
                for obs in req.results ?? [] {
                    guard let cand = obs.topCandidates(1).first else { continue }
                    let s = cand.string
                    let b = obs.boundingBox
                    var text = "", xs: [Double] = []
                    var idx = s.startIndex
                    while idx < s.endIndex {
                        let next = s.index(after: idx)
                        let ch = s[idx]
                        if !ch.isWhitespace {
                            text.append(ch)
                            if let box = try? cand.boundingBox(for: idx..<next) { xs.append(Double(box.boundingBox.midX) * W) }
                            else { xs.append(Double(b.minX) * W + Double(b.width) * W * (Double(s.distance(from: s.startIndex, to: idx)) + 0.5) / Double(max(1, s.count))) }
                        } else if let last = text.last, last.isASCII && (last.isLetter || last.isNumber) {
                            text.append(" "); xs.append(xs.last ?? Double(b.minX) * W)
                        }
                        idx = next
                    }
                    if text.trimmingCharacters(in: .whitespaces).isEmpty { continue }
                    out.append(SeatingLogic.Piece(text: text, x: Double(b.minX) * W, y: (1 - Double(b.maxY)) * H,
                                                  w: Double(b.width) * W, h: Double(b.height) * H, charX: xs))
                }
                cont.resume(returning: out)
            }
        }
    }
}
