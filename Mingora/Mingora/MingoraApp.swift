//
//  MingoraApp.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/30.
//

import SwiftUI
import LauncherCore

@main
struct MingoraApp: App {
    @NSApplicationDelegateAdaptor(MingoraAppDelegate.self) private var delegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(delegate.appState)
                //.environmentObject(sharedViewModel)
                .frame(
                    minWidth: MingoraAppDelegate.windowSize.width,
                    idealWidth: MingoraAppDelegate.windowSize.width,
                    maxWidth: MingoraAppDelegate.windowSize.width,
                    minHeight: MingoraAppDelegate.windowSize.height,
                    idealHeight: MingoraAppDelegate.windowSize.height,
                    maxHeight: MingoraAppDelegate.windowSize.height
                )
        }
        .defaultSize(
            width: MingoraAppDelegate.windowSize.width,
            height: MingoraAppDelegate.windowSize.height
        )
    }
}
