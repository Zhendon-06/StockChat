import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        IosChatDatabaseKt.initializeStockChatDatabase()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
