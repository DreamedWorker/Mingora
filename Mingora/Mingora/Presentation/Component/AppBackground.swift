//
//  AppBackground.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/31.
//

import SwiftUI
import LauncherCore
import Kingfisher

struct AppBackground: View {
    let currentSelectedGame: String
    @State private var bgType: BackgroundType = .none
    @State private var imageBgUrl: String? = nil
    @State private var videoBgUrl: String? = nil
    @State private var themeBgUrl: String? = nil
    @Environment(AppState.self) private var appState
    
    var body: some View {
        ZStack(alignment: .topLeading) {
            backgroundView

            // 只要当前有背景且主题图 URL 有效，就显示主题图；不限定为视频背景。
            if bgType != .none,
               let themeBgUrl,
               let themeURL = URL(string: themeBgUrl) {
                KFImage(themeURL)
                    .resizable()
                    .placeholder {
                        Color.clear
                    }
                    .fade(duration: 0.2)
                    .scaledToFit()
                    .padding(.top, 24)
                    // GameSelector occupies the first 286pt on the left.
                    // Keep the theme to its right with a visible gap.
                    .padding(.leading, 310)
            }
        }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .ignoresSafeArea()
            .task(id: currentSelectedGame) {
                await showBackground(gameId: currentSelectedGame)
            }
    }
    
    @ViewBuilder
    private var backgroundView: some View {
        switch bgType {
        case .image:
            if let imageBgUrl = imageBgUrl,
               let url = URL(string: imageBgUrl) {
                KFImage(url)
                    .resizable()
                    .placeholder {
                        Color.black
                    }
                    .fade(duration: 0.2)
                    .scaledToFill()
            } else {
                Color.black
            }
        case .video:
            if let url = videoBgUrl {
                VideoPlayer(videoLocalPath: URL(filePath: url))
            } else {
                Color.black
            }
        case .none:
            Color.clear
        case .loading:
            VStack {
                ProgressView()
            }
        }
    }
    
    @MainActor
    private func showBackground(gameId: String) async {
        guard !Task.isCancelled, gameId == currentSelectedGame else { return }

        // 切换游戏时先清除旧视频，避免新请求期间继续显示上一个游戏的视频。
        if let gameInfo = appState.gameInfos.first(where: { $0.id == gameId }) {
            videoBgUrl = nil
            themeBgUrl = nil
            imageBgUrl = gameInfo.display.background.url
            bgType = .image
        } else {
            videoBgUrl = nil
            themeBgUrl = nil
            bgType = .loading
        }

        let hasDecoderInstalled = BackgroundService.shared.hasFFmpegInstalled()
        let bgInfo: GameBackground?

        if let cached = MemoryCache.shared.getValue(for: "game_bg_\(gameId)") as? GameBackground,
           !cached.backgrounds.isEmpty {
            bgInfo = cached
        } else {
            do {
                bgInfo = try await fetchRemoteBackground(gameId: gameId)
            } catch {
                // 任务被切换或视图销毁时，不要把取消当成“没有背景”处理。
                guard !Task.isCancelled, gameId == currentSelectedGame else { return }
                bgInfo = nil
            }
        }

        guard !Task.isCancelled, gameId == currentSelectedGame else { return }
        await applyBackground(
            bgInfo: bgInfo,
            gameId: gameId,
            hasDecoderInstalled: hasDecoderInstalled
        )
    }
    
    private func fetchRemoteBackground(gameId: String) async throws -> GameBackground {
        let remoteBg = try await BackgroundService.shared.updateGameBackground(
            gameId: GameId.Companion.shared.convertId(id: gameId)
        )
        try Task.checkCancellation()
        MemoryCache.shared.setValue(for: "game_bg_\(gameId)", remoteBg)
        return remoteBg
    }
    
    @MainActor
    private func applyBackground(
        bgInfo: GameBackground?,
        gameId: String,
        hasDecoderInstalled: Bool
    ) async {
        guard !Task.isCancelled,
              gameId == currentSelectedGame,
              let bgInfo,
              let firstBackground = bgInfo.backgrounds.first else {
            guard !Task.isCancelled, gameId == currentSelectedGame else { return }
            await applyFallbackBackground(gameId: gameId)
            return
        }
        
        if hasDecoderInstalled {
            await applyVideoBackgroundIfPossible(
                gameId: gameId,
                videoUrl: firstBackground.video.url,
                themeImageUrl: firstBackground.theme.url,
                fallbackImageUrl: firstBackground.background.url
            )
        } else {
            setImageBackground(
                url: firstBackground.background.url,
                themeImageUrl: firstBackground.theme.url
            )
        }
    }
    
    @MainActor
    private func applyVideoBackgroundIfPossible(
        gameId: String,
        videoUrl: String,
        themeImageUrl: String,
        fallbackImageUrl: String
    ) async {
        // 转码期间先显示图片和主题图；转码成功后再切换到视频。
        setImageBackground(
            url: fallbackImageUrl,
            themeImageUrl: themeImageUrl
        )

        do {
            let bgvPath = try await BackgroundService.shared.cacheVideoBackground(
                gameId: gameId,
                videoUrl: videoUrl
            )

            // Kotlin 层可能因 FFmpeg 的同步执行而晚于取消返回，必须再次确认请求仍然有效。
            guard !Task.isCancelled, gameId == currentSelectedGame else { return }

            if let path = bgvPath {
                videoBgUrl = path
                themeBgUrl = themeImageUrl
                bgType = .video
            } else {
                setImageBackground(
                    url: fallbackImageUrl,
                    themeImageUrl: themeImageUrl
                )
            }
        } catch {
            // 取消、切换游戏或视图销毁时，不能再写回旧请求的 fallback。
            guard !Task.isCancelled, gameId == currentSelectedGame else { return }
            setImageBackground(
                url: fallbackImageUrl,
                themeImageUrl: themeImageUrl
            )
        }
    }
    
    @MainActor
    private func setImageBackground(url: String, themeImageUrl: String? = nil) {
        videoBgUrl = nil
        themeBgUrl = themeImageUrl
        imageBgUrl = url
        bgType = .image
    }
    
    @MainActor
    private func applyFallbackBackground(gameId: String) async {
        guard !Task.isCancelled, gameId == currentSelectedGame else { return }

        if let gameInfo = appState.gameInfos.first(where: { $0.id == gameId }) {
            videoBgUrl = nil
            themeBgUrl = nil
            imageBgUrl = gameInfo.display.background.url
            bgType = .image
        } else {
            imageBgUrl = nil
            videoBgUrl = nil
            themeBgUrl = nil
            bgType = .none
        }
    }

}

private enum BackgroundType: Equatable {
    case image
    case video
    case none
    case loading
}
