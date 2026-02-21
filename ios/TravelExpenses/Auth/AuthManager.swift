import Foundation
import LocalAuthentication
import CryptoKit

/// Auto-lock timeout options, matching the Android implementation.
enum AutoLockTimeout: String, CaseIterable, Identifiable {
    case immediate   = "IMMEDIATE"
    case oneMinute   = "ONE_MINUTE"
    case fiveMinutes = "FIVE_MINUTES"
    case fifteenMinutes = "FIFTEEN_MINUTES"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .immediate:      return "Immediate"
        case .oneMinute:      return "1 minute"
        case .fiveMinutes:    return "5 minutes"
        case .fifteenMinutes: return "15 minutes"
        }
    }

    var seconds: Int {
        switch self {
        case .immediate:      return 0
        case .oneMinute:      return 60
        case .fiveMinutes:    return 300
        case .fifteenMinutes: return 900
        }
    }
}

/// Centralises all security state for the app:
/// - 6-digit PIN stored in Keychain as PBKDF2-SHA256 hash
/// - Biometric (Face ID / Touch ID) via LocalAuthentication
/// - Auto-lock timeout persisted in UserDefaults
@MainActor
final class AuthManager: ObservableObject {

    // MARK: - Published state
    @Published private(set) var isPinSet: Bool = false
    @Published var biometricEnabled: Bool = false {
        didSet { UserDefaults.standard.set(biometricEnabled, forKey: Keys.biometricEnabled) }
    }
    @Published var autoLockTimeout: AutoLockTimeout = .immediate {
        didSet { UserDefaults.standard.set(autoLockTimeout.rawValue, forKey: Keys.autoLockTimeout) }
    }
    @Published private(set) var biometricType: LABiometryType = .none

    // MARK: - Private
    private let keychainService = "com.travelexpenses.pin"
    private let keychainAccount = "pin_hash"
    private let saltKeychainAccount = "pin_salt"

    // MARK: - Init
    init() {
        // Restore persisted settings
        biometricEnabled = UserDefaults.standard.bool(forKey: Keys.biometricEnabled)
        if let raw = UserDefaults.standard.string(forKey: Keys.autoLockTimeout),
           let t = AutoLockTimeout(rawValue: raw) {
            autoLockTimeout = t
        }
        isPinSet = loadPinHash() != nil
        checkBiometricAvailability()
    }

    // MARK: - PIN management

    func setPin(_ pin: String) throws {
        guard pin.count == 6, pin.allSatisfy(\.isNumber) else {
            throw AuthError.invalidPin
        }
        let salt = generateSalt()
        let hash = try hashPin(pin, salt: salt)
        try saveToKeychain(value: hash, account: keychainAccount)
        try saveToKeychain(value: salt.base64EncodedString(), account: saltKeychainAccount)
        isPinSet = true
    }

    func verifyPin(_ pin: String) -> Bool {
        guard let storedHash = loadPinHash(),
              let saltB64 = loadFromKeychain(account: saltKeychainAccount),
              let saltData = Data(base64Encoded: saltB64),
              let computedHash = try? hashPin(pin, salt: saltData) else {
            return false
        }
        return computedHash == storedHash
    }

    func clearPin() {
        deleteFromKeychain(account: keychainAccount)
        deleteFromKeychain(account: saltKeychainAccount)
        isPinSet = false
    }

    // MARK: - Biometric authentication

    func canUseBiometrics() -> Bool {
        biometricType != .none && biometricEnabled
    }

    func authenticateWithBiometrics(
        reason: String = "Unlock Travel Expenses",
        completion: @escaping (Bool, Error?) -> Void
    ) {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            completion(false, error)
            return
        }
        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: reason
        ) { success, evalError in
            DispatchQueue.main.async {
                completion(success, evalError)
            }
        }
    }

    // MARK: - Private helpers

    private func checkBiometricAvailability() {
        let ctx = LAContext()
        var err: NSError?
        if ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &err) {
            biometricType = ctx.biometryType
        } else {
            biometricType = .none
        }
    }

    private func generateSalt() -> Data {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes)
    }

    private func hashPin(_ pin: String, salt: Data) throws -> String {
        guard let pinData = pin.data(using: .utf8) else { throw AuthError.hashingFailed }
        // PBKDF2-SHA256, 10 000 iterations, 32-byte key
        var derivedKey = [UInt8](repeating: 0, count: 32)
        let result = pinData.withUnsafeBytes { pinBytes in
            salt.withUnsafeBytes { saltBytes in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    pinBytes.baseAddress?.assumingMemoryBound(to: Int8.self), pin.utf8.count,
                    saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self), salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    10_000,
                    &derivedKey, derivedKey.count
                )
            }
        }
        guard result == kCCSuccess else { throw AuthError.hashingFailed }
        return Data(derivedKey).map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Keychain helpers

    private func saveToKeychain(value: String, account: String) throws {
        guard let data = value.data(using: .utf8) else { throw AuthError.keychainFailed }
        deleteFromKeychain(account: account)
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: account,
            kSecValueData:   data,
        ]
        guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else {
            throw AuthError.keychainFailed
        }
    }

    private func loadFromKeychain(account: String) -> String? {
        let query: [CFString: Any] = [
            kSecClass:            kSecClassGenericPassword,
            kSecAttrService:      keychainService,
            kSecAttrAccount:      account,
            kSecReturnData:       true,
            kSecMatchLimit:       kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data,
              let str = String(data: data, encoding: .utf8) else {
            return nil
        }
        return str
    }

    private func loadPinHash() -> String? {
        loadFromKeychain(account: keychainAccount)
    }

    @discardableResult
    private func deleteFromKeychain(account: String) -> Bool {
        let query: [CFString: Any] = [
            kSecClass:       kSecClassGenericPassword,
            kSecAttrService: keychainService,
            kSecAttrAccount: account,
        ]
        return SecItemDelete(query as CFDictionary) == errSecSuccess
    }

    // MARK: - Constant keys
    private enum Keys {
        static let biometricEnabled = "auth_biometric_enabled"
        static let autoLockTimeout  = "auth_auto_lock_timeout"
    }
}

// MARK: - Errors
enum AuthError: LocalizedError {
    case invalidPin, hashingFailed, keychainFailed

    var errorDescription: String? {
        switch self {
        case .invalidPin:     return "PIN must be exactly 6 digits."
        case .hashingFailed:  return "Failed to hash PIN."
        case .keychainFailed: return "Failed to access Keychain."
        }
    }
}

// Need to import CommonCrypto for PBKDF2
import CommonCrypto
