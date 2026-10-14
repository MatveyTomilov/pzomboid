-- Original private addon. No KI5 code or assets are redistributed.
-- Apply on both client and server; use the same addon on all players.
local TARGET = "Base.97bushAmbulance"
local CAPACITY = 300
local ticks = 29

local function apply(vehicle)
    local script = vehicle:getScript()
    if not script or script:getFullName() ~= TARGET then return end
    for i = 0, vehicle:getPartCount() - 1 do
        local part = vehicle:getPartByIndex(i)
        local container = part:getItemContainer()
        if container then
            -- B42 divides occupied-seat capacity by four before applying
            -- its vehicle-container limit. Compensate only for this vehicle.
            local wanted = CAPACITY
            if container:isOccupiedVehicleSeat() then wanted = CAPACITY * 4 end
            if container:getCapacity() ~= CAPACITY then
                part:setContainerCapacity(wanted)
            end
        end
    end
end

local function scan()
    local cell = getCell()
    if not cell then return end
    local vehicles = cell:getVehicles()
    if not vehicles then return end
    local iterator = vehicles:iterator()
    while iterator:hasNext() do
        apply(iterator:next())
    end
end

local function tick()
    ticks = ticks + 1
    if ticks < 30 then return end
    ticks = 0
    scan()
end

Events.OnGameStart.Add(scan)
Events.OnTick.Add(tick)