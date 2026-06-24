import SwiftUI

struct ContentView: View {
    @StateObject private var router = ServerRouter()
    @StateObject private var webModel = WebViewModel()
    @StateObject private var lockManager = AppLockManager()
    @State private var showSettings = false
    @State private var lastLoadedURL: URL?

    var body: some View {
        ZStack {
            NavigationStack {
                ZStack(alignment: .top) {
                    OdysseusWebView(model: webModel)
                        .ignoresSafeArea(.container, edges: .all)

                    if webModel.isLoading && webModel.loadProgress < 1.0 {
                        ProgressView(value: webModel.loadProgress)
                            .progressViewStyle(.linear)
                            .tint(.indigo)
                            .frame(height: 2)
                            .transition(.opacity)
                    }

                    if webModel.hasError {
                        ErrorView(message: webModel.errorMessage) {
                            webModel.reload()
                        }
                        .transition(.opacity)
                    }
                }
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        serverBadge
                    }
                    ToolbarItem(placement: .principal) {
                        Text(webModel.pageTitle)
                            .font(.headline)
                            .lineLimit(1)
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Menu {
                            Button(action: { webModel.reload() }) {
                                Label("Reload", systemImage: "arrow.clockwise")
                            }
                            Button(action: { webModel.goBack() }) {
                                Label("Back", systemImage: "chevron.left")
                            }
                            .disabled(!webModel.canGoBack)
                            Button(action: { webModel.goForward() }) {
                                Label("Forward", systemImage: "chevron.right")
                            }
                            .disabled(!webModel.canGoForward)
                            Divider()
                            Button(action: { lockManager.lock() }) {
                                Label("Lock", systemImage: "lock.fill")
                            }
                            Button(action: { showSettings = true }) {
                                Label("Settings", systemImage: "gear")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                        }
                    }
                }
            }
            .preferredColorScheme(.dark)

            // Lock screen overlays everything
            if lockManager.isLocked {
                LockScreenView(lockManager: lockManager)
                    .ignoresSafeArea()
                    .transition(.opacity)
                    .zIndex(10)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(router: router)
        }
        .onChange(of: router.currentURL) { _, newURL in
            if newURL != lastLoadedURL {
                lastLoadedURL = newURL
                webModel.load(newURL)
            }
        }
        .onChange(of: lockManager.isLocked) { _, locked in
            // Re-attempt auto-login after unlocking in case session expired
            if !locked { webModel.attemptAutoLogin() }
        }
        .onAppear {
            lastLoadedURL = router.currentURL
            webModel.load(router.currentURL)
        }
    }

    private var serverBadge: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(router.isLocal ? Color.green : Color.blue)
                .frame(width: 7, height: 7)
            Text(router.isLocal ? "Local" : "Remote")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }
}
