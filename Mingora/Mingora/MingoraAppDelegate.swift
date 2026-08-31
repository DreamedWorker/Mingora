//
//  MingoraAppDelegate.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/30.
//

import AppKit
import LauncherCore

class MingoraAppDelegate: NSObject, NSApplicationDelegate {
    static let windowSize = NSSize(width: 1200, height: 676)
    let appState = AppState()
    
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }
    
    func applicationWillFinishLaunching(_ notification: Notification) {
        NSApp.appearance = NSAppearance(named: .darkAqua)
        LauncherCoreKt.startupLib()
        appState.setupBasicData()
    }
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.windows.forEach(configure(_:))
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(windowDidBecomeAvailable(_:)),
            name: NSWindow.didBecomeKeyNotification,
            object: nil
        )
    }
    
    @objc
    private func windowDidBecomeAvailable(_ notification: Notification) {
        guard let window = notification.object as? NSWindow else {
            return
        }
        configure(window)
    }
    
    private func configure(_ window: NSWindow) {
        window.setContentSize(Self.windowSize)
        window.contentMinSize = Self.windowSize
        window.contentMaxSize = Self.windowSize
        
        window.styleMask.insert(.fullSizeContentView)
        window.titlebarAppearsTransparent = true
        window.titleVisibility = .visible
        window.titlebarSeparatorStyle = .none
        
        // A fixed-size window cannot be resized or zoomed/maximized.
        window.styleMask.remove(.resizable)
        window.styleMask.remove(.fullScreen)
        window.standardWindowButton(.zoomButton)?.isEnabled = false
        window.standardWindowButton(.zoomButton)?.isHidden = true
        
        // AppKit recalculates the title-bar chrome after activation. Reapply
        // the separator setting after that pass to prevent a focus-time line.
        DispatchQueue.main.async { [weak window] in
            guard let window else { return }
            window.titlebarAppearsTransparent = true
            window.titlebarSeparatorStyle = .none
        }
    }
}
