//
//  MemoryCache.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/31.
//

import Foundation
import LauncherCore

class MemoryCache {
    static let shared = MemoryCache()
    
    private init() {}
    
    private let cache = NSCache<NSString, KotlinBase>()
    
    func setValue<T: KotlinBase>(for key: String, _ value: T) {
        cache.setObject(value, forKey: NSString(string: key))
    }
    
    func getValue<T: KotlinBase>(for key: String) -> T? {
        return cache.object(forKey: NSString(string: key)) as? T
    }
}
