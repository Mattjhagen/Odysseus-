import Foundation
import LocalAuthentication
import SwiftUI

@MainActor
final class AppLockManager: ObservableObject {
    @Published var isLocked = true
    @Published var authError: String?
    @Published var biometryType: LABiometryType = .none

    private let context = LAContext()

    init() {
        var error: NSError?
        context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
        biometryType = context.biometryType
    }

    func authenticate() {
        authError = nil
        let ctx = LAContext()
        var error: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            authError = error?.localizedDescription ?? "Authentication unavailable"
            return
        }

        let reason = "Unlock Odysseus"
        ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, err in
            DispatchQueue.main.async {
                if success {
                    withAnimation(.easeOut(duration: 0.25)) {
                        self.isLocked = false
                    }
                    self.authError = nil
                } else {
                    self.authError = err?.localizedDescription
                }
            }
        }
    }

    func lock() {
        withAnimation(.easeIn(duration: 0.2)) {
            isLocked = true
        }
        authError = nil
    }
}
