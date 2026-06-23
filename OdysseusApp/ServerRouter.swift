import Foundation
import Network
import Combine

@MainActor
final class ServerRouter: ObservableObject {
    @Published var currentURL: URL
    @Published var isLocal: Bool = false

    private let remoteHost = "https://finchwire.site"
    private let localHost  = "http://192.168.1.73:7000"
    private let localIP    = "192.168.1"   // subnet prefix to detect home LAN

    private var monitor: NWPathMonitor?
    private var monitorQueue = DispatchQueue(label: "net.finchwire.odysseus.netmon")

    init() {
        currentURL = URL(string: "https://finchwire.site")!
        startMonitoring()
    }

    private func startMonitoring() {
        monitor = NWPathMonitor()
        monitor?.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let onLocal = Self.checkLocalNetwork(path, localPrefix: self.localIP)
            Task { @MainActor [weak self] in
                guard let self else { return }
                if onLocal != self.isLocal {
                    self.isLocal = onLocal
                    let base = onLocal ? self.localHost : self.remoteHost
                    self.currentURL = URL(string: base)!
                }
            }
        }
        monitor?.start(queue: monitorQueue)
    }

    private nonisolated static func checkLocalNetwork(_ path: NWPath, localPrefix: String) -> Bool {
        guard path.status == .satisfied else { return false }
        for iface in path.availableInterfaces where iface.type == .wifi {
            if let addr = getIPAddress(for: iface.name), addr.hasPrefix(localPrefix) {
                return true
            }
        }
        return false
    }

    private nonisolated static func getIPAddress(for interface: String) -> String? {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return nil }
        defer { freeifaddrs(first) }
        var ptr = first
        while true {
            let flags = Int32(ptr.pointee.ifa_flags)
            let family = ptr.pointee.ifa_addr.pointee.sa_family
            let name = String(cString: ptr.pointee.ifa_name)
            if name == interface, family == UInt8(AF_INET), flags & IFF_LOOPBACK == 0 {
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                getnameinfo(ptr.pointee.ifa_addr, socklen_t(ptr.pointee.ifa_addr.pointee.sa_len),
                            &hostname, socklen_t(hostname.count), nil, 0, NI_NUMERICHOST)
                return String(cString: hostname)
            }
            guard let next = ptr.pointee.ifa_next else { break }
            ptr = next
        }
        return nil
    }

    deinit {
        monitor?.cancel()
    }
}
