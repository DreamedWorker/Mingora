//
//  WizardView.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/30.
//

import SwiftUI
import LauncherCore

struct WizardView: View {
    @State private var part: WizardViewPart = .greating
    @Environment(AppState.self) private var appState
    
    @ViewBuilder
    var body: some View {
        switch part {
        case .greating:
            OnbordingView {
                withAnimation(.default, {
                    part = .downloading
                })
            }
        case .downloading:
            EnvironmentSetupView(
                onCompleted: {
                    Task { @MainActor in
                        do {
                            try await LauncherCoreKt.wizardCompleted()
                            appState.isFirstOpen = false
                        } catch {
                            NSApplication.shared.terminate(self)
                        }
                    }
                },
                onSetGameInfoList: { games in appState.fillGamesDuringSetup(games) }
            )
        }
    }
}

private struct EnvironmentSetupView: View {
    @State private var viewModel = EnvironmentSetupViewModel()
    @State private var ffmpegInstalled: Bool?
    
    let onCompleted: () -> Void
    let onSetGameInfoList: ([GameInfo]) -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: viewModel.isCompleted ? "checkmark.circle.fill" : "arrow.down.circle")
                .font(.system(size: 56))
                .foregroundStyle(viewModel.isCompleted ? Color.green : Color.accentColor)
            
            Text(viewModel.isCompleted ? "环境配置完成" : "配置运行环境")
                .font(.largeTitle.bold())
            
            Text("Mingora 需要下载并配置 Wine 和相关文件。")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            
            Toggle("使用镜像站下载环境文件", isOn: $viewModel.useMirror)
                .toggleStyle(.checkbox)
                .disabled(viewModel.isInstalling)
            Picker(
                selection: $viewModel.selectedServer,
                content: {
                    Text("中国大陆").tag(HYPLauncherId.chinaOfficial)
                    Text("海外地区").tag(HYPLauncherId.globalOfficial)
                },
                label: {
                    Text("主要游玩的服务器所在地")
                }
            )
            
            if viewModel.hasStarted {
                VStack(spacing: 10) {
                    if let progress = viewModel.progress {
                        ProgressView(value: progress)
                    } else if viewModel.isInstalling {
                        ProgressView()
                    } else {
                        ProgressView(value: 1)
                    }
                    
                    HStack {
                        Text(viewModel.statusMessage)
                            .lineLimit(1)
                        Spacer()
                        if let progress = viewModel.progress {
                            Text(progress, format: .percent.precision(.fractionLength(0)))
                                .monospacedDigit()
                        }
                    }
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    
                    if !viewModel.currentResource.isEmpty {
                        Text(viewModel.currentResource)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: 520)
            } else {
                Text("点击确认后开始下载。下载过程中请不要退出应用。")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            
            if let errorMessage = viewModel.errorMessage {
                Label(errorMessage, systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.red)
                    .font(.callout)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 520)
            }
            
            FFmpegStatusCard
            
            Button {
                if viewModel.isCompleted {
                    onCompleted()
                } else {
                    Task {
                        await viewModel.startProcess(setGames: { games in
                            onSetGameInfoList(games)
                        })
                    }
                }
            } label: {
                if viewModel.isInstalling {
                    ProgressView()
                        .controlSize(.small)
                    Text("正在配置…")
                } else if viewModel.isCompleted {
                    Text("完成并进入主页")
                } else if viewModel.errorMessage != nil {
                    Text("重试下载")
                } else {
                    Text("确认并开始下载")
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            // 任务未结束时禁止重复点击；失败后允许重试，成功后允许确认进入主页。
            .disabled(viewModel.isInstalling)
        }
        .frame(maxWidth: 520)
        .task {
            refreshFFmpegStatus()
        }
        .onChange(of: viewModel.isCompleted) { _, isCompleted in
            if isCompleted {
                refreshFFmpegStatus()
            }
        }
    }
    
    @ViewBuilder
    private var FFmpegStatusCard: some View {
        HStack(spacing: 14) {
            Image(systemName: ffmpegInstalled == true ? "checkmark.circle.fill" : "video.slash.fill")
                .font(.title2)
                .foregroundStyle(ffmpegInstalled == true ? .green : .orange)
                .frame(width: 30)
            
            VStack(alignment: .leading, spacing: 4) {
                Text("FFmpeg")
                    .font(.headline)
                
                if ffmpegInstalled == true {
                    Text("已检测到 FFmpeg，视频背景功能可能可用。")
                        .foregroundStyle(.secondary)
                } else if ffmpegInstalled == false {
                    Text("未检测到 FFmpeg，视频背景功能可能无法使用。")
                        .foregroundStyle(.secondary)
                    Text("安装命令：brew install ffmpeg")
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(.secondary)
                } else {
                    Text("正在检查安装状态…")
                        .foregroundStyle(.secondary)
                }
            }
            
            Spacer(minLength: 12)
            
            Button("重新检测") {
                refreshFFmpegStatus()
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
            .disabled(ffmpegInstalled == nil)
        }
        .padding(16)
        .frame(maxWidth: 520, alignment: .leading)
        .background(.quaternary.opacity(0.45), in: RoundedRectangle(cornerRadius: 12))
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(.quaternary, lineWidth: 1)
        }
    }
    
    private func refreshFFmpegStatus() {
        ffmpegInstalled = BackgroundService.shared.hasFFmpegInstalled()
    }
}

private struct OnbordingView: View {
    let onStart: () -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "sparkles")
                .font(.system(size: 64))
                .foregroundStyle(.tint)
            
            Text("欢迎使用 Mingora")
                .font(.largeTitle.bold())
            
            Text("首次使用前需要先配置运行环境。")
                .foregroundStyle(.secondary)
            
            Button("开始使用") {
                onStart()
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }
}

private enum WizardViewPart {
    case greating, downloading
}

#Preview {
    WizardView()
}
