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
                // A Universal Link — a shared https://cooknco.eu/… address the system
                // handed us instead of Safari, because the app is associated with the
                // domain (see iosApp.entitlements). It arrives as a browsing activity
                // rather than through onOpenURL, which is why it is handled separately
                // even though both end at the same place.
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    guard let url = activity.webpageURL else { return }
                    MainViewControllerKt.handleDeepLink(url: url.absoluteString)
                }
        }
    }
}
