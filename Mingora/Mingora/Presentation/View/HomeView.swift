//
//  HomeView.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/31.
//

import SwiftUI

struct HomeView: View {
    @Environment(AppState.self) private var appState
    
    var body: some View {
        if !appState.gameInfos.isEmpty && appState.lastOpenedGame != nil {
            ZStack(alignment: .topLeading) {
                AppBackground(currentSelectedGame: appState.lastOpenedGame!)
                
                GameSelector(
                    games: appState.gameInfos,
                    currentGameID: appState.lastOpenedGame!,
                    onSelect: appState.setLastOpenedGame(gameId:),
                    updateGameInfo: appState.updateOrInsertGameInfo(_:)
                )
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

#Preview {
    HomeView()
}
