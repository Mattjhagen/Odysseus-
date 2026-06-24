import SwiftUI

@main
struct OdysseusApp: App {
    init() {
        NotificationManager.shared.requestAuthorization()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
        }
    }
}
import Foundation
import UserNotifications
import UIKit

class NotificationManager: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationManager()
    
    // Callback to pass the reply back to the WebView
    var onReplyReceived: ((String) -> Void)?

    private override init() {
        super.init()
    }

    func requestAuthorization() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                self.setupCategories()
            }
        }
        center.delegate = self
    }

    private func setupCategories() {
        let replyAction = UNTextInputNotificationAction(
            identifier: "REPLY_ACTION",
            title: "Reply",
            options: [],
            textInputButtonTitle: "Send",
            textInputPlaceholder: "Type your reply..."
        )
        
        let category = UNNotificationCategory(
            identifier: "CHAT_CATEGORY",
            actions: [replyAction],
            intentIdentifiers: [],
            options: []
        )
        
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    func showNotification(message: String) {
        // Only show if we're in the background
        guard UIApplication.shared.applicationState != .active else { return }

        let content = UNMutableNotificationContent()
        content.title = "Odysseus"
        content.body = message
        content.sound = .default
        content.categoryIdentifier = "CHAT_CATEGORY"

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )

        UNUserNotificationCenter.current().add(request)
    }

    // Delegate method to catch the text input
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == "REPLY_ACTION",
           let textResponse = response as? UNTextInputNotificationResponse {
            let replyText = textResponse.userText
            
            // Start background task to keep app alive while sending reply
            BackgroundTaskManager.shared.startTask()
            
            // Pass the reply back to WebView
            onReplyReceived?(replyText)
            
            // Show a temporary "Sending..." notification update
            let content = UNMutableNotificationContent()
            content.title = "Odysseus"
            content.body = "Sending reply..."
            content.sound = nil
            
            let request = UNNotificationRequest(identifier: "sending_update", content: content, trigger: nil)
            UNUserNotificationCenter.current().add(request)
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["sending_update"])
            }
        }
        
        completionHandler()
    }
    
    // Allow notifications while app is in foreground (if desired, though we abort above)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
import Foundation
import UIKit

class BackgroundTaskManager {
    static let shared = BackgroundTaskManager()
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    func startTask() {
        if backgroundTask != .invalid {
            endTask()
        }
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "ChatStreamTask") {
            // Expiration handler: OS is about to suspend the app.
            self.endTask()
        }
    }

    func endTask() {
        if backgroundTask != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTask)
            backgroundTask = .invalid
        }
    }
}
