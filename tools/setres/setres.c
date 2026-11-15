/*
 * setres: changes the resolution of Wine's virtual desktop.
 *
 *   setres.exe 800x600    switch the desktop to 800x600
 *   setres.exe reset      go back to the size the desktop was started with
 *
 * The change is made without CDS_FULLSCREEN, so Wine keeps it after this
 * program exits. Exit code: the ChangeDisplaySettingsEx result (0 = ok).
 *
 * Build: x86_64-w64-mingw32-gcc -O2 -s -municode -mwindows setres.c -o setres.exe
 */

#include <windows.h>
#include <shellapi.h>
#include <stdlib.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE prev, LPWSTR cmdline, int show)
{
    DEVMODEW mode = { .dmSize = sizeof(mode) };
    int argc, width, height;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    wchar_t *end;

    if (!argv || argc < 2) return DISP_CHANGE_BADPARAM;

    if (!_wcsicmp(argv[1], L"reset"))
        return ChangeDisplaySettingsExW(NULL, NULL, NULL, 0, NULL);

    width = wcstol(argv[1], &end, 10);
    if (*end == L'x' || *end == L'X') height = wcstol(end + 1, &end, 10);
    else if (argc > 2) height = wcstol(argv[2], &end, 10);
    else height = 0;
    if (width < 320 || height < 200) return DISP_CHANGE_BADPARAM;

    if (!EnumDisplaySettingsW(NULL, ENUM_CURRENT_SETTINGS, &mode)) return DISP_CHANGE_FAILED;
    mode.dmPelsWidth = width;
    mode.dmPelsHeight = height;
    mode.dmFields = DM_PELSWIDTH | DM_PELSHEIGHT;
    return ChangeDisplaySettingsExW(NULL, &mode, NULL, 0, NULL);
}
