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
