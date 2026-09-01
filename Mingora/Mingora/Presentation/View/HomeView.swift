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
                GameNotice(currentGame: appState.lastOpenedGame!)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(alignment: .topTrailing) {
                GameSelector(
                    games: appState.gameInfos,
                    currentGameID: appState.lastOpenedGame!,
                    onSelect: appState.setLastOpenedGame(gameId:),
                    updateGameInfo: appState.updateOrInsertGameInfo(_:)
                )
                .padding(.top, 24)
                .padding(.trailing, 24)
            }
        }
    }
}

#Preview {
    HomeView()
}
