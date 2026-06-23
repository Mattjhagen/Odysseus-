import Foundation
import Security

struct OdysseusCredentials {
    var username: String
    var password: String
}

enum KeychainService {
    private static let service = "site.finchwire.odysseus"
    private static let usernameKey = "odysseus_username"
    private static let passwordKey = "odysseus_password"

    static func save(_ credentials: OdysseusCredentials) {
        save(value: credentials.username, key: usernameKey)
        save(value: credentials.password, key: passwordKey)
    }

    static func load() -> OdysseusCredentials? {
        guard let username = load(key: usernameKey),
              let password = load(key: passwordKey) else { return nil }
        return OdysseusCredentials(username: username, password: password)
    }

    static func delete() {
        delete(key: usernameKey)
        delete(key: passwordKey)
    }

    private static func save(value: String, key: String) {
        let data = Data(value.utf8)
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: key
        ]
        let attrs: [CFString: Any] = [kSecValueData: data]
        if SecItemUpdate(query as CFDictionary, attrs as CFDictionary) == errSecItemNotFound {
            var add = query
            add[kSecValueData] = data
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    private static func load(key: String) -> String? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: key,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func delete(key: String) {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: key
        ]
        SecItemDelete(query as CFDictionary)
    }
}
