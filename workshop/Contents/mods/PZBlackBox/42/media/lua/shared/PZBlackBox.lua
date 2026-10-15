-- No global key binding, no Mod Options dependency, no game settings changes.
local started = false
local warned = false
local function boot()
    if PZBlackBox and PZBlackBox.start then
        if not started then started = PZBlackBox.start() == true end
    elseif not warned then
        warned = true
        print("[PZBlackBox] Java recorder unavailable in this JVM. Enable/approve the BlackBox JAR in ZombieBuddy. Client approval does not instrument the separate Host process.")
    end
end
local function tick()
    if PZBlackBox and PZBlackBox.tick then PZBlackBox.tick() end
end
Events.OnGameBoot.Add(boot)
Events.OnGameStart.Add(boot)
Events.OnTick.Add(tick)
