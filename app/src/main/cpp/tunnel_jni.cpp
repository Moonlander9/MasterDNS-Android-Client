#include <jni.h>

#include <atomic>
#include <mutex>
#include <string>
#include <thread>

extern "C" {
int hev_socks5_tunnel_main_from_str(const unsigned char *config_str,
                                    unsigned int config_len, int tun_fd);
void hev_socks5_tunnel_quit(void);
}

namespace {
std::mutex g_mutex;
std::thread g_worker;
std::atomic<bool> g_running(false);
std::atomic<int> g_last_result(0);

int start_tunnel(std::string config_yaml, int tun_fd) {
    if (tun_fd < 0) {
        return -22;
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_running.load() && g_worker.joinable()) {
        g_worker.join();
    }

    if (g_running.load()) {
        return -16;
    }

    if (config_yaml.empty()) {
        return -22;
    }

    g_running.store(true);
    g_last_result.store(0);

    g_worker = std::thread([config = std::move(config_yaml), tun_fd]() mutable {
        const auto *raw = reinterpret_cast<const unsigned char *>(config.data());
        int result =
            hev_socks5_tunnel_main_from_str(raw, static_cast<unsigned int>(config.size()), tun_fd);
        g_last_result.store(result);
        g_running.store(false);
    });

    return 0;
}

int stop_tunnel() {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_running.load() && !g_worker.joinable()) {
        return 0;
    }

    hev_socks5_tunnel_quit();

    if (g_worker.joinable()) {
        g_worker.join();
    }

    g_running.store(false);
    return 0;
}

bool is_running() {
    const bool running = g_running.load();

    if (!running) {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (g_worker.joinable()) {
            g_worker.join();
        }
    }

    return running;
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_masterdnsvpn_android_HevNativeBridge_nativeStart(JNIEnv *env, jobject,
                                                           jstring config_yaml,
                                                           jint tun_fd) {
    if (config_yaml == nullptr) {
        return -22;
    }

    const char *chars = env->GetStringUTFChars(config_yaml, nullptr);
    if (chars == nullptr) {
        return -12;
    }

    std::string config(chars);
    env->ReleaseStringUTFChars(config_yaml, chars);

    return start_tunnel(std::move(config), static_cast<int>(tun_fd));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_masterdnsvpn_android_HevNativeBridge_nativeStop(JNIEnv *, jobject) {
    return stop_tunnel();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_masterdnsvpn_android_HevNativeBridge_nativeIsRunning(JNIEnv *, jobject) {
    return is_running() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_masterdnsvpn_android_HevNativeBridge_nativeLastResult(JNIEnv *, jobject) {
    return g_last_result.load();
}
