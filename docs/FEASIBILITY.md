# PZ BlackBox feasibility — initial local audit

Scope: local B42.21.0, bundled Zulu 25.0.1, installed ZombieBuddy 2.3.4. This matrix is initial and must be updated after implementation and actual tests. PROTOTYPED below initially means an identified observation point awaiting implementation and verification, not a completed collector.

| Measurement | Observation point / meaning | Initial status / limitation |
|---|---|---|
| Frame work / interval | private static GameWindow.frameStep(); private logic(); renderInternal(). frameStep excludes the pacing sleep in the outer run loop. Interval between starts includes pacing. Rendering is GameThread CPU submission, not GPU completion. | PROTOTYPED; inspected local bytecode/decompilation, no game run |
| Lua boundary calls | se.krka.kahlua.integration.LuaCaller.pcall/pcallvoid/pcallBoolean/protectedCallVoid/protectedCallBoolean | PROTOTYPED; arrays wrappers delegate to other hooked overloads. Avoid duplicate wrapper calls while preserve distinct nested closures. No coverage of internal Lua-to-Lua calls |
| Lua event context | zombie.Lua.Event.trigger(KahluaTable,LuaCaller,Object[]) and public name | PROTOTYPED; scoped ThreadLocal event context, no callback replacement |
| Lua closure source | LuaClosure.prototype.filename/file/name/lines | PROTOTYPED; inspect same source as ZBLuaPerfMon, cache bounded identities |
| Java sampling / GC / contention | bundled jdk.jfr present; Recording and RecordingFile | PROTOTYPED; no global flags, bounded low-rate recording |
| Error reporting | DebugLogStream.printException(... Throwable first ...) plus bounded capture of failed Lua protected calls | PROTOTYPED; nested overloads must count once; no global PrintStream replacement |
| Loaded zombies | IsoCell.getZombieList and per-zombie update timing | PROTOTYPED; periodic game-thread counts and bounded traversal, not total world population |
| Chunk reading/preparation | WorldStreamer.DoChunkAlways(IsoChunk,ByteBuffer); IsoChunk.LoadChunk(int,int,ByteBuffer), loadInWorldStreamerThread(), doLoadGridsquare() | PROTOTYPED; request counts not unique chunks, stages can nest |
| Asset lifecycle | AssetManager.load(AssetPath[,params]), onLoadingSucceeded/onLoadingFailed | PROTOTYPED; request overhead is not async completion latency; texture lookups are not GPU uploads |
| Texture lookup | Texture.getSharedTextureInternal(String,int) | PROTOTYPED; no reliable cache distinction until verified |
| Registry | ZomboidFileSystem.instance.getModIDs/getModDir/getModInfoForDir; Loader.getActiveJavaMods | PROTOTYPED; loaded Java list separate from installed source directories |
| Context | game-thread player/vehicle role; GameClient.client/GameServer.server; CoopMaster.isRunning | PROTOTYPED; camera mode UNKNOWN without mod integration |
| GPU / VRAM | No safe universal Java measurement identified | UNSUPPORTED; retain null and reason |
| Host deep recording | Separate server JVM requires actual ZombieBuddy agent load there | BLOCKED if agent absent; client presence proves nothing about server |
| NPC timings | Generic Lua/Java boundary capture only | UNSUPPORTED for direct per-NPC integration; no mod-specific API |

## Sources inspected

- https://github.com/zed-0xff/ZombieBuddy tag v2.3.4 (9a08a542cfb55f7c8f4973a545639430b3c601ee). Current upstream master has moved to 3.0.0-beta2 and its annotations API is different; do not compile against master for the local install.
- https://github.com/zed-0xff/ZBLuaPerfMon (current source demonstrates Lua profiling but currently imports the 3.x annotation package).
- https://projectzomboid.com/modding/ (supplement only; installed game bytecode is authoritative for B42).
- Local game projectzomboid.jar and ZombieBuddy.jar; analysis outside repository at E:/PZ-BlackBox-Lab/api/src. Do not redistribute those classes/decompiled sources.

## ZombieBuddy observations

2.3.4 uses me.zed_0xff.zombie_buddy.Patch (not .annotations.Patch); @Patch.Local creates per-call advice locals; @Patch.OnExit supports onThrowable/suppress; @Exposer.LuaClass exposes normal APIs. Main.main(String[]) is called through regular mod loading. Generic media/java/ JAR location can apply to client and server only when the agent is actually present. Watches/EventLog/PacketLog are experimental and must not be required. Events/getClosureInfo can be used when available but closure metadata can be read without enabling experimental features.

## Audit-stage checks actually executed

- Local Core static initialization inspected: `new GameVersion(42, 21, "")`; no game launched.
- Installed ZombieBuddy manifest Implementation-Version=2.3.4; compile API is root `me.zed_0xff.zombie_buddy.Patch`.
- Programmatic JFR probe compiled using existing ECJ3.43 at source/target21, executed in bundled Zulu25.0.1: custom-event count1, execution samples13, JFR120204bytes. Its custom duration/timestamp assertions passed. This verifies the isolated JFR mechanism only, not a BlackBox collector or in-game profiling.
- ManagementFactory heap/thread queries executed in isolated probe: heapused22110640bytes, threads9. These are the probe JVM values, NOT game measurements.
- Gradle9.1.0 launched under bundled Java25; distribution SHA-256 matched official `a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806`.
- Existing ECJ3.43 supports source/target through24, not25; source/target21 is viable on Java25. A full JDK download was attempted but network download was not confirmed complete.
- Structured exact descriptors extracted from installed game without class initialization: [API signatures](evidence/b42-21-api-signatures.json).
- Built-in CoopMaster.launchServer inspected: separate child JVM gets heap/classpath/home/cache arguments; it does not forward the client javaagent option. No Host process was launched.

## Implementation gate

Cursor CLI was invoked with the required `cursor-grok-4.6-xhigh` model and correct branch/worktree. It exited1 before implementation: `ActionRequiredError: ... You're out of usage`. An owner decision is pending for the AGENTS.md Codex-implementation exception plus independent review. No implementation or game verification is claimed at this stage.
