/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Puente JNI NDK de AnyWall: Universal Converter v0.4 (SynthesizerEnd)
 * Enlaza con libarchive (BSD-2-Clause) exclusivamente para lectura y extracción
 * de contenedores .rar y .rar5 (incluyendo multivolumen y protección anti-Zip-Slip).
 * Prohibida la compresión hacia .rar.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <unordered_set>
#include <sstream>
#include <cstdio>
#include <cstring>
#include <sys/stat.h>
#include <errno.h>

#include "libarchive/archive.h"
#include "libarchive/archive_entry.h"

namespace {

/**
 * Sanitiza cualquier ruta interna de un archivo .rar / .rar5 para impedir ataques Zip-Slip
 * (eliminando "../", "./", rutas absolutas o letras de unidad Windows).
 */
std::string sanitizeRelativeEntryPath(const char* rawPath) {
    if (rawPath == nullptr || rawPath[0] == '\0') {
        return "";
    }
    std::string input(rawPath);
    for (char& c : input) {
        if (c == '\\') c = '/';
    }

    std::vector<std::string> safeParts;
    std::stringstream ss(input);
    std::string segment;
    while (std::getline(ss, segment, '/')) {
        if (segment.empty() || segment == "." || segment == "..") {
            continue;
        }
        // Evitar prefijos de unidad tipo "C:"
        const auto colonPos = segment.find(':');
        if (colonPos != std::string::npos) {
            segment = segment.substr(colonPos + 1);
        }
        for (char& ch : segment) {
            if (static_cast<unsigned char>(ch) < 32 ||
                ch == '*' || ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|') {
                ch = '_';
            }
        }
        if (!segment.empty() && segment != "." && segment != "..") {
            safeParts.push_back(segment);
        }
    }

    std::string result;
    for (size_t i = 0; i < safeParts.size(); ++i) {
        if (i > 0) result += "/";
        result += safeParts[i];
    }
    return result;
}

bool mkdirsForFile(const std::string& fullFilePath) {
    const auto lastSlash = fullFilePath.find_last_of('/');
    if (lastSlash == std::string::npos) return true;
    const std::string dirPath = fullFilePath.substr(0, lastSlash);
    if (dirPath.empty()) return true;

    std::string current;
    for (size_t i = 0; i < dirPath.size(); ++i) {
        char c = dirPath[i];
        current.push_back(c);
        if (c == '/' && current.size() > 1) {
            ::mkdir(current.c_str(), 0755);
        }
    }
    if (::mkdir(dirPath.c_str(), 0755) != 0 && errno != EEXIST) {
        return false;
    }
    return true;
}

void configureRarReader(struct archive* a, const std::string& passphrase) {
    archive_read_support_filter_none(a);
    archive_read_support_filter_gzip(a);
    archive_read_support_format_rar(a);
    archive_read_support_format_rar5(a);
    archive_read_support_format_raw(a);
    if (!passphrase.empty()) {
        archive_read_add_passphrase(a, passphrase.c_str());
    }
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_anywall_converter_data_engines_LibArchiveRarBridge_nativeGetLibArchiveVersion(
        JNIEnv* env,
        jobject /* thiz */) {
    const char* ver = archive_version_string();
    return env->NewStringUTF(ver ? ver : "libarchive 3.7.7");
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_anywall_converter_data_engines_LibArchiveRarBridge_nativeInspectRarArchive(
        JNIEnv* env,
        jobject /* thiz */,
        jobjectArray volumePaths,
        jstring passphrase,
        jint maxEntries) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (volumePaths == nullptr) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    const jsize volCount = env->GetArrayLength(volumePaths);
    if (volCount <= 0) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    std::vector<std::string> ownedPaths;
    ownedPaths.reserve(static_cast<size_t>(volCount));
    for (jsize i = 0; i < volCount; ++i) {
        auto jPath = static_cast<jstring>(env->GetObjectArrayElement(volumePaths, i));
        if (jPath != nullptr) {
            const char* raw = env->GetStringUTFChars(jPath, nullptr);
            if (raw != nullptr) {
                ownedPaths.emplace_back(raw);
                env->ReleaseStringUTFChars(jPath, raw);
            }
            env->DeleteLocalRef(jPath);
        }
    }

    std::vector<const char*> cFilenames;
    cFilenames.reserve(ownedPaths.size() + 1);
    for (const auto& p : ownedPaths) {
        cFilenames.push_back(p.c_str());
    }
    cFilenames.push_back(nullptr);

    std::string passStr;
    if (passphrase != nullptr) {
        const char* rawPass = env->GetStringUTFChars(passphrase, nullptr);
        if (rawPass != nullptr) {
            passStr = rawPass;
            env->ReleaseStringUTFChars(passphrase, rawPass);
        }
    }

    struct archive* a = archive_read_new();
    configureRarReader(a, passStr);

    std::vector<std::string> rows;
    int openRc = archive_read_open_filenames(a, cFilenames.data(), 64 * 1024);
    if (openRc != ARCHIVE_OK) {
        const char* err = archive_error_string(a);
        rows.push_back(std::string("ERR\t") + (err ? err : "No se pudo abrir el archivo RAR"));
    } else {
        struct archive_entry* entry = nullptr;
        const int limit = (maxEntries > 0) ? maxEntries : 500;
        while (static_cast<int>(rows.size()) < limit) {
            int r = archive_read_next_header(a, &entry);
            if (r == ARCHIVE_EOF) {
                break;
            }
            if (r < ARCHIVE_WARN) {
                const char* err = archive_error_string(a);
                rows.push_back(std::string("ERR\t") + (err ? err : "Cabecera RAR cifrada o dañada"));
                break;
            }
            const char* rawEntryName = archive_entry_pathname_utf8(entry);
            if (rawEntryName == nullptr || rawEntryName[0] == '\0') {
                rawEntryName = archive_entry_pathname(entry);
            }
            std::string cleanPath = sanitizeRelativeEntryPath(rawEntryName);
            if (cleanPath.empty()) {
                archive_read_data_skip(a);
                continue;
            }
            la_int64_t entrySize = archive_entry_size(entry);
            if (entrySize < 0) entrySize = 0;
            bool isDir = (archive_entry_filetype(entry) == AE_IFDIR);
            int encFlags = archive_entry_is_encrypted(entry);
            bool isEncrypted = (encFlags != 0);

            std::ostringstream line;
            line << "ENTRY\t" << cleanPath << "\t" << entrySize << "\t"
                 << (isDir ? "1" : "0") << "\t" << (isEncrypted ? "1" : "0");
            rows.push_back(line.str());
            archive_read_data_skip(a);
        }
    }

    archive_read_free(a);

    jobjectArray outArray = env->NewObjectArray(static_cast<jsize>(rows.size()), stringClass, nullptr);
    for (size_t i = 0; i < rows.size(); ++i) {
        jstring jRow = env->NewStringUTF(rows[i].c_str());
        env->SetObjectArrayElement(outArray, static_cast<jsize>(i), jRow);
        env->DeleteLocalRef(jRow);
    }
    return outArray;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_anywall_converter_data_engines_LibArchiveRarBridge_nativeExtractRarArchive(
        JNIEnv* env,
        jobject /* thiz */,
        jobjectArray volumePaths,
        jstring outputDirPath,
        jobjectArray selectedEntries,
        jstring passphrase) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (volumePaths == nullptr || outputDirPath == nullptr) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    const char* rawOutDir = env->GetStringUTFChars(outputDirPath, nullptr);
    std::string baseOutDir(rawOutDir ? rawOutDir : "");
    if (rawOutDir != nullptr) {
        env->ReleaseStringUTFChars(outputDirPath, rawOutDir);
    }

    const jsize volCount = env->GetArrayLength(volumePaths);
    std::vector<std::string> ownedPaths;
    ownedPaths.reserve(static_cast<size_t>(volCount));
    for (jsize i = 0; i < volCount; ++i) {
        auto jPath = static_cast<jstring>(env->GetObjectArrayElement(volumePaths, i));
        if (jPath != nullptr) {
            const char* raw = env->GetStringUTFChars(jPath, nullptr);
            if (raw != nullptr) {
                ownedPaths.emplace_back(raw);
                env->ReleaseStringUTFChars(jPath, raw);
            }
            env->DeleteLocalRef(jPath);
        }
    }

    std::vector<const char*> cFilenames;
    cFilenames.reserve(ownedPaths.size() + 1);
    for (const auto& p : ownedPaths) {
        cFilenames.push_back(p.c_str());
    }
    cFilenames.push_back(nullptr);

    std::unordered_set<std::string> filterSet;
    if (selectedEntries != nullptr) {
        const jsize selCount = env->GetArrayLength(selectedEntries);
        for (jsize i = 0; i < selCount; ++i) {
            auto jSel = static_cast<jstring>(env->GetObjectArrayElement(selectedEntries, i));
            if (jSel != nullptr) {
                const char* rawSel = env->GetStringUTFChars(jSel, nullptr);
                if (rawSel != nullptr) {
                    std::string cleanSel = sanitizeRelativeEntryPath(rawSel);
                    if (!cleanSel.empty()) {
                        filterSet.insert(cleanSel);
                    }
                    env->ReleaseStringUTFChars(jSel, rawSel);
                }
                env->DeleteLocalRef(jSel);
            }
        }
    }

    std::string passStr;
    if (passphrase != nullptr) {
        const char* rawPass = env->GetStringUTFChars(passphrase, nullptr);
        if (rawPass != nullptr) {
            passStr = rawPass;
            env->ReleaseStringUTFChars(passphrase, rawPass);
        }
    }

    struct archive* a = archive_read_new();
    configureRarReader(a, passStr);

    std::vector<std::string> outputs;
    int openRc = archive_read_open_filenames(a, cFilenames.data(), 64 * 1024);
    if (openRc != ARCHIVE_OK) {
        const char* err = archive_error_string(a);
        outputs.push_back(std::string("ERR\t") + (err ? err : "No se pudo abrir el archivo RAR"));
    } else {
        struct archive_entry* entry = nullptr;
        while (true) {
            int r = archive_read_next_header(a, &entry);
            if (r == ARCHIVE_EOF) {
                break;
            }
            if (r < ARCHIVE_WARN) {
                const char* err = archive_error_string(a);
                outputs.push_back(std::string("ERR\t") + (err ? err : "Error al leer cabecera RAR"));
                break;
            }
            if (archive_entry_filetype(entry) == AE_IFDIR) {
                archive_read_data_skip(a);
                continue;
            }

            const char* rawEntryName = archive_entry_pathname_utf8(entry);
            if (rawEntryName == nullptr || rawEntryName[0] == '\0') {
                rawEntryName = archive_entry_pathname(entry);
            }
            std::string cleanRelPath = sanitizeRelativeEntryPath(rawEntryName);
            if (cleanRelPath.empty()) {
                archive_read_data_skip(a);
                continue;
            }

            if (!filterSet.empty() && filterSet.find(cleanRelPath) == filterSet.end()) {
                archive_read_data_skip(a);
                continue;
            }

            std::string destFullPath = baseOutDir + "/" + cleanRelPath;
            if (!mkdirsForFile(destFullPath)) {
                outputs.push_back("ERR\tNo se pudo crear directorio destino para " + cleanRelPath);
                break;
            }

            FILE* outFp = std::fopen(destFullPath.c_str(), "wb");
            if (outFp == nullptr) {
                outputs.push_back("ERR\tNo se pudo abrir archivo destino para " + cleanRelPath);
                break;
            }

            const void* buff = nullptr;
            size_t size = 0;
            la_int64_t offset = 0;
            bool entryFailed = false;

            while (true) {
                int dr = archive_read_data_block(a, &buff, &size, &offset);
                if (dr == ARCHIVE_EOF) {
                    break;
                }
                if (dr < ARCHIVE_WARN) {
                    const char* err = archive_error_string(a);
                    outputs.push_back(std::string("ERR\t") + (err ? err : "Error al descomprimir datos RAR"));
                    entryFailed = true;
                    break;
                }
                if (size > 0 && buff != nullptr) {
                    std::fseek(outFp, static_cast<long>(offset), SEEK_SET);
                    size_t written = std::fwrite(buff, 1, size, outFp);
                    if (written != size) {
                        outputs.push_back("ERR\tError de escritura en disco al extraer " + cleanRelPath);
                        entryFailed = true;
                        break;
                    }
                }
            }

            std::fclose(outFp);
            if (entryFailed) {
                std::remove(destFullPath.c_str());
                break;
            }
            outputs.push_back("OK\t" + cleanRelPath);
        }
    }

    archive_read_free(a);

    jobjectArray outArray = env->NewObjectArray(static_cast<jsize>(outputs.size()), stringClass, nullptr);
    for (size_t i = 0; i < outputs.size(); ++i) {
        jstring jItem = env->NewStringUTF(outputs[i].c_str());
        env->SetObjectArrayElement(outArray, static_cast<jsize>(i), jItem);
        env->DeleteLocalRef(jItem);
    }
    return outArray;
}
