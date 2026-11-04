import Foundation
import Dispatch

/// Serialize file access so encoding/writes do not block taps or page transitions.
/// A read waits for preceding writes, including immediately after logout or restoration.
enum LocalJSONFile {
    private static let queue = DispatchQueue(label: "com.deepshawn.hwcounter.files", qos: .utility)

    static func read<T: Decodable>(_ type: T.Type, from url: URL) -> T? {
        queue.sync {
            guard let bytes = try? Data(contentsOf: url) else { return nil }
            return try? JSONDecoder().decode(type, from: bytes)
        }
    }

    static func write<T: Encodable>(_ value: T, to url: URL) {
        queue.async {
            guard let bytes = try? JSONEncoder().encode(value) else { return }
            try? bytes.write(to: url, options: .atomic)
        }
    }

    static func flush() { queue.sync {} }
}
