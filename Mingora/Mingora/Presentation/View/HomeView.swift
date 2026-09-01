//
//  HomeView.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/31.
//

import SwiftUI

struct HomeView: View {
    @Environment(AppState.self) private var appState
    @State private var uiPart: UIPart = .launcher
    
    var body: some View {
        if !appState.gameInfos.isEmpty && appState.lastOpenedGame != nil {
            ZStack(alignment: .topLeading) {
                AppBackground(currentSelectedGame: appState.lastOpenedGame!)
                HStack(spacing: 0) {
                    // 功能列表
                    VStack {
                        Image(systemName: "photo")
                            .colorScheme(.dark)
                            .font(.system(size: 16))
                            .padding(.top)
                            .help("游戏截屏")
                        Spacer()
                        Image(systemName: "gear")
                            .colorScheme(.dark)
                            .font(.system(size: 16))
                            .padding(.bottom, 20)
                            .help("软件设置")
                    }
                    .padding()
                    .frame(width: 72)
                    .background(Color.black.opacity(0.8))
                    
                    // 对应显示内容
                    switch uiPart {
                    case .launcher:
                        VStack {
                            Spacer()
                            HStack(alignment: .bottom) {
                                GameNotice(currentGame: appState.lastOpenedGame!)
                                Spacer()
                            }
                        }
                        .padding(.bottom, 20)
                        .padding(.horizontal, 20)
                    case .gallery:
                        VStack {}
                    }
                }
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

private enum UIPart {
    case launcher
    case gallery
}

#Preview {
    HomeView()
}
