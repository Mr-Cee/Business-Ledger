#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <shobjidl.h>
#include <propkey.h>
#include <propvarutil.h>
#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>
#include <string>

static const wchar_t* APP_ID = L"BusinessLedger.Desktop";
struct ComScope {
    HRESULT result;
    ComScope() : result(CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED)) {}
    ~ComScope() { if (SUCCEEDED(result)) CoUninitialize(); }
};
static std::wstring wide(JNIEnv* env, jstring value) {
    if (!value) return L"";
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) return L"";
    std::wstring text(reinterpret_cast<const wchar_t*>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars); return text;
}
static HRESULT put(IPropertyStore* store, const PROPERTYKEY& key, const wchar_t* text) {
    PROPVARIANT value; HRESULT hr = InitPropVariantFromString(text, &value);
    if (SUCCEEDED(hr)) { hr = store->SetValue(key, value); PropVariantClear(&value); }
    return hr;
}
static std::wstring get(IPropertyStore* store, const PROPERTYKEY& key) {
    PROPVARIANT value; PropVariantInit(&value); std::wstring text;
    if (SUCCEEDED(store->GetValue(key, &value)) && value.vt == VT_LPWSTR && value.pwszVal) text = value.pwszVal;
    PropVariantClear(&value); return text;
}

extern "C" __declspec(dllexport) HRESULT WINAPI LedgerSetShortcutIdentity(const wchar_t* path) {
    ComScope com; IShellLinkW* link = nullptr; IPersistFile* file = nullptr; IPropertyStore* store = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&link));
    if (SUCCEEDED(hr)) hr = link->QueryInterface(IID_PPV_ARGS(&file));
    if (SUCCEEDED(hr)) hr = file->Load(path, STGM_READWRITE);
    if (SUCCEEDED(hr)) hr = link->QueryInterface(IID_PPV_ARGS(&store));
    if (SUCCEEDED(hr)) hr = put(store, PKEY_AppUserModel_ID, APP_ID);
    if (SUCCEEDED(hr)) hr = store->Commit();
    if (SUCCEEDED(hr)) hr = file->Save(path, TRUE);
    if (store) store->Release(); if (file) file->Release(); if (link) link->Release(); return hr;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_businessledger_WindowsTaskbar_initializeNative(JNIEnv*, jclass) {
    return SUCCEEDED(SetCurrentProcessExplicitAppUserModelID(APP_ID)) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jstring JNICALL Java_com_businessledger_WindowsTaskbar_processIdentityNative(JNIEnv* env, jclass) {
    PWSTR text = nullptr; std::wstring result;
    if (SUCCEEDED(GetCurrentProcessExplicitAppUserModelID(&text)) && text) { result = text; CoTaskMemFree(text); }
    return env->NewString(reinterpret_cast<const jchar*>(result.data()), static_cast<jsize>(result.size()));
}

// JAWT supplies the actual HWND owned by Swing, including invisible displayable test windows.
struct DrawingWindow {
    HMODULE jawtModule = nullptr; JAWT awt = {}; JAWT_DrawingSurface* surface = nullptr;
    JAWT_DrawingSurfaceInfo* info = nullptr; bool locked = false; HWND window = nullptr;
    DrawingWindow(JNIEnv* env, jobject component, const std::wstring& jawtPath) {
        jawtModule = LoadLibraryExW(jawtPath.c_str(), nullptr, LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR | LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
        if (!jawtModule) return;
        typedef jboolean (JNICALL *GetAwt)(JNIEnv*, JAWT*);
        GetAwt getter = reinterpret_cast<GetAwt>(GetProcAddress(jawtModule, "JAWT_GetAWT"));
        awt.version = JAWT_VERSION_1_4;
        if (!getter || getter(env, &awt) == JNI_FALSE) return;
        surface = awt.GetDrawingSurface(env, component); if (!surface) return;
        if (surface->Lock(surface) & JAWT_LOCK_ERROR) return; locked = true;
        info = surface->GetDrawingSurfaceInfo(surface); if (!info || !info->platformInfo) return;
        window = static_cast<JAWT_Win32DrawingSurfaceInfo*>(info->platformInfo)->hwnd;
    }
    ~DrawingWindow() {
        if (info) surface->FreeDrawingSurfaceInfo(info);
        if (locked) surface->Unlock(surface);
        if (surface) awt.FreeDrawingSurface(surface);
        if (jawtModule) FreeLibrary(jawtModule);
    }
};
extern "C" JNIEXPORT jboolean JNICALL Java_com_businessledger_WindowsTaskbar_configureNative(
        JNIEnv* env, jclass, jobject component, jstring icon, jstring launcher, jstring jawtPath) {
    DrawingWindow drawing(env, component, wide(env, jawtPath)); if (!drawing.window) return JNI_FALSE;
    ComScope com; IPropertyStore* store = nullptr;
    HRESULT hr = SHGetPropertyStoreForWindow(drawing.window, IID_PPV_ARGS(&store));
    if (FAILED(hr)) return JNI_FALSE;
    wchar_t module[MAX_PATH * 4]; HMODULE self = nullptr;
    GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        reinterpret_cast<LPCWSTR>(&LedgerSetShortcutIdentity), &self);
    DWORD length = GetModuleFileNameW(self, module, _countof(module));
    std::wstring name = L"@" + std::wstring(module, length) + L",-101";
    std::wstring command = L"\"" + wide(env, launcher) + L"\"";
    std::wstring resource = wide(env, icon) + L",0";
    hr = put(store, PKEY_AppUserModel_RelaunchCommand, command.c_str());
    if (SUCCEEDED(hr)) hr = put(store, PKEY_AppUserModel_RelaunchDisplayNameResource, name.c_str());
    if (SUCCEEDED(hr)) hr = put(store, PKEY_AppUserModel_RelaunchIconResource, resource.c_str());
    if (SUCCEEDED(hr)) hr = put(store, PKEY_AppUserModel_ID, APP_ID);
    if (SUCCEEDED(hr)) hr = store->Commit();
    store->Release(); return SUCCEEDED(hr) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_com_businessledger_WindowsTaskbar_windowIdentityNative(
        JNIEnv* env, jclass, jobject component, jstring jawtPath) {
    DrawingWindow drawing(env, component, wide(env, jawtPath)); if (!drawing.window) return nullptr;
    ComScope com; IPropertyStore* store = nullptr;
    if (FAILED(SHGetPropertyStoreForWindow(drawing.window, IID_PPV_ARGS(&store)))) return nullptr;
    const PROPERTYKEY keys[] = {PKEY_AppUserModel_ID, PKEY_AppUserModel_RelaunchIconResource, PKEY_AppUserModel_RelaunchCommand};
    jobjectArray result = env->NewObjectArray(3, env->FindClass("java/lang/String"), nullptr);
    for (int i = 0; i < 3; i++) { std::wstring value = get(store, keys[i]);
        jstring text = env->NewString(reinterpret_cast<const jchar*>(value.data()), static_cast<jsize>(value.size()));
        env->SetObjectArrayElement(result, i, text); env->DeleteLocalRef(text); }
    store->Release(); return result;
}
extern "C" JNIEXPORT void JNICALL Java_com_businessledger_WindowsTaskbar_clearNative(
        JNIEnv* env, jclass, jobject component, jstring jawtPath) {
    DrawingWindow drawing(env, component, wide(env, jawtPath)); if (!drawing.window) return;
    ComScope com; IPropertyStore* store = nullptr;
    if (FAILED(SHGetPropertyStoreForWindow(drawing.window, IID_PPV_ARGS(&store)))) return;
    PROPVARIANT empty; PropVariantInit(&empty);
    const PROPERTYKEY keys[] = {PKEY_AppUserModel_ID, PKEY_AppUserModel_RelaunchIconResource,
        PKEY_AppUserModel_RelaunchCommand, PKEY_AppUserModel_RelaunchDisplayNameResource};
    for (const PROPERTYKEY& key : keys) store->SetValue(key, empty);
    store->Release();
}
