import SwiftUI

struct SettingsView: View {
    @ObservedObject var router: ServerRouter
    @Environment(\.dismiss) private var dismiss

    @State private var username = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var savedBanner = false
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Active server") {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(router.isLocal ? Color.green : Color.blue)
                                .frame(width: 8, height: 8)
                            Text(router.isLocal ? "Local (LAN)" : "Remote (WAN)")
                                .foregroundStyle(.secondary)
                        }
                    }
                    LabeledContent("URL") {
                        Text(router.currentURL.absoluteString)
                            .foregroundStyle(.secondary)
                            .font(.caption)
                    }
                } header: {
                    Text("Connection")
                } footer: {
                    Text("Switches to 192.168.1.73:7000 automatically when on your home Wi-Fi.")
                }

                Section {
                    TextField("Username or email", text: $username)
                        .textContentType(.username)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)

                    HStack {
                        Group {
                            if showPassword {
                                TextField("Password", text: $password)
                            } else {
                                SecureField("Password", text: $password)
                            }
                        }
                        .textContentType(.password)

                        Button(action: { showPassword.toggle() }) {
                            Image(systemName: showPassword ? "eye.slash" : "eye")
                                .foregroundStyle(.secondary)
                        }
                    }

                    Button(action: saveCredentials) {
                        HStack {
                            Label("Save & enable auto-login", systemImage: "key.fill")
                            Spacer()
                            if savedBanner {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.green)
                                    .transition(.scale.combined(with: .opacity))
                            }
                        }
                    }
                    .disabled(username.isEmpty || password.isEmpty)

                    if KeychainService.load() != nil {
                        Button(role: .destructive, action: { confirmDelete = true }) {
                            Label("Remove saved credentials", systemImage: "trash")
                        }
                    }
                } header: {
                    Text("Auto-login")
                } footer: {
                    Text("Credentials are stored in the iOS Keychain and injected after Face ID / passcode authentication. 2FA prompts from Odysseus will still appear if enabled.")
                }

                Section("About") {
                    LabeledContent("App", value: "Odysseus for iOS")
                    LabeledContent("Remote", value: "finchwire.site")
                    LabeledContent("Local", value: "192.168.1.73")
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear(perform: loadExistingCredentials)
            .confirmationDialog("Remove saved credentials?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Remove", role: .destructive) {
                    KeychainService.delete()
                    username = ""
                    password = ""
                }
                Button("Cancel", role: .cancel) {}
            }
        }
    }

    private func loadExistingCredentials() {
        if let creds = KeychainService.load() {
            username = creds.username
            password = creds.password
        }
    }

    private func saveCredentials() {
        KeychainService.save(OdysseusCredentials(username: username, password: password))
        withAnimation {
            savedBanner = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation { savedBanner = false }
        }
    }
}
