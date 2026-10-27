import Foundation
import Speech
import AVFoundation

/// 连续中文语音识别（SFSpeechRecognizer）。每段话结束后自动重新开始，适合一个个念名字。
@MainActor
final class SpeechListener: ObservableObject {
    @Published var listening = false
    @Published var partial = ""
    @Published var error: String? = nil

    var onFinal: ((String) -> Void)?

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-CN"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var silenceTimer: Timer?
    private var segment = 0

    func start() {
        error = nil
        SFSpeechRecognizer.requestAuthorization { st in
            Task { @MainActor in
                guard st == .authorized else { self.error = "请在“设置 → 作业通”里允许语音识别"; return }
                AVAudioSession.sharedInstance().requestRecordPermission { ok in
                    Task { @MainActor in
                        if ok { self.begin() } else { self.error = "请在“设置 → 作业通”里允许使用麦克风" }
                    }
                }
            }
        }
    }

    private func begin() {
        guard let recognizer, recognizer.isAvailable else { error = "语音识别暂不可用（需联网或下载中文离线语音）"; return }
        do {
            let s = AVAudioSession.sharedInstance()
            try s.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try s.setActive(true, options: .notifyOthersOnDeactivation)
            let input = engine.inputNode
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { [weak self] buf, _ in
                self?.request?.append(buf)
            }
            engine.prepare()
            try engine.start()
            listening = true
            newSegment()
        } catch {
            self.error = "无法启动麦克风：\(error.localizedDescription)"
            stop()
        }
    }

    private func newSegment() {
        guard listening, let recognizer else { return }
        task?.cancel()
        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition { req.requiresOnDeviceRecognition = false }
        request = req
        partial = ""
        segment += 1
        let seg = segment
        task = recognizer.recognitionTask(with: req) { [weak self] result, err in
            Task { @MainActor in
                // 只处理当前这一段的回调（取消旧段时也会回调）
                guard let self, seg == self.segment else { return }
                if let result {
                    self.partial = result.bestTranscription.formattedString
                    self.armSilence()
                    if result.isFinal { self.finish() }
                } else if err != nil, self.listening {
                    // 静音超时等：稍等后重新开一段（避免出错时空转）
                    if !self.partial.isEmpty { self.finish(); return }
                    self.segment += 1
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                        Task { @MainActor in if self?.listening == true { self?.newSegment() } }
                    }
                }
            }
        }
    }

    /// 停顿 1.2 秒视为一句话结束
    private func armSilence() {
        silenceTimer?.invalidate()
        silenceTimer = Timer.scheduledTimer(withTimeInterval: 1.2, repeats: false) { [weak self] _ in
            Task { @MainActor in self?.finish() }
        }
    }

    private func finish() {
        silenceTimer?.invalidate()
        segment += 1
        let text = partial.trimmingCharacters(in: .whitespaces)
        partial = ""
        request?.endAudio()
        task?.cancel()
        task = nil
        if !text.isEmpty { onFinal?(text) }
        if listening { newSegment() }
    }

    func stop() {
        listening = false
        segment += 1
        silenceTimer?.invalidate()
        if engine.isRunning { engine.stop() }
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        task = nil
        request = nil
        partial = ""
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
