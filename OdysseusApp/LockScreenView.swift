import SwiftUI
import LocalAuthentication

struct LockScreenView: View {
    @ObservedObject var lockManager: AppLockManager

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 40) {
                Spacer()

                VStack(spacing: 16) {
                    Image(systemName: biometryIcon)
                        .font(.system(size: 64, weight: .ultraLight))
                        .foregroundStyle(.white)
                        .symbolEffect(.pulse, options: .repeating)

                    Text("Odysseus")
                        .font(.system(size: 32, weight: .thin, design: .serif))
                        .foregroundStyle(.white)

                    Text("finchwire.site")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.4))
                }

                Spacer()

                VStack(spacing: 16) {
                    if let error = lockManager.authError {
                        Text(error)
                            .font(.footnote)
                            .foregroundStyle(.red.opacity(0.8))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                            .transition(.opacity)
                    }

                    Button(action: { lockManager.authenticate() }) {
                        HStack(spacing: 10) {
                            Image(systemName: biometryIcon)
                            Text(unlockLabel)
                        }
                        .font(.body.weight(.medium))
                        .foregroundStyle(.black)
                        .padding(.horizontal, 36)
                        .padding(.vertical, 14)
                        .background(.white)
                        .clipShape(Capsule())
                    }
                }

                Spacer().frame(height: 60)
            }
        }
        .onAppear { lockManager.authenticate() }
    }

    private var biometryIcon: String {
        switch lockManager.biometryType {
        case .faceID:   return "faceid"
        case .touchID:  return "touchid"
        case .opticID:  return "opticid"
        default:        return "lock.fill"
        }
    }

    private var unlockLabel: String {
        switch lockManager.biometryType {
        case .faceID:   return "Unlock with Face ID"
        case .touchID:  return "Unlock with Touch ID"
        default:        return "Unlock with Passcode"
        }
    }
}
