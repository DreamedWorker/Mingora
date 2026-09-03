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
    @State private var isShowingLanguageSelection = false
    @State private var selectedAudioLanguage: AudioLanguageOption = .chinese
    @State private var isShowingTerminateConfirmation = false
    @State private var errorMessage: String?
    @State private var isActionRunning = false

    private let panelWidth: CGFloat = 286

    private var status: InstallStatus { appState.installStatus(for: selectedGame) }
    private var gameId: GameId { GameId.Companion.shared.convertId(id: selectedGame) }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: statusIcon)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(statusIconColor)
                    .frame(width: 22, height: 22)
                    .background(statusIconColor.opacity(0.16), in: Circle())

                VStack(alignment: .leading, spacing: 2) {
                    Text("游戏状态")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.62))
                    Text(statusTitle)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                }

                Spacer(minLength: 8)
            }

            if let downloadedText = status.downloadedText {
                VStack(alignment: .leading, spacing: 7) {
                    ProgressView(value: status.progress)
                        .progressViewStyle(.linear)
                        .tint(.white)

                    HStack {
                        Text(downloadedText)
                        Spacer(minLength: 8)
                        Text("\(Int(status.progress * 100))%")
                    }
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                }
            }

            actionView
        }
        .padding(14)
        .frame(width: panelWidth, alignment: .leading)
        .background(Color.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        }
        .shadow(color: .black.opacity(0.22), radius: 16, y: 8)
        .sheet(isPresented: $isShowingLanguageSelection) {
            AudioLanguageSelectionView(selection: selectedAudioLanguage) { language in
                selectedAudioLanguage = language
                isShowingLanguageSelection = false
                Task { @MainActor in
                    // 等待选择语言弹窗完成关闭后，再打开目录选择器，避免两个弹窗同时呈现。
                    try? await Task.sleep(for: .milliseconds(200))
                    guard !Task.isCancelled else { return }
                    isChoosingDirectory = true
                }
            }
            .fittedModalPresentation()
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
                        audioLanguage: selectedAudioLanguage.gameAudioLanguage,
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

    @ViewBuilder
    private var actionView: some View {
        switch status {
        case .notInstalled, .failed, .terminated:
            Button {
                isShowingLanguageSelection = true
            } label: {
                Label(status == .notInstalled ? "下载游戏" : "重新下载", systemImage: "arrow.down.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.regular)

        case .preparing:
            HStack(spacing: 8) {
                ProgressView()
                    .controlSize(.small)
                    .tint(.white)
                Text("准备安装…")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.82))
                Spacer()
            }

        case .downloading:
            installActionButtons(
                primaryTitle: "暂停",
                primaryIcon: "pause.fill",
                primaryAction: { try await GameService.shared.pauseGameInstall(gameId: gameId) }
            )

        case .paused:
            installActionButtons(
                primaryTitle: "继续",
                primaryIcon: "play.fill",
                primaryAction: { try await GameService.shared.resumeGameInstall(gameId: gameId) }
            )

        case .completed:
            Button {
                // 游戏启动逻辑待接入。
            } label: {
                Label("启动游戏", systemImage: "play.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.regular)
        }
    }

    private func installActionButtons(
        primaryTitle: String,
        primaryIcon: String,
        primaryAction: @escaping () async throws -> Void
    ) -> some View {
        HStack(spacing: 8) {
            Button {
                run(primaryAction)
            } label: {
                Label(primaryTitle, systemImage: primaryIcon)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)

            Button {
                isShowingTerminateConfirmation = true
            } label: {
                Label("终止", systemImage: "xmark")
            }
            .buttonStyle(.bordered)
        }
        .controlSize(.regular)
    }

    private var statusTitle: String {
        switch status {
        case .notInstalled:
            return "尚未安装"
        case .preparing:
            return "正在准备"
        case .downloading:
            return "正在下载"
        case .paused:
            return "下载已暂停"
        case .completed:
            return "可以开始游戏"
        case .failed:
            return "安装失败"
        case .terminated:
            return "安装已终止"
        }
    }

    private var statusIcon: String {
        switch status {
        case .notInstalled:
            return "arrow.down.circle"
        case .preparing:
            return "gearshape.2"
        case .downloading:
            return "arrow.down.circle.fill"
        case .paused:
            return "pause.circle.fill"
        case .completed:
            return "checkmark.circle.fill"
        case .failed:
            return "exclamationmark.circle.fill"
        case .terminated:
            return "xmark.circle.fill"
        }
    }

    private var statusIconColor: Color {
        switch status {
        case .notInstalled, .terminated:
            return .white.opacity(0.72)
        case .preparing, .downloading, .paused:
            return .orange
        case .completed:
            return .green
        case .failed:
            return .red
        }
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


private extension View {
    @ViewBuilder
    func fittedModalPresentation() -> some View {
        if #available(macOS 26.0, *) {
            presentationSizing(.fitted)
        } else {
            self
        }
    }
}

private enum AudioLanguageOption: String, CaseIterable, Identifiable {
    case chinese = "中文"
    case english = "English (US)"
    case japanese = "日本語"
    case korean = "한국어"

    var id: Self { self }

    var subtitle: String {
        switch self {
        case .chinese: return "普通话语音"
        case .english: return "美国英语语音"
        case .japanese: return "日语语音"
        case .korean: return "韩语语音"
        }
    }

    var gameAudioLanguage: GameAudioLanguage {
        switch self {
        case .chinese: return .chinese
        case .english: return .english
        case .japanese: return .japanese
        case .korean: return .korean
        }
    }
}

private struct AudioLanguageSelectionView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var selection: AudioLanguageOption
    let onConfirm: (AudioLanguageOption) -> Void

    init(
        selection: AudioLanguageOption,
        onConfirm: @escaping (AudioLanguageOption) -> Void
    ) {
        _selection = State(initialValue: selection)
        self.onConfirm = onConfirm
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 10) {
                Image(systemName: "waveform")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.tint)
                    .frame(width: 30, height: 30)
                    .background(Color.accentColor.opacity(0.14), in: Circle())

                VStack(alignment: .leading, spacing: 3) {
                    Text("选择游戏语音")
                        .font(.title3.weight(.semibold))
                    Text("安装后将使用所选语音语言。")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
            }

            VStack(spacing: 8) {
                ForEach(AudioLanguageOption.allCases) { option in
                    Button {
                        selection = option
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: selection == option ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 17))
                                .foregroundStyle(selection == option ? Color.accentColor : .secondary)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(option.rawValue)
                                    .font(.body.weight(.medium))
                                Text(option.subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .contentShape(Rectangle())
                        .background(
                            selection == option
                                ? Color.accentColor.opacity(0.10)
                                : Color.primary.opacity(0.04),
                            in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                        )
                        .overlay {
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(
                                    selection == option
                                        ? Color.accentColor.opacity(0.55)
                                        : Color.primary.opacity(0.10),
                                    lineWidth: 1
                                )
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack {
                Spacer()
                Button("取消") {
                    dismiss()
                }
                .buttonStyle(.bordered)

                Button("选择安装目录") {
                    onConfirm(selection)
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(24)
        .frame(width: 360)
    }
}
