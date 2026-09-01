//
//  ContentView.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/30.
//

import SwiftUI
import LauncherCore

struct ContentView: View {
    @Environment(AppState.self) private var appState
    
    var body: some View {
        if appState.isFirstOpen {
            WizardView()
        } else {
            HomeView()
        }
    }
}

#Preview {
    ContentView()
        .environment(AppState.forPreviewWithWizard)
}
