//
//  RuntimeResourceInstaller.swift
//  Mingora
//
//  Created by 鸳汐 on 2026/8/27.
//

import Foundation

enum RuntimeResourceInstaller {
    private static let fileManager = FileManager.default

    static var resourceDirectory: URL? {
        guard let bundleResourceURL = Bundle.main.resourceURL else {
            return nil
        }

        let namedResourcesDirectory = bundleResourceURL.appendingPathComponent("Resources", isDirectory: true)
        var isDirectory: ObjCBool = false
        if fileManager.fileExists(atPath: namedResourcesDirectory.path, isDirectory: &isDirectory), isDirectory.boolValue {
            return namedResourcesDirectory
        }

        // Xcode may flatten files from the Resources group into the app bundle.
        return bundleResourceURL
    }

    static func copyWineRuntimeFiles(from sourceDirectory: URL, to prefixDirectory: URL) throws {
        var isDirectory: ObjCBool = false
        guard fileManager.fileExists(atPath: sourceDirectory.path, isDirectory: &isDirectory), isDirectory.boolValue else {
            throw InstallerError.resourceDirectoryNotFound(sourceDirectory.path)
        }

        let sourceFiles = try fileManager.contentsOfDirectory(
            at: sourceDirectory,
            includingPropertiesForKeys: nil,
            options: [.skipsHiddenFiles]
        )

        let targetDirectories = [
            "32": prefixDirectory
                .appendingPathComponent("drive_c", isDirectory: true)
                .appendingPathComponent("windows", isDirectory: true)
                .appendingPathComponent("syswow64", isDirectory: true),
            "64": prefixDirectory
                .appendingPathComponent("drive_c", isDirectory: true)
                .appendingPathComponent("windows", isDirectory: true)
                .appendingPathComponent("system32", isDirectory: true),
        ]
        var copiedFileCount = 0

        for sourceFile in sourceFiles {
            let sourceName = sourceFile.lastPathComponent
            guard let bitness = bitness(of: sourceName),
                  let targetDirectory = targetDirectories[bitness] else {
                continue
            }

            var sourceIsDirectory: ObjCBool = false
            guard fileManager.fileExists(atPath: sourceFile.path, isDirectory: &sourceIsDirectory),
                  !sourceIsDirectory.boolValue else {
                continue
            }

            try fileManager.createDirectory(
                at: targetDirectory,
                withIntermediateDirectories: true,
                attributes: nil
            )

            let targetName = destinationName(for: sourceName, bitness: bitness)
            let targetFile = targetDirectory.appendingPathComponent(targetName)
            if fileManager.fileExists(atPath: targetFile.path) {
                try fileManager.removeItem(at: targetFile)
            }
            try fileManager.copyItem(at: sourceFile, to: targetFile)
            copiedFileCount += 1
        }

        guard copiedFileCount > 0 else {
            throw InstallerError.noRuntimeFilesFound(sourceDirectory.path)
        }
    }

    private static func bitness(of fileName: String) -> String? {
        let lowercasedName = fileName.lowercased()
        if lowercasedName.hasSuffix("32.dll") || lowercasedName.hasSuffix("32.exe") {
            return "32"
        }
        if lowercasedName.hasSuffix("64.dll") || lowercasedName.hasSuffix("64.exe") {
            return "64"
        }
        return nil
    }

    private static func destinationName(for fileName: String, bitness: String) -> String {
        let lowercasedName = fileName.lowercased()
        let extensionName = lowercasedName.hasSuffix(".dll") ? ".dll" : ".exe"
        return String(fileName.dropLast(bitness.count + extensionName.count)) + extensionName
    }

    private enum InstallerError: LocalizedError {
        case resourceDirectoryNotFound(String)
        case noRuntimeFilesFound(String)

        var errorDescription: String? {
            switch self {
            case let .resourceDirectoryNotFound(path):
                return "找不到 App 资源目录：\(path)"
            case let .noRuntimeFilesFound(path):
                return "资源目录中没有找到以 32/64.dll 或 32/64.exe 结尾的文件：\(path)"
            }
        }
    }
}
