#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Role DeepSeek — автоматическая сборка APK одной командой.

Скрипт сам:
  1. находит или скачивает JDK 17 (Temurin);
  2. находит или скачивает Android SDK (command line tools) и ставит
     platform-tools, platforms;android-34, build-tools;34.0.0;
  3. принимает лицензии Android SDK;
  4. запускает Gradle Wrapper и собирает APK;
  5. кладёт готовый файл рядом со скриптом.

Запуск (из папки проекта, там где settings.gradle.kts):
    python build_apk.py                 # debug APK
    python build_apk.py --release       # release APK (R8)
    python build_apk.py --install       # собрать и сразу поставить на подключённый телефон
    python build_apk.py --clean         # чистая сборка (удалить build/, .gradle/, .kotlin/)
    python build_apk.py --fresh-tools   # плюс перекачать JDK и Android SDK заново
    python build_apk.py --diag          # диагностика: где лежат исходники и что скажет компилятор

Если сборка падает с ошибкой KSP вида "error.NonExistentClass", скрипт сам сделает
чистую сборку и повторит попытку, а если не поможет — переберёт варианты подключения
каталога с исходниками (SRC_VARIANTS) и снова соберёт. Полный лог всегда пишется
в build-log.txt.

Требуется только Python 3.8+ (никаких pip-пакетов — только стандартная библиотека).
Первый запуск занимает 15-40 минут: скачивается JDK + Android SDK + Gradle + зависимости.
Следующие сборки — 1-3 минуты.
"""

import os
import re
import sys
import time
import shutil
import zipfile
import tarfile
import platform
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / ".build-tools"
IS_WINDOWS = platform.system() == "Windows"
JAVA_EXE = "java.exe" if IS_WINDOWS else "java"

SDK_PLATFORM = "android-34"
SDK_BUILD_TOOLS = "34.0.0"

# Единственный каталог, где должны лежать Kotlin-исходники (см. блок
# RD_SOURCES_BEGIN в app/build.gradle.kts). Любые .kt-файлы в других местах
# ломают KSP/Hilt.
EXPECTED_SRC_ROOT = "kotlin"

# Как сказать Gradle, что исходники лежат в ../kotlin. Официального способа для
# нестандартного каталога нет, поэтому при ошибке KSP вида error.NonExistentClass
# скрипт перебирает варианты по очереди и повторяет сборку.
# Вариант A — самый совместимый (Kotlin-плагин компилирует и java.srcDirs).
SRC_MARKER_BEGIN = "// >>> RD_SOURCES_BEGIN"
SRC_MARKER_END = "// >>> RD_SOURCES_END >>>"
SRC_VARIANT_HEADER = (
    "// >>> RD_SOURCES_BEGIN — этот блок управляется скриптом build_apk.py >>>\n"
    "// Kotlin-исходники лежат в ../kotlin (вместо стандартного app/src/main/java):\n"
    "// так короче дерево каталогов, а пакеты остаются настоящими —\n"
    "// ../kotlin/com/pythonistavp/roledeepseek/...\n"
    "// Ниже сказано Gradle/KSP, где искать исходники. Если KSP падает с\n"
    "// error.NonExistentClass, build_apk.py сам перебирает остальные варианты\n"
    "// подключения (SRC_VARIANTS в build_apk.py) и повторяет сборку.\n"
)
SRC_VARIANTS = [
    (
        "A",
        'java.srcDirs("../kotlin")',
        'android {\n'
        '    sourceSets {\n'
        '        getByName("main") {\n'
        '            java.srcDirs("../kotlin")\n'
        '        }\n'
        '    }\n'
        '}\n',
    ),
    (
        "B",
        'kotlin.srcDirs("../kotlin")',
        'android {\n'
        '    sourceSets {\n'
        '        getByName("main") {\n'
        '            kotlin.srcDirs("../kotlin")\n'
        '        }\n'
        '    }\n'
        '}\n',
    ),
    (
        "C",
        'java.srcDirs + kotlin.setSrcDirs',
        'android {\n'
        '    sourceSets {\n'
        '        getByName("main") {\n'
        '            java.srcDirs("../kotlin")\n'
        '        }\n'
        '    }\n'
        '}\n'
        'kotlin {\n'
        '    sourceSets.configureEach {\n'
        '        if (name == "main") {\n'
        '            this.kotlin.setSrcDirs(listOf("../kotlin"))\n'
        '        }\n'
        '    }\n'
        '}\n',
    ),
]

CMDLINE_TOOLS_URLS = {
    "Windows": "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
    "Linux": "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip",
    "Darwin": "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip",
}

RELEASE = "--release" in sys.argv
INSTALL = "--install" in sys.argv
CLEAN = "--clean" in sys.argv
FRESH_TOOLS = "--fresh-tools" in sys.argv
DIAG = "--diag" in sys.argv
LOG_FILE = ROOT / "build-log.txt"

# Чтобы русские буквы не роняли скрипт в старых консолях Windows (cp866/cp1251).
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass


def log(msg=""):
    try:
        print(msg, flush=True)
    except UnicodeEncodeError:
        print(str(msg).encode("ascii", "replace").decode("ascii"), flush=True)


def step(msg):
    log("")
    log("=" * 68)
    log("  " + msg)
    log("=" * 68)


def prepare_cmd(cmd):
    cmd = [str(c) for c in cmd]
    if IS_WINDOWS and cmd[0].lower().endswith((".bat", ".cmd")):
        # .bat нельзя запустить напрямую через CreateProcess, и путь может содержать
        # пробелы — оборачиваем в cmd.exe с двойными кавычками (документированный приём).
        cmd = 'cmd.exe /c "%s"' % subprocess.list2cmdline(cmd)
    return cmd


def run(cmd, env=None, capture=False, input_text=None):
    """Запуск внешней команды. Возвращает (код, вывод) при capture=True."""
    cmd = prepare_cmd(cmd)
    if capture:
        p = subprocess.run(
            cmd, env=env, cwd=str(ROOT),
            input=input_text, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        )
        return p.returncode, p.stdout or ""
    p = subprocess.run(
        cmd, env=env, cwd=str(ROOT),
        input=(input_text.encode() if input_text else None),
    )
    return p.returncode, ""


def run_stream(cmd, env, log_path):
    """Запуск с живым выводом в консоль + полной копией в build-log.txt."""
    cmd = prepare_cmd(cmd)
    with open(log_path, "w", encoding="utf-8", errors="replace") as logf:
        proc = subprocess.Popen(
            cmd, env=env, cwd=str(ROOT),
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace", bufsize=1,
        )
        for line in proc.stdout:
            logf.write(line)
            logf.flush()
            try:
                sys.stdout.write(line)
                sys.stdout.flush()
            except UnicodeEncodeError:
                sys.stdout.write(line.encode("ascii", "replace").decode("ascii"))
                sys.stdout.flush()
        proc.wait()
        return proc.returncode


def stop_gradle_daemons(wrapper, env):
    """Гасим фоновые Gradle-демоны: иначе на Windows они держат файлы build/ и чистка не срабатывает."""
    try:
        run([str(wrapper), "--stop"], env=env, capture=True)
    except Exception:
        pass


def clean_build_outputs():
    """Чистим только артефакты сборки — скачанные JDK/SDK (.build-tools) не трогаем.
    Удаление проверяем: молча не удалившиеся папки — самая частая причина
    «чистка не помогла»."""
    for rel in ("build", "app/build", ".gradle", ".kotlin", "app/.kotlin"):
        target = ROOT / rel
        if not target.exists():
            continue
        log("  [clean] удаляю %s" % rel)
        shutil.rmtree(target, ignore_errors=True)
        if target.exists():
            backup = ROOT / ("%s_old" % rel.replace("/", "_"))
            try:
                if backup.exists():
                    shutil.rmtree(backup, ignore_errors=True)
                shutil.move(str(target), str(backup))
                log("  [i] файлы были заняты — отодвинул каталог в %s" % backup.name)
            except OSError as e:
                log("  [!] НЕ УДАЛОСЬ удалить %s (%s) — возможно, папка открыта в проводнике" % (rel, e))


KSP_DIAG_INIT = r"""
gradle.projectsEvaluated {
    def out = { String s -> println "KSPDIAG " + s }
    def app = rootProject.findProject(":app")
    if (app == null) { out("no :app project"); return }
    try {
        def androidExt = app.extensions.findByName("android")
        out("android.main.java.srcDirs = " + androidExt.sourceSets.getByName("main").java.srcDirs)
    } catch (Throwable e) { out("android err: " + e) }
    try {
        def kotlinExt = app.extensions.findByName("kotlin")
        out("kotlin.main.kotlin.srcDirs = " + kotlinExt.sourceSets.getByName("main").kotlin.srcDirs)
    } catch (Throwable e) { out("kotlin err: " + e) }
    try {
        def t = app.tasks.findByName("kspDebugKotlin")
        if (t == null) { t = app.tasks.named("kspDebugKotlin").get() }
        def files = t.sources.files
        out("ksp sources total = " + files.size())
        out("ksp sees SettingsViewModel.kt = " + files.any { it.name == "SettingsViewModel.kt" })
        out("ksp sees SettingsRepository.kt = " + files.any { it.name == "SettingsRepository.kt" })
        out("ksp sees UsageRepository.kt = " + files.any { it.name == "UsageRepository.kt" })
        out("ksp sees GetStatisticsUseCase.kt = " + files.any { it.name == "GetStatisticsUseCase.kt" })
        out("ksp sees BackupUseCase.kt = " + files.any { it.name == "BackupUseCase.kt" })
        def sample = files.findAll { it.name.endsWith(".kt") }.take(6)
        sample.each { out("ksp src: " + it) }
    } catch (Throwable e) { out("ksp err: " + e) }
}
"""


def print_config_diag(wrapper, env):
    """Печатает, какие именно исходники Gradle реально отдаёт KSP."""
    step("ДИАГНОСТИКА: что именно видит KSP")
    init_script = TOOLS / "ksp-diag.gradle"
    init_script.parent.mkdir(parents=True, exist_ok=True)
    init_script.write_text(KSP_DIAG_INIT, encoding="utf-8")
    config_log = ROOT / "build-config-log.txt"
    run_stream([str(wrapper), "help", "-I", str(init_script), "-q",
                "--no-daemon", "--console=plain"], env, config_log)
    text = config_log.read_text(encoding="utf-8", errors="replace") if config_log.exists() else ""
    lines = [l.strip() for l in text.splitlines() if l.strip().startswith("KSPDIAG")]
    if not lines:
        log("  [!] не удалось получить данные о конфигурации (см. %s)" % config_log)
    for l in lines:
        log("  " + l[len("KSPDIAG"):].strip())
    log("")


def kotlin_errors_from_log():
    noise = re.compile(r"Hilt_|Dagger|_HiltComponents|_Impl|dagger|javax\.inject|"
                       r"BuildConfig|Room|_Factory|Assistant|Generated|_Companion")
    errors = []
    log_text = LOG_FILE.read_text(encoding="utf-8", errors="replace") if LOG_FILE.exists() else ""
    for line in log_text.splitlines():
        s = line.strip()
        if s.startswith("e: ") and not noise.search(s):
            errors.append(s)
    return list(dict.fromkeys(errors))


def compile_without_ksp(java_home, sdk, wrapper, env):
    """Главная диагностика: компилируем Kotlin-код, отключив KSP (Hilt/Room).
    Так видны НАСТОЯЩИЕ ошибки в исходниках, а не следствия."""
    step("ДИАГНОСТИКА: компилирую Kotlin без KSP (Hilt/Room отключены)")
    log("  Ошибки про Hilt_/Dagger/сгенерированный код здесь ожидаемы и отфильтрованы.")
    run_stream([str(wrapper), ":app:compileDebugKotlin", "-x", ":app:kspDebugKotlin",
                "--continue", "--no-daemon", "--console=plain"],
               env, LOG_FILE)
    errors = kotlin_errors_from_log()
    log("")
    log("  >>> НАСТОЯЩИХ ОШИБОК KOTLIN: %d" % len(errors))
    for s in errors[:40]:
        log("      %s" % s)
    log("")
    log("  Полный лог: %s" % LOG_FILE)
    return errors


def download(url, dest, label):
    """Скачать файл с прогрессом. Кэшируется: повторный вызов не качает заново."""
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        log("  [кэш] %s уже скачан (%.0f МБ)" % (label, dest.stat().st_size / 1048576))
        return dest

    log("  [ск] Скачиваю %s" % label)
    log("        %s" % url)
    tmp = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "RoleDeepSeekBuilder/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(tmp, "wb") as f:
        total = int(resp.headers.get("Content-Length") or 0)
        got = 0
        t0 = time.time()
        last = 0.0
        while True:
            chunk = resp.read(1024 * 256)
            if not chunk:
                break
            f.write(chunk)
            got += len(chunk)
            now = time.time()
            if now - last > 0.4:
                last = now
                speed = got / max(now - t0, 0.001) / 1048576
                if total:
                    pct = got * 100.0 / total
                    bar = "#" * int(pct / 3)
                    log("        [%-34s] %5.1f%%  %.0f/%.0f МБ  %.1f МБ/с"
                        % (bar, pct, got / 1048576, total / 1048576, speed))
                else:
                    log("        %.0f МБ  %.1f МБ/с" % (got / 1048576, speed))
    tmp.replace(dest)
    log("  [ok] %s скачан (%.0f МБ)" % (label, dest.stat().st_size / 1048576))
    return dest


def extract(archive, dest):
    dest = Path(dest)
    if dest.exists():
        shutil.rmtree(dest, ignore_errors=True)
    dest.mkdir(parents=True, exist_ok=True)
    with open(archive, "rb") as f:
        magic = f.read(4)
    if magic[:2] == b"PK":
        with zipfile.ZipFile(archive) as z:
            z.extractall(dest)
    else:
        with tarfile.open(archive, "r:gz") as t:
            t.extractall(dest)
    return dest


def java_major(java_exe):
    try:
        code, out = run([str(java_exe), "-version"], capture=True)
    except Exception:
        return 0
    m = re.search(r'version "(\d+)(?:\.(\d+))?', out)
    if not m:
        return 0
    major = int(m.group(1))
    if major == 1 and m.group(2):
        major = int(m.group(2))
    return major


def find_java_home_under(base, depth=3):
    """Ищем каталог, содержащий bin/java (у macOS JDK лежит глубже, в Contents/Home)."""
    base = Path(base)
    if not base.exists():
        return None
    if (base / "bin" / JAVA_EXE).exists():
        return base
    if depth <= 0:
        return None
    try:
        children = sorted([p for p in base.iterdir() if p.is_dir()])
    except OSError:
        return None
    for c in children:
        found = find_java_home_under(c, depth - 1)
        if found:
            return found
    return None


def java_candidates():
    cands = []
    for var in ("ROLE_DEEPSEEK_JDK", "JAVA_HOME"):
        v = os.environ.get(var)
        if v:
            cands.append(Path(v))
    if platform.system() == "Darwin":
        try:
            out = subprocess.run(["/usr/libexec/java_home", "-v", "17+"],
                                 capture_output=True, text=True).stdout.strip()
            if out:
                cands.append(Path(out))
        except Exception:
            pass
    which = shutil.which("java")
    if which:
        try:
            cands.append(Path(which).resolve().parent.parent)
        except OSError:
            pass
    cands.append(TOOLS / "jdk")
    return cands


def detect_existing_java():
    for c in java_candidates():
        home = c if (c / "bin" / JAVA_EXE).exists() else find_java_home_under(c, depth=3)
        if home and java_major(home / "bin" / JAVA_EXE) >= 17:
            return home
    return None


def install_java():
    step("ШАГ 1/4 — JDK 17")
    home = detect_existing_java()
    if home:
        log("  [ok] Найден подходящий JDK: %s" % home)
        return home

    machine = platform.machine().lower()
    arch = "aarch64" if machine in ("arm64", "aarch64") else "x64"
    system = platform.system()
    os_part = {"Windows": "windows", "Darwin": "mac"}.get(system, "linux")
    url = ("https://api.adoptium.net/v3/binary/latest/17/ga/%s/%s/jdk/hotspot/normal/eclipse"
           % (os_part, arch))
    archive = TOOLS / ("jdk17.zip" if system == "Windows" else "jdk17.tar.gz")
    download(url, archive, "JDK 17 (Adoptium Temurin)")
    log("  Распаковываю JDK (это может занять минуту)...")
    dest = extract(archive, TOOLS / "jdk")
    home = find_java_home_under(dest)
    if not home:
        raise RuntimeError("Не удалось найти bin/java в распакованном JDK")
    log("  [ok] JDK готов: %s" % home)
    return home


def sdkmanager_path(sdk):
    return sdk / "cmdline-tools" / "latest" / "bin" / (
        "sdkmanager.bat" if IS_WINDOWS else "sdkmanager")


def tool_env(java_home, sdk=None):
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home)
    path = [str(Path(java_home) / "bin")]
    if sdk:
        path += [str(Path(sdk) / "platform-tools"),
                 str(Path(sdk) / "cmdline-tools" / "latest" / "bin")]
    env["PATH"] = os.pathsep.join(path + [env.get("PATH", "")])
    return env


def sdk_candidates():
    cands = []
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_SDK"):
        v = os.environ.get(var)
        if v:
            cands.append(Path(v))
    home = Path.home()
    cands += [
        home / "AppData" / "Local" / "Android" / "Sdk",   # Windows
        home / "Library" / "Android" / "sdk",             # macOS
        home / "Android" / "Sdk",                         # Linux
        TOOLS / "android-sdk",
    ]
    return cands


def install_android_sdk(java_home):
    step("ШАГ 2/4 — Android SDK (platform 34, build-tools 34)")

    sdk = None
    for c in sdk_candidates():
        if (c / "platforms" / SDK_PLATFORM).exists() and (c / "build-tools").exists():
            sdk = c
            log("  [ok] Использую уже установленный Android SDK: %s" % sdk)
            break
        if c == TOOLS / "android-sdk" and (c / "cmdline-tools").exists():
            sdk = c
            break
    if sdk is None:
        sdk = TOOLS / "android-sdk"
        log("  Android SDK будет установлен в: %s" % sdk)

    mgr = sdkmanager_path(sdk)
    if not mgr.exists():
        url = CMDLINE_TOOLS_URLS.get(platform.system())
        if not url:
            raise RuntimeError("Неизвестная ОС: " + platform.system())
        archive = TOOLS / "cmdline-tools.zip"
        download(url, archive, "Android command line tools")
        log("  Распаковываю command line tools...")
        tmp = extract(archive, TOOLS / "cmdline-tools-tmp")
        (sdk / "cmdline-tools").mkdir(parents=True, exist_ok=True)
        dst = sdk / "cmdline-tools" / "latest"
        if dst.exists():
            shutil.rmtree(dst, ignore_errors=True)
        src = tmp / "cmdline-tools"
        shutil.move(str(src if src.exists() else tmp), str(dst))
        shutil.rmtree(TOOLS / "cmdline-tools-tmp", ignore_errors=True)
        mgr = sdkmanager_path(sdk)

    if not mgr.exists():
        raise RuntimeError("sdkmanager не найден: %s" % mgr)

    env = tool_env(java_home, sdk)
    sdk_arg = "--sdk_root=%s" % sdk

    log("  Принимаю лицензии Android SDK...")
    run([str(mgr), sdk_arg, "--licenses"], env=env, capture=True, input_text="y\n" * 100)

    log("  Устанавливаю: platform-tools, platforms;%s, build-tools;%s"
        % (SDK_PLATFORM, SDK_BUILD_TOOLS))
    code, _ = run([str(mgr), sdk_arg, "platform-tools",
                   "platforms;%s" % SDK_PLATFORM,
                   "build-tools;%s" % SDK_BUILD_TOOLS], env=env)
    if code != 0:
        raise RuntimeError("sdkmanager завершился с ошибкой (код %s)" % code)
    log("  [ok] Android SDK готов")
    return sdk


def write_local_properties(sdk):
    """Gradle ищет путь к SDK в local.properties."""
    path = str(Path(sdk).resolve()).replace("\\", "/")
    (ROOT / "local.properties").write_text("sdk.dir=%s\n" % path, encoding="utf-8")
    log("  [ok] local.properties → sdk.dir=%s" % path)


def gradle_task():
    return "assembleRelease" if RELEASE else "assembleDebug"


def run_gradle_once(java_home, sdk, wrapper, env):
    log("  Запускаю: %s %s" % (wrapper.name, gradle_task()))
    log("  (Gradle скачает сам себя и все зависимости — ниже идёт его вывод)")
    # ksp.incremental=false: KSP обрабатывает все исходники заново. Немного медленнее,
    # зато исключает самый неприятный класс ошибок вида error.NonExistentClass.
    code = run_stream([str(wrapper), gradle_task(), "--no-daemon", "--console=plain",
                       "-Pksp.incremental=false"],
                      env=env, log_path=LOG_FILE)
    return code, LOG_FILE.read_text(encoding="utf-8", errors="replace") if LOG_FILE.exists() else ""


def scan_kotlin_sources():
    """Возвращает (файлы, корни, дубликаты, несовпадения путей) по всему проекту."""
    skip = {".build-tools", "build", ".gradle", ".kotlin", ".git", ".idea", "captures",
            "gradle", "_stray_backup"}
    files = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in skip and not d.startswith(".")]
        for fn in filenames:
            if fn.endswith(".kt"):
                files.append(Path(dirpath) / fn)

    roots, mismatched, decls = {}, [], {}
    for f in files:
        try:
            text = f.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        m = re.search(r"(?m)^\s*package\s+([\w.]+)", text)
        pkg = m.group(1) if m else ""
        pkg_parts = [p for p in pkg.split(".") if p]
        dirs = list(f.relative_to(ROOT).parts)[:-1]
        if pkg_parts and dirs[-len(pkg_parts):] == pkg_parts:
            root = "/".join(dirs[:len(dirs) - len(pkg_parts)]) or "<корень проекта>"
        else:
            root = "<путь не совпадает с package>"
            mismatched.append(f)
        roots.setdefault(root, []).append(f)
        for dm in re.finditer(
                r"(?m)^(?:@\w+(?:\([^)]*\))?\s*)*(?:public |internal |private |sealed |"
                r"abstract |open |data |enum |value )*(class|interface|object)\s+([A-Za-z_]\w*)",
                text):
            decls.setdefault(pkg + "." + dm.group(2), []).append(f)
    dupes = {k: v for k, v in decls.items() if len({str(x) for x in v}) > 1}
    return files, roots, dupes, mismatched


def quarantine_stray_duplicates(roots, dupes):
    """
    Лишние копии исходников (например kotlin/kotlin/... или app/src/main/kotlin/...)
    ломают KSP: одна и та же сущность оказывается объявлена дважды. Такие копии
    уносим в _stray_backup/ — файлы никуда не пропадают, но сборка разблокируется.
    """
    stray_roots = [r for r in roots if r != EXPECTED_SRC_ROOT]
    if not stray_roots:
        return 0
    canonical = {str(f) for f in roots.get(EXPECTED_SRC_ROOT, [])}
    stray_files = []
    for r in stray_roots:
        stray_files += roots[r]

    moved = 0
    for f in stray_files:
        names = [k for k, v in dupes.items() if f in v]
        if not names or not any(canonical & {str(x) for x in dupes[k]} for k in names):
            log("  [!] Не понимаю, что делать с файлом: %s — оставляю как есть" % f.relative_to(ROOT))
            continue
        target = ROOT / "_stray_backup" / f.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            shutil.move(str(f), str(target))
            log("  [stray] лишняя копия перенесена в _stray_backup/: %s" % f.relative_to(ROOT))
            moved += 1
        except OSError as e:
            log("  [!] не смог перенести %s: %s" % (f, e))
    return moved


def check_source_layout(auto_fix):
    """Проверяем, что исходники лежат ровно в одной папке и без дублей."""
    files, roots, dupes, mismatched = scan_kotlin_sources()
    problems = []
    if len(roots) > 1:
        problems.append("исходники найдены в нескольких каталогах: "
                        + ", ".join(sorted(roots)))
    if dupes:
        problems.append("класс(ы) объявлены в нескольких файлах: "
                        + ", ".join(sorted(dupes)[:6]))
    if mismatched:
        problems.append("путь не совпадает с package: "
                        + ", ".join(str(f.relative_to(ROOT)) for f in mismatched[:4]))
    if not problems:
        return
    log("")
    log("  [!] ВНИМАНИЕ: структура исходников выглядит подозрительно")
    for p in problems:
        log("      - %s" % p)
    if auto_fix:
        log("  Пробую убрать лишние копии (они уедут в _stray_backup/, ничего не удаляется)...")
        if quarantine_stray_duplicates(roots, dupes):
            files, roots, dupes, mismatched = scan_kotlin_sources()
            if len(roots) == 1 and not dupes and not mismatched:
                log("  [ok] Исправлено — структура в порядке.")
            else:
                log("  [!!] Остались вопросы — запустите: build_apk.bat --diag")
    log("")


EXPECTED_KT_MIN = 70
REQUIRED_PACKAGES = ("data", "domain", "di", "navigation", "service", "ui", "util", "widget")


def check_project_completeness():
    """Ловим неполностью распакованный архив.

    Если на диск попала только часть .kt-файлов, Gradle падает с сотней
    «Unresolved reference 'data' / 'util'», а KSP — с error.NonExistentClass.
    Диагноз при этом очевиден только по количеству файлов, поэтому проверяем его
    до сборки и не тратим 20 минут впустую."""
    files, roots, dupes, mismatched = scan_kotlin_sources()
    if len(files) >= EXPECTED_KT_MIN:
        return True
    base = ROOT / EXPECTED_SRC_ROOT / "com" / "pythonistavp" / "roledeepseek"
    missing = []
    for pkg in REQUIRED_PACKAGES:
        d = base / pkg
        if not (d.exists() and any(d.rglob("*.kt"))):
            missing.append(pkg)
    log("")
    log("  " + "!" * 64)
    log("  ПРОЕКТ РАСПАКОВАН НЕПОЛНОСТЬЮ: .kt-файлов найдено %d, а должно быть ~81."
        % len(files))
    if missing:
        log("  Отсутствуют пакеты: %s" % ", ".join(missing))
    log("")
    log("  Что делать: возьмите архив Role-deepseek.zip и распакуйте его ПОВЕРХ папки")
    log("  проекта с заменой файлов — вернутся все исходники. Папку .build-tools не")
    log("  удаляйте: в ней уже скачанные JDK и Android SDK, заново качать не придётся.")
    log("  " + "!" * 64)
    return False


def ksp_broken(text):
    """Похоже ли, что сборку уронил именно KSP/Hilt (а не настоящая ошибка кода)."""
    return ("NonExistentClass" in text) or ("Error occurred in KSP" in text)


def first_error_lines(limit=60):
    """Строки 'e: ...' из build-log.txt как есть — без фильтра шума."""
    if not LOG_FILE.exists():
        return []
    text = LOG_FILE.read_text(encoding="utf-8", errors="replace")
    return [l.strip() for l in text.splitlines() if l.strip().startswith("e: ")][:limit]


def apply_source_variant(index):
    """Переписывает блок между маркерами RD_SOURCES_BEGIN/END в app/build.gradle.kts."""
    gradle = ROOT / "app" / "build.gradle.kts"
    if not gradle.exists():
        log("  [!] Не найден app/build.gradle.kts — автоподбор вариантов пропущен.")
        return False
    text = gradle.read_text(encoding="utf-8", errors="replace")
    start = text.find(SRC_MARKER_BEGIN)
    end = text.find(SRC_MARKER_END)
    if start == -1 or end == -1:
        log("  [!] В app/build.gradle.kts нет маркеров RD_SOURCES_BEGIN/RD_SOURCES_END.")
        log("      Возьмите build.gradle.kts из свежего архива проекта — тогда скрипт")
        log("      сможет сам перебрать варианты подключения исходников.")
        return False
    end += len(SRC_MARKER_END)
    backup = gradle.with_name("build.gradle.kts.bak")
    if not backup.exists():
        shutil.copyfile(str(gradle), str(backup))
    gradle.write_text(
        text[:start] + SRC_VARIANT_HEADER + SRC_VARIANTS[index][2] + SRC_MARKER_END + text[end:],
        encoding="utf-8",
    )
    return True


def check_gradle_markers():
    gradle = ROOT / "app" / "build.gradle.kts"
    if not gradle.exists():
        return False
    text = gradle.read_text(encoding="utf-8", errors="replace")
    if SRC_MARKER_BEGIN in text and SRC_MARKER_END in text:
        return True
    log("")
    log("  [!] В app/build.gradle.kts нет блока RD_SOURCES_BEGIN/RD_SOURCES_END.")
    log("      Файл остался от старой версии проекта. Возьмите app/build.gradle.kts")
    log("      из свежего архива, иначе автоподбор вариантов подключения исходников")
    log("      работать не будет.")
    log("")
    return False


def build_apk(java_home, sdk):
    step("ШАГ 3/4 — Сборка APK через Gradle (первый раз долго)")
    write_local_properties(sdk)

    wrapper = ROOT / ("gradlew.bat" if IS_WINDOWS else "gradlew")
    if not wrapper.exists():
        raise RuntimeError("Не найден Gradle Wrapper (%s) — скрипт должен лежать в корне проекта."
                           % wrapper.name)
    if not IS_WINDOWS:
        os.chmod(str(wrapper), 0o755)

    env = tool_env(java_home, sdk)
    env["ANDROID_HOME"] = str(sdk)
    env["ANDROID_SDK_ROOT"] = str(sdk)

    if CLEAN:
        log("  [--clean] Чистая сборка: удаляю артефакты прошлых сборок")
        stop_gradle_daemons(wrapper, env)
        clean_build_outputs()

    variant = 0
    code, out = run_gradle_once(java_home, sdk, wrapper, env)

    # error.NonExistentClass в InjectProcessingStep — самый частый сбой KSP/Hilt.
    # Причин две: (1) испорченный инкрементальный кэш KSP, (2) неудачный способ
    # подключения каталога ../kotlin. Поэтому сначала повторяем тот же вариант
    # начисто, а затем перебираем остальные варианты подключения исходников.
    if code != 0 and ksp_broken(out):
        log("")
        log("  [!!] KSP/Hilt не смог разобрать типы. Собираю начисто тем же вариантом...")
        stop_gradle_daemons(wrapper, env)
        clean_build_outputs()
        code, out = run_gradle_once(java_home, sdk, wrapper, env)

    while code != 0 and ksp_broken(out) and variant + 1 < len(SRC_VARIANTS):
        variant += 1
        letter, description = SRC_VARIANTS[variant][0], SRC_VARIANTS[variant][1]
        log("")
        log("  [!!] Всё ещё error.NonExistentClass.")
        log("  [i] Пробую вариант %s подключения исходников: %s" % (letter, description))
        if not apply_source_variant(variant):
            break
        stop_gradle_daemons(wrapper, env)
        clean_build_outputs()
        code, out = run_gradle_once(java_home, sdk, wrapper, env)

    if code != 0:
        log("")
        log("  Полный лог: %s" % LOG_FILE)
        raw = first_error_lines()
        if raw:
            log("")
            log("  Ошибки компилятора как есть (первые %d):" % len(raw))
            for s in raw:
                log("      %s" % s)
        # Самое важное: показываем настоящие ошибки исходников, а не следствия в KSP.
        try:
            print_config_diag(wrapper, env)
            errors = compile_without_ksp(java_home, sdk, wrapper, env)
            if errors:
                log("  Скопируйте блок '>>> НАСТОЯЩИХ ОШИБОК KOTLIN' выше и пришлите разработчику.")
        except Exception as diag_error:
            log("  (диагностику выполнить не удалось: %s)" % diag_error)
        raise RuntimeError("Gradle упал с кодом %s — смотрите вывод выше." % code)

    if variant > 0:
        log("")
        log("  [i] Сборка прошла на варианте %s подключения исходников (%s)."
            % (SRC_VARIANTS[variant][0], SRC_VARIANTS[variant][1]))
        log("      В app/build.gradle.kts этот вариант уже прописан — менять ничего не нужно.")

    out_dir = ROOT / "app" / "build" / "outputs" / "apk" / ("release" if RELEASE else "debug")
    apks = sorted(out_dir.glob("*.apk"))
    if not apks:
        raise RuntimeError("APK не найден в %s" % out_dir)

    src = max(apks, key=lambda p: p.stat().st_size)
    final = ROOT / ("RoleDeepSeek-%s.apk" % ("release" if RELEASE else "debug"))
    shutil.copyfile(str(src), str(final))
    return final, sdk


def diagnose():
    """Показывает, откуда реально берутся исходники и какие ошибки видит компилятор."""
    step("ДИАГНОСТИКА ИСХОДНИКОВ")
    kt_files, roots, dupes, mismatched = scan_kotlin_sources()

    log("  Всего .kt файлов в проекте: %d" % len(kt_files))
    log("  Каталоги, в которых лежат Kotlin-исходники (ожидается только '%s'):"
        % EXPECTED_SRC_ROOT)
    for root in sorted(roots):
        log("    %-46s %d файл(ов)" % (root, len(roots[root])))

    if len(roots) > 1:
        log("")
        log("  [!] Исходники лежат БОЛЬШЕ ЧЕМ В ОДНОМ каталоге.")
        log("      Лишние копии ломают KSP/Hilt (отсюда и error.NonExistentClass).")

    if dupes:
        log("")
        log("  [!] ОДИН И ТОТ ЖЕ КЛАСС ОБЪЯВЛЕН В НЕСКОЛЬКИХ ФАЙЛАХ (%d):" % len(dupes))
        for k in sorted(dupes)[:20]:
            log("    %s" % k)
            for f in dupes[k][:4]:
                log("        %s" % f.relative_to(ROOT))

    if mismatched:
        log("")
        log("  [!] Файлы, чей путь не совпадает с их package:")
        for f in mismatched[:20]:
            log("    %s" % f.relative_to(ROOT))

    if not dupes and len(roots) == 1 and not mismatched:
        log("")
        log("  [ok] Со структурой исходников всё в порядке.")

    log("  Всего .kt: %d" % len(kt_files))
    check_project_completeness()

    java_home = install_java()
    sdk = install_android_sdk(java_home)
    write_local_properties(sdk)
    wrapper = ROOT / ("gradlew.bat" if IS_WINDOWS else "gradlew")
    if not IS_WINDOWS:
        os.chmod(str(wrapper), 0o755)
    env = tool_env(java_home, sdk)
    env["ANDROID_HOME"] = str(sdk)
    env["ANDROID_SDK_ROOT"] = str(sdk)
    print_config_diag(wrapper, env)
    compile_without_ksp(java_home, sdk, wrapper, env)


def install_apk(apk, sdk):
    step("ШАГ 4/4 — Установка на подключённый телефон")
    adb = Path(sdk) / "platform-tools" / ("adb.exe" if IS_WINDOWS else "adb")
    if not adb.exists():
        log("  [!!] adb не найден — пропускаю установку")
        return
    code, out = run([str(adb), "devices"], capture=True)
    if "\tdevice" not in out:
        log("  [!!] Телефон не виден по USB. Включите «Отладка по USB» и подтвердите запрос на экране.")
        return
    code, _ = run([str(adb), "install", "-r", str(apk)])
    log("  [ok] APK установлен" if code == 0 else "  [!!] adb install завершился с кодом %s" % code)


def main():
    log("")
    log("  Role DeepSeek — автоматическая сборка APK")
    log("  Проект: %s" % ROOT)
    log("  ОС: %s / %s / Python %s" % (platform.system(), platform.machine(),
                                       platform.python_version()))

    if FRESH_TOOLS and TOOLS.exists():
        log("  [--fresh-tools] Удаляю скачанные JDK/Android SDK (%s) ..." % TOOLS)
        shutil.rmtree(TOOLS, ignore_errors=True)

    if not (ROOT / "settings.gradle.kts").exists():
        log("")
        log("  ОШИБКА: рядом со скриптом нет settings.gradle.kts.")
        log("  Положите build_apk.py в КОРНЕВУЮ папку проекта (где лежит gradlew) и запустите снова.")
        sys.exit(1)

    if DIAG:
        try:
            diagnose()
        except Exception as e:
            log("")
            log("  X  ОШИБКА диагностики: %s" % e)
            sys.exit(1)
        return

    try:
        check_source_layout(auto_fix=True)
        check_gradle_markers()
        if not check_project_completeness():
            raise RuntimeError("Исходники в папке проекта неполные — сборка отменена.")
        java_home = install_java()
        sdk = install_android_sdk(java_home)
        apk, sdk = build_apk(java_home, sdk)
        if INSTALL:
            install_apk(apk, sdk)
    except Exception as e:
        log("")
        log("  X  ОШИБКА: %s" % e)
        log("     Скопируйте текст ошибки выше и пришлите разработчику.")
        sys.exit(1)

    step("ГОТОВО!")
    log("  APK: %s" % apk)
    log("  Размер: %.1f МБ" % (apk.stat().st_size / 1048576))
    log("")
    log("  Как установить на телефон:")
    log("   1. Скопируйте %s на телефон (кабель / Telegram / Drive)." % apk.name)
    log("   2. Откройте его файловым менеджером и разрешите установку из неизвестных источников.")
    log("")


if __name__ == "__main__":
    main()
