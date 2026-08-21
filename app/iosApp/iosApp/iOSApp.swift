import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // cooknco://login?token=… — the Google OAuth callback.
                    MainViewControllerKt.handleDeepLink(url: url.absoluteString)
                }
        }
    }
}
