import SwiftUI

struct ErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 20) {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 56))
                    .foregroundStyle(.secondary)
                Text("Couldn't Connect")
                    .font(.title2.bold())
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                Button(action: retry) {
                    Label("Try Again", systemImage: "arrow.clockwise")
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(.indigo)
                        .clipShape(Capsule())
                }
            }
            .foregroundStyle(.white)
        }
    }
}
