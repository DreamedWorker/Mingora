//
//  CurrentGame.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/28.
//

import SwiftUI
import Kingfisher
import LauncherCore

private struct CurrentGame: View {
    let selectedGameInfo: GameInfo
    let isExpanded: Bool
    let onToggle: () -> Void

    init(
        selectedGameInfo: GameInfo,
        isExpanded: Bool = false,
        onToggle: @escaping () -> Void = {}
    ) {
        self.selectedGameInfo = selectedGameInfo
        self.isExpanded = isExpanded
        self.onToggle = onToggle
    }

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 12) {
                gameIcon(size: 48)
                VStack(alignment: .leading, spacing: 3) {
                    Text("当前游戏")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.65))
                    Text(selectedGameInfo.display.name)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(selectedGameInfo.biz)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.62))
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white.opacity(0.72))
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
                    .animation(.easeOut(duration: 0.18), value: isExpanded)
            }
            .padding(12)
            .frame(width: 286, alignment: .leading)
            .background(Color.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.white.opacity(isExpanded ? 0.3 : 0.14), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.22), radius: 16, y: 8)
        }
        .buttonStyle(.plain)
        .help("切换游戏")
    }

    @ViewBuilder
    private func gameIcon(size: CGFloat) -> some View {
        KFImage(URL(string: selectedGameInfo.display.icon.url))
            .resizable()
            .placeholder {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color.white.opacity(0.1))
                    .overlay {
                        Image(systemName: "gamecontroller.fill")
                            .foregroundStyle(.white.opacity(0.45))
                    }
            }
            .fade(duration: 0.18)
            .scaledToFill()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}


struct GameSelector: View {
    let games: [GameInfo]
    let currentGameID: String
    let onSelect: (String) -> Void
    let updateGameInfo: (GameInfo) -> Void

    @State private var isExpanded = false

    private let panelWidth: CGFloat = 286

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let selectedGame = games.first(where: { $0.id == currentGameID }) {
                CurrentGame(
                    selectedGameInfo: selectedGame,
                    isExpanded: isExpanded,
                    onToggle: togglePanel
                )

                if isExpanded {
                    expandedPanel
                        .transition(.asymmetric(
                            insertion: .scale(scale: 0.96, anchor: .top).combined(with: .opacity),
                            removal: .opacity
                        ))
                }
            }
        }
        .frame(width: panelWidth, alignment: .leading)
        .animation(.easeOut(duration: 0.18), value: isExpanded)
        .onAppear {
            // 此控件出现后，延迟1秒，然后更新游戏信息
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(1))
                if MemoryCache.shared.getValue(for: "game_info_\(currentGameID)") == nil {
                    // 启动应用后，内存中的游戏信息一定为空，此时可以加载新数据。
                    do {
                        let neoData = try await HomeService.shared.getGameInfo(
                            gameId: GameId.Companion.shared.convertId(id: currentGameID)
                        )
                        updateGameInfo(neoData)
                        MemoryCache.shared.setValue(for: "game_info_\(currentGameID)", neoData)
                    } catch {}
                }
            }
        }
    }

    private var expandedPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("选择游戏")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Spacer()
                Text("点击切换")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.5))
            }

            ScrollView {
                Button(action: {}) {
                    HStack(spacing: 10) {
                        Image(systemName: "plus.app")
                            .resizable()
                            .frame(width: 36, height: 36)
                        Text("添加游戏")
                        Spacer(minLength: 6)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 8)
                
                LazyVStack(spacing: 4) {
                    ForEach(games, id: \.id) { game in
                        gameRow(game)
                    }
                }
            }
            .frame(maxHeight: 242)
        }
        .padding(12)
        .frame(width: panelWidth, alignment: .leading)
        .background(Color.black.opacity(0.8), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.13), lineWidth: 1)
        }
        .shadow(color: .black.opacity(0.24), radius: 18, y: 10)
    }

    private func gameRow(_ game: GameInfo) -> some View {
        Button {
            select(game)
        } label: {
            HStack(spacing: 10) {
                gameIcon(for: game, size: 36)

                VStack(alignment: .leading, spacing: 2) {
                    Text(game.display.name)
                        .font(.callout.weight(.medium))
                        .foregroundStyle(.white)
                        .lineLimit(1)

                    Text(game.biz)
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.52))
                        .lineLimit(1)
                }

                Spacer(minLength: 6)

                if game.id == currentGameID {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(game.id == currentGameID ? Color.white.opacity(0.12) : Color.clear)
        }
    }

    @ViewBuilder
    private func gameIcon(for game: GameInfo, size: CGFloat) -> some View {
        KFImage(URL(string: game.display.icon.url))
            .resizable()
            .placeholder {
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(Color.white.opacity(0.1))
                    .overlay {
                        Image(systemName: "gamecontroller.fill")
                            .font(.system(size: size * 0.36))
                            .foregroundStyle(.white.opacity(0.45))
                    }
            }
            .fade(duration: 0.18)
            .scaledToFill()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
    }

    private func togglePanel() {
        withAnimation(.easeOut(duration: 0.18)) {
            isExpanded.toggle()
        }
    }

    private func select(_ game: GameInfo) {
        Task { @MainActor in
            if MemoryCache.shared.getValue(for: "game_info_\(game.id)") == nil {
                // 当切换游戏时，首次切换内存中也为空，但下次切回时不为空，确保网络请求次数最少。
                do {
                    let neoData = try await HomeService.shared.getGameInfo(
                        gameId: GameId.Companion.shared.convertId(id: game.id)
                    )
                    updateGameInfo(neoData)
                    MemoryCache.shared.setValue(for: "game_info_\(game.id)", neoData)
                } catch {}
            }
            onSelect(game.id)
        }
        withAnimation(.easeOut(duration: 0.18)) {
            isExpanded = false
        }
    }
}
