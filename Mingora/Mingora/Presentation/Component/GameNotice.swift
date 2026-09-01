//
//  GameNotice.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/9/1.
//

import SwiftUI
import Kingfisher
import LauncherCore

struct GameNotice: View {
    @Environment(AppState.self) private var appState
    @State private var news: GameContent? = nil
    @State private var currentIndex = 0
    @State private var timer: Timer? = nil
    @State private var selectedScope: NewsType = .Activity
    @State private var selectedPosts: [GameContent.Post] = []
    
    let currentGame: String
    
    var body: some View {
        VStack {
            if let news = news {
                let banner = news.banners[currentIndex]
                KFImage.url(URL(string: banner.image.url))
                    .loadDiskFileSynchronously(true)
                    .resizable()
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .frame(width: 420, height: 195)
                    .onTapGesture {
                        openBrowser(for: banner.image.link)
                    }
                    .onAppear {
                        startTimer(news: news)
                    }
                    .onChange(of: news, {_, new in
                        currentIndex = 0
                        timer?.invalidate()
                        startTimer(news: new)
                    })
                VStack {
                    HStack(spacing: 8) {
                        NewsTypeIndicator(title: "活动", showSelected: selectedScope == NewsType.Activity)
                            .onTapGesture {
                                selectedScope = .Activity
                            }
                        NewsTypeIndicator(title: "公告", showSelected: selectedScope == NewsType.Notice)
                            .onTapGesture {
                                selectedScope = .Notice
                            }
                        NewsTypeIndicator(title: "资讯", showSelected: selectedScope == NewsType.Info)
                            .onTapGesture {
                                selectedScope = .Info
                            }
                        Spacer()
                        Image(systemName: "speaker.wave.2")
                    }
                    .frame(height: 30)
                    ScrollView(.vertical, showsIndicators: false) {
                        LazyVStack {
                            ForEach(selectedPosts, id: \.id) { post in
                                HStack {
                                    Text(post.title).foregroundStyle(.white)
                                    Spacer()
                                    Text(post.date).foregroundStyle(.white.opacity(0.85))
                                }
                                .padding(.vertical, 4)
                                .onTapGesture {
                                    openBrowser(for: post.link)
                                }
                            }
                        }
                        .padding(.horizontal, 8)
                        .onAppear {
                            selectedPosts = news.posts.filter({ $0.type == selectedScope.rawValue })
                        }
                        .onChange(of: news, {_, new in
                            selectedPosts = new.posts.filter({ $0.type == selectedScope.rawValue })
                        })
                        .onChange(of: selectedScope, {_, new in
                            selectedPosts = news.posts.filter({ $0.type == new.rawValue })
                        })
                    }
                }
            } else {
                ProgressView()
            }
        }
        .background(.black.opacity(0.75))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .frame(width: 420, height: 310)
        .onAppear {
            Task {
                await fetchNews(for: currentGame)
            }
        }
        .onChange(of: currentGame, { old, new in
            Task {
                currentIndex = 0
                await fetchNews(for: new)
                timer?.invalidate()
            }
        })
    }
    
    private func startTimer(news: GameContent) {
        timer = Timer.scheduledTimer(withTimeInterval: 3.0, repeats: true) { timer in
            withAnimation(.easeInOut) {
                DispatchQueue.main.async {
                    let page = self.currentIndex + 1
                    if page > news.banners.count - 1 {
                        self.currentIndex = 0
                    } else {
                        self.currentIndex = page
                    }
                }
            }
        }
    }
    
    @MainActor
    private func fetchNews(for gameId: String) async {
        if let cached = appState.gameNews.first(where: { $0.game.id == gameId }) {
            news = cached
        } else {
            do {
                let remote = try await HomeService.shared.getGameNotice(
                    gameId: GameId.Companion.shared.convertId(id: gameId)
                )
                appState.cacheGameNews(remote)
                news = remote
            } catch {
                print(error)
                news = nil
            }
        }
    }
    
    private func openBrowser(for website: String) {
        let url = URL(string: website)!
        NSWorkspace.shared.open(url)
    }
}

private struct NewsTypeIndicator: View {
    let title: String
    let showSelected: Bool
    
    var body: some View {
        VStack {
            Text(title).font(.title3).bold().colorScheme(.dark)
            GeometryReader { geo in
                Rectangle()
                    .fill(.primary.opacity((showSelected) ? 1 : 0))
                    .clipShape(RoundedRectangle(cornerRadius: 4))
                    .frame(width: geo.size.width, height: 2)
            }
        }
        .padding(4)
    }
}

private enum NewsType: String {
    case Activity = "POST_TYPE_ACTIVITY"
    case Notice = "POST_TYPE_ANNOUNCE"
    case Info = "POST_TYPE_INFO"
}
