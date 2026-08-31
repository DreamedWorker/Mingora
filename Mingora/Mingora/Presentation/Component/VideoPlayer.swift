//
//  VideoPlayer.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/9/1.
//

import SwiftUI
import AVFoundation

struct VideoPlayer: NSViewRepresentable {
    let videoLocalPath: URL
    
    func makeNSView(context: Context) -> VideoPlayerView {
        let view = VideoPlayerView()
        view.setVideo(url: videoLocalPath)
        return view
    }
    
    func updateNSView(_ nsView: VideoPlayerView, context: Context) {
        nsView.setVideo(url: videoLocalPath)
    }
    
    static func dismantleNSView(_ nsView: VideoPlayerView, coordinator: ()) {
        nsView.stopVideo()
    }
}

final class VideoPlayerView: NSView {
    private let playerLayer = AVPlayerLayer()
    private var player: AVPlayer?
    private var endObserver: NSObjectProtocol?
    private var currentURL: URL?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        setupPlayerLayer()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupPlayerLayer()
    }

    override func layout() {
        super.layout()
        playerLayer.frame = bounds
    }

    func setVideo(url: URL) {
        guard currentURL != url else {
            if player?.rate == 0 {
                player?.play()
            }
            return
        }

        stopVideo()
        currentURL = url

        let player = AVPlayer(url: url)
        player.isMuted = true
        self.player = player
        playerLayer.player = player
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: player.currentItem,
            queue: .main
        ) { [weak self] _ in
            self?.player?.seek(to: .zero)
            self?.player?.play()
        }
        player.play()
    }

    func stopVideo() {
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
            self.endObserver = nil
        }
        player?.pause()
        playerLayer.player = nil
        player = nil
        currentURL = nil
    }

    private func setupPlayerLayer() {
        wantsLayer = true
        layer = CALayer()
        playerLayer.videoGravity = .resizeAspectFill
        layer?.addSublayer(playerLayer)
    }

    deinit {
        stopVideo()
    }
}
