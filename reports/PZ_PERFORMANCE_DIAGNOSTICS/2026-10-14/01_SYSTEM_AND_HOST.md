# Компьютер, игра и Host — 14 октября 2026

Аудит выполнялся локально на Windows; все команды были чтением. Игра и Host на момент сбора выключены. Текущие показатели ОС/GPU являются состоянием после сессии, а не замерами во время лагов.

## Компьютер

- CPU: AMD Ryzen 7 7700, 8 физических ядер / 16 логических потоков, MaxClockSpeed WMI 3801 MHz (не измеренная частота под нагрузкой).
- RAM: 32 ГБ установленной, доступно ОС 31,12 GiB; свободно при сборе примерно 22,4 GiB. Динамика во время игры неизвестна.
- GPU: NVIDIA GeForce RTX 5070, драйвер 610.88 / Windows 32.0.16.1088, дата драйвера 22.07.2026. NVIDIA-SMI: 12227 MiB VRAM, использовано около 821 MiB, нагрузка 1%, мощность 23 W в простое. WMI AdapterRAM не использован: он неверно показывает около 4 ГБ для данной карты.
- Есть встроенная AMD Radeon Graphics; какой GPU реально использовал клиент в последнем сеансе, свежий сохранённый фрагмент старта не подтверждает. Основной монитор NVIDIA: 2560×1440, 144 Гц; второй вывод AMD: 1920×1080, WMI 99 Гц.
- Игра и файл подкачки на E:, NVMe KINGSTON SNV2S1000G; свободно около 169 GiB. C: NVMe ADATA; свободно около 106 GiB. Другие диски HDD/USB перечислены в system.json.
- Файл подкачки E:\pagefile.sys: 20480 MiB, автоматическое управление выключено, текущая/пиковая занятость WMI после сеанса 0. Это не исключает прошлые пики или paging файлов.
- Ethernet Realtek 2.5GbE, согласованная скорость 1 Gbit/s. Это LAN link speed, не скорость Интернета и не ping друга.
- Электропитание: Windows «Сбалансированная». Замеров частот/температур/троттлинга во время игры нет.

## Игра и запуск

- Steam App 108600, установка E:\SteamLibrary\steamapps\common\ProjectZomboid.
- Реальная версия: 42.21.0, Git revision 4a0e9546ec, pzbullet 1.0.0.28 (version.txt и серверный лог).
- Steam buildid 25485521, установлен Windows depot 108604. BetaKey в проверенных текстовых конфигурациях не найден; название ветки по одному appmanifest достоверно не устанавливается. Пользователь может прочитать Steam → Свойства → Бета-версии без изменения выбора.
- JRE: Zulu OpenJDK 25.0.1+8 LTS, 64-bit. `java -version` выполнялся отдельно, игру не запускал.
- Steam LaunchOptions для игры: `-agentlib:zbNative --` — native loader ZombieBuddy.
- Клиент classpath: `.` перед `projectzomboid.jar`; поэтому loose classes PZ_Optimization заменяют классы из JAR.
- Клиент vmArgs: полный список в evidence/client-launcher.json. Xmx8192m; Windows профиль содержит UseShenandoahGC. Также `-Dpzopt.jit=steady`, `-XX:PerMethodTrapLimit=0`, `-XX:PerBytecodeTrapLimit=0`, `-Dpzopt.heap=8g,none,0`. Точный итоговый collector работающей JVM не измерен. Это текущий launcher, а не доказательство, что значения были одинаковы всю сессию.
- PZ_Optimization: commit 4be8d4ea / build 13.10.2026, revision 4a0e9546ec. В Host лог показывает активные overrides и gcMode=g1; gcMode из настроек плагина не равен доказательству параметров самой JVM.
- Выбран Host pzhardcore; UI memory=7168 MiB (7 GiB) в Lua/host.ini. Серверная JVM уже завершилась, её фактическую командную строку/Xmx/collector реконструировать однозначно нельзя. 7 GiB — настройка запуска, не замер использования.
- Потенциально клиент и Host разрешают около 15 GiB heap суммарно, плюс native/GPU/модели/JIT/ОС. Память heap и VRAM не взаимозаменяемы. Увеличение heap без замеров не предлагается.

## Host и Sandbox

Полные обезличенные настройки: evidence/pzhardcore.ini, SandboxVars.lua. Текущие списки: 235 Mod ID / 211 Workshop ID, default клиента 145 Mod ID. `host.ini` подтверждает активный профиль. Сейв не открывался.

- MaxPlayers=6, фактически обычно 2–4; в журнале встречаются сообщения о 3 игроках.
- DefaultPort=18261, UDPPort=18262; PingLimit=0 (не ограничивает ping, не снижает задержку); SteamVAC=true. Проверки безопасности не отключать.
- PauseEmpty=true, SaveWorldEveryMinutes=0. Это отсутствие периодического сохранения по данному параметру; сохранение при выходе было успешным. PZ_Optimization дополнительно логирует hotsaveIntervalSec=30, saveCellAsync=true — требуется учитывать этот отдельный механизм.
- ClientActionLogs=ISEnterVehicle;ISExitVehicle;ISTakeEngineParts; фильтр client commands для vehicle записан в ini; MapRemotePlayerVisibility=3. Эти параметры не являются измерениями bandwidth/TPS.
- Zombies=2; PopulationMultiplier=1.6, Start=1.0, Peak=1.8, PeakDay=28. Произведение 1.6×1.8=2.88 относится к масштабу настройки пика; реальное число видимых/активных зомби не измерено.
- RespawnHours/UnseenHours/Multiplier=0; RedistributeHours=12; FollowSoundDistance=100; RallyGroupSize=20, variance=50, travel=20, separation=15, radius=3.
- A-Life: RatePercent=75, HostileCeiling=32, PopulateMax=45, PersistentSquadCap=20, Offline_TickSeconds=20, HibernateTiles=150, Encounters_IntervalMinutes=60, Outposts_BuildBudget=250. Это настройки, не фактическое число NPC.
- No Vanilla Vehicles=true, Gamesta spawnRate=-1. NoVanillaVehicles связан с подтверждённым вызовом замены машины/перезагрузки script в More Car Features; не отключать параметр без отдельного решения владельца.

## Графика и Viewpoint

2560×1440, оконный режим, VSync=false; uiRenderFPS=60. Текущее ограничение игровых кадров, судя по логам, 144 FPS, menu=60. Retained PZOpt averages: 93.1 / 79.0 / 60.0 FPS за разные окна — не сравнение водителя/пассажира и не 1% low.

options.ini: bloodDecals=10, corpseShadows=true, textureCompression=false, texture2x=true, modelTextureMipmaps=false, simpleClothingTextures=1, maxVehicleTextureSize=4 (индекс режима, не 4 пикселя). Значение maxTextureSize=1 тоже enum; не переводить его в разрешение без игрового UI.

Viewpoint: mode=Vanilla, shader pack=trueweathers, preset=Custom; модельные паки включены. LOD farBlocks=24, packRing=4, fullRateCharacters=16, reliefEnabled=false, treeShare=.25, treesGoneBlocks=24, unawareZombiesThinned=true. Chunks cookThreads=4, buildBudgetMs=3, maxMs=9; far workers=4, budgetMs=1. Эти worker pools могут конкурировать с Host/оптимизатором; нельзя складывать их как число постоянно занятых CPU.

Камера: first/third включены; camera.autoSwap=false, shoulder=Right; headMovement=.66, shake=1.0, vehicleBehind=true; thirdPersonCamera.vehicleFollow=true, vehicleSlide=1.0, vehicleSwing=.5, vehicleFeel=.5. Настройки старого дополнения могут сохраняться в файле, даже если их обработчик не загружен. Их наличие не доказывает применение.

Лимиты Viewpoint памяти — потолки разных пулов, часть native/GPU/неактивные режимы. Не суммировать их как реальную RAM: memory.nearMeshMiB=512, shell=768, pack=768, floor=512, cpuCache=320, corpse=256; minecraft=4096 не означает 4 GiB при выключенном Minecraft shaderpack.
