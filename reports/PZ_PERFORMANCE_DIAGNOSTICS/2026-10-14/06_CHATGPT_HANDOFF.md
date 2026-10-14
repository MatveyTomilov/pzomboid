# Передача диагностики ChatGPT

Локальный read-only аудит выполнен 14 октября 2026. Изменения, тестовые запуски, обновления, отключения и исправления не выполнялись. Основной мир не открывался. Владелец требует сохранить Project Viewpoint и камеры первого/третьего лица. Приоритет — сильные лаги пассажира, когда друг ведёт автомобиль.

## Среда

Windows 11 Pro 10.0.26300; Ryzen 7 7700 (8C/16T), 32GB RAM; RTX5070 с 12GB VRAM, NVIDIA driver610.88, есть AMD iGPU. Игра на NVMe KINGSTON SNV2S1000G: E:\SteamLibrary\steamapps\common\ProjectZomboid, свободно169GiB. Pagefile20GiB на E:, не автоматический, usage0 после сеанса. Ethernet1Gbps; Windows Balanced. В момент аудита игра/Host выключены, поэтому CPU/GPU/RAM snapshot не является записью лагов.

PZ42.21.0 revision4a0e9546ec, pzbullet1.0.0.28, Steam Windows build25485521. Точное название Steam beta branch не подтверждено; BetaKey не найден в проверенных текстовых конфигурациях. JRE Zulu25.0.1+8. Steam LaunchOptions: `-agentlib:zbNative --`. Используемые логи/configs находятся в C:\Users\<USER>\Zomboid. Другой cachedir не обнаружен, но активного процесса нет для проверки command line.

Host встроенный, профиль pzhardcore, UI memory7168MiB. Фактическая command line серверной JVM в последнем запуске не записана. Клиент Xmx8192m, Windows Shenandoah profile, `-Dpzopt.jit=steady`, `-XX:PerMethodTrapLimit=0`, `-XX:PerBytecodeTrapLimit=0`, `-Dpzopt.heap=8g,none,0`. Classpath "." перед JAR. Host MaxPlayers6, PingLimit0, PauseEmpty=true, SaveWorldEveryMinutes0, ports18261/18262, SteamVAC=true. Не отключать целостность/безопасность.

## Моды и параметры

Host235ModID/211WorkshopID; default клиента145ModID. Все активные Host ModID имеют найденные mod.info;38 имеют найденные JAR. Полная таблица в02_MOD_AUDIT.md, источники/зависимости/версии/дубликаты/JAR SHA256 в evidence/active-mods.json. Client default не равен runtime MP набору: разница списков сама не доказывает конфликт.

Viewpoint0.1.6/3809306528; ZombieBuddy/3619862853 — предыдущая loader версия2.3.4, mod.info её не подтверждает. QOL3811256204 версия не маркирована; Precision0.3.0/3814074009; True Ballistics1.4.6/3812864848; FPSPlus1.0.1beta/3814046234. A-Life core1.4.1/3803984183+RU/3806181882 активны; Jeem, Bandits, YANMC отсутствуют в текущем Host Mods. Не опираться на старую историю подключений.

NPC settings: RatePercent75, HostileCeiling32, PopulateMax45, PersistentSquadCap20, OfflineTick20s, HibernateTiles150, Encounter60min, OutpostsBudget250. Zombies Population1.6, Start1, Peak1.8 day28, Respawn0, Redistribute12h, FollowSound100, RallyGroup20. Фактическое количество NPC/зомби при лаге неизвестно.

Графика2560×1440/144Hz, VSyncfalse, runtime gamecap144/menu60; textureCompressionfalse, vehicle texture enum4, mipmapsfalse, blood10. Viewpoint Vanilla mode с trueweathers pack; modelpacks включены, farBlocks24, packRing4, fullRateCharacters16, relief false, treeShare.25; chunk workers4/far workers4. CameraRight/autoswapfalse/headMovement.66/shake1/vehicleBehindtrue. Retained thirdPersonVehicleFollowtrue/slide1/swing.5/feel.5 могут быть legacy keys без действующего обработчика.

PZ_Optimization3805285544 установлен как1126 loose files, commit4be8d4ea, revision совпадает с JAR. Работает в клиенте и Host. Server runtime: `[pzopt] loaded override zombie.iso.IsoWorld … active`; `vehicleSmooth=interp`, `driveLookSmooth=true`, `physicsDefer=true`, `entityUpdateParallel=true`, `animatorParallel=true`, `frameThreads=8`, `charDrawThreads=14`, `luaProfile=false`, `instrument=false`, `hotsaveIntervalSec=30`. Автор не гарантирует mods/MP. Наличие pzopt в стеке не доказывает его вину.

## Доказательства последней сессии

Сессия13.10 около22:27 →14.10 00:46. Клиент DebugLog после ротации начинается23:54; console сохраняет более позднюю часть. logs.zip содержит и старые запуски, и сейвы. Сейвы не включены в этот ZIP. Старые hs_err9–10 октября отдельно, не приписывать текущему сеансу.

- 00:16:31.221: `[ALIFE-PERF] tick=174ms slowest=population:174ms`.
- 00:42:37.743: `[ALIFE-PERF] tick=176ms slowest=population:176ms`. Подтверждённая дорогая задача Host.
- 00:17:26.833: Viewpoint main203.4ms, own2.5/rest200.9; render211.8ms, own3.9. Большая часть паузы вне измеряемых passes Viewpoint; GC/game update/другие патчи не разделены.
- 00:24:32.415–36.983:175 `SpriteRenderer$RingBuffer.next > Buffer overrun`.
- 6019 предупреждений read-only `bdoshove`; конкретный setter и стоимость неизвестны.
- 24 NetTimedAction NPE: protectedCallBoolean возвращаетnull. Один конкретный action ISApplyMakeUp.lua28/getMakeUpType null. Остальные actions не определены.
- More Car Features3520758551 дважды: OnSpawnVehicleAdditionalFeatures.lua789 → `vehicle:scriptReloaded(true)` → native `Bullet.removeVehicle(int)` UnsatisfiedLinkError. Сбой замены спавнящейся машины; не доказана связь с пассажирской камерой.
- 164 inconsistent packets:163PlayerHitSquare,1ZombieStagger. Это не доказательство задержки vehicle packets.
- 858 missing AnimSets/actiongroups scan exceptions только f0 при старте;5 ladderW property errors. Не считать их858 игровыми зависаниями.
- Client7 container already has id,3 invalid corpse index; потеря предметов не доказана.
- Нет OOM в сохранившихся фрагментах. Host штатно завершился, save738.1215ms на выходе. Новых связанных WER событий за два дня не найдено.
- Сохранены144 slow-frame сообщения. Средние PZOpt93.1/79.0/60FPS за разные окна; нет p99/1%low и привязки к роли/маршруту.

## Выводы и план

Дополнительное подтверждённое расхождение: локальный Zomboid/mods содержит Viewpoint 0.1.5a-hotfix. Его JAR и локальные QOL/True Ballistics отличаются SHA-256 от Steam/Host; Steam и Host совпадают. ZombieBuddy/Precision совпадают во всех копиях. Старые копии могли быть намеренно сохранены для восстановления. Их фактическое участие в последнем клиентском runtime не доказано; архивный console/log_5.txt показывает Workshop JAR в classpath, но не даёт надёжной датировки каждой строки. При следующем разрешённом запуске сохранить список jarPath ZombieBuddy; ничего не удалять автоматически.

Три приоритетные области: (1) измеренные A-Life population hitches и возможная конкуренция клиент/Host; (2) render/animation ошибки и потенциальное Java-пересечение PZOpt/Viewpoint/QOL; (3) camera/vehicle interpolation и streaming при езде — пассажирская гипотеза пока не доказана.

Нужен минимальный3-минутный тест на КОПИИ мира: стоять15s/пешком15s; Hostdriver20s/Hostpassenger20s; passenger first20s/third20s/iso20s; knownroute20s/newroute20s. Одна машина, скорость и погода. Друг сохраняет собственный лог. Отмечать время, FPS, прыгает камера или весь мир/машина, CPU по logicalcores, RAMcommit, dedicated/shared VRAM, диск/hardfaults, ping только если реально доступен. Не придумыватьTPS/percentiles.

После отдельного одобрения обосновано A/B PZOpt Stock preset и при необходимости обратимый откат его файлов, сохраняя Viewpoint. Stock preset не равен отсутствию overrides. Камерный bob/follow/shake, меньшее LOD, Vanilla shader pack и cap60/90 — обратимые варианты, не доказанные исправления. A-Life сохранить, сначала профиль/распределение population job. Не менять число NPC, зомби, loot или баланс без решения владельца.

JFR/GC/per-handler profiling требуют нового разрешения. Не менять drivers/pagefile/сеть/heap без замеров. Не объявлять утечку только по лагам и не исключать её только из-за отсутствия OOM.

Шесть документов и evidence дают все локальные сведения. Полные configs обезличены; пароли, токены, IP, SteamID и имена игроков скрыты. Состав модов/версии сохранены. Основные файлы игры, модов и мира не изменялись.
