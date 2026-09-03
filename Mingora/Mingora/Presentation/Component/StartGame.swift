//
//  StartGame.swift
//  Mingora
//

import SwiftUI
import UniformTypeIdentifiers
import LauncherCore

struct StartGame: View {
    @Environment(AppState.self) private var appState
    let selectedGame: String
    @State private var isChoosingDirectory = false
    @State private var isShowingTerminateConfirmation = false
    @State private var errorMessage: String?
    @State private var isActionRunning = false

    private var status: InstallStatus { appState.installStatus(for: selectedGame) }
    private var gameId: GameId { GameId.Companion.shared.convertId(id: selectedGame) }

    var body: some View {
        VStack(alignment: .trailing, spacing: 10) {
            if let downloadedText = status.downloadedText {
                VStack(alignment: .trailing, spacing: 5) {
                    ProgressView(value: status.progress)
                        .progressViewStyle(.linear)
                        .frame(width: 280)
                    Text(downloadedText)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.75))
                }
            }

            HStack(spacing: 8) {
                switch status {
                case .notInstalled, .failed, .terminated:
                    Button(status == .notInstalled ? "下载游戏" : "重新下载") {
                        isChoosingDirectory = true
                    }
                    .buttonStyle(.borderedProminent)
                case .preparing:
                    ProgressView()
                        .controlSize(.small)
                    Text("准备安装…")
                        .foregroundStyle(.white)
                case .downloading:
                    Button("暂停") { run { try await GameService.shared.pauseGameInstall(gameId: gameId) } }
                        .buttonStyle(.borderedProminent)
                    Button("终止") { isShowingTerminateConfirmation = true }
                        .buttonStyle(.bordered)
                case .paused:
                    Button("继续") { run { try await GameService.shared.resumeGameInstall(gameId: gameId) } }
                        .buttonStyle(.borderedProminent)
                    Button("终止") { isShowingTerminateConfirmation = true }
                        .buttonStyle(.bordered)
                case .completed:
                    Button("启动游戏") { }
                        .buttonStyle(.borderedProminent)
                }
            }
        }
        .alert("终止安装？", isPresented: $isShowingTerminateConfirmation) {
            Button("取消", role: .cancel) {}
            Button("终止", role: .destructive) {
                run { try await GameService.shared.terminateGameInstall(gameId: gameId) }
            }
        } message: {
            Text("已下载的临时文件将保留，但当前安装任务会被移除。")
        }
        .alert("安装失败", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("好", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "未知错误")
        }
        .fileImporter(
            isPresented: $isChoosingDirectory,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                run {
                    try await GameService.shared.installGame(
                        gameId: gameId,
                        destination: url.path,
                        audioLanguage: .english,
                        installType: .install
                    )
                }
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
        .onAppear { appState.startInstallStatusPolling() }
        .onDisappear { appState.stopInstallStatusPolling() }
    }

    private func run(_ operation: @escaping () async throws -> Void) {
        guard !isActionRunning else { return }
        isActionRunning = true
        Task { @MainActor in
            defer { isActionRunning = false }
            do {
                try await operation()
                await appState.refreshInstallStatuses()
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
