local mp = require "mp"

local enabled_property = "user-data/mpvnova/shield-mpeg2-fallback"

local function has_mpeg2_video_track()
    local tracks = mp.get_property_native("track-list", {})
    for _, track in ipairs(tracks) do
        if track.type == "video" and track.codec == "mpeg2video" and not track.image then
            return true
        end
    end
    return false
end

mp.add_hook("on_preloaded", 50, function()
    if not mp.get_property_bool(enabled_property, false) or not has_mpeg2_video_track() then
        return
    end

    mp.msg.info("MPEG2 software fallback: selecting G-NEXT SW before decoder creation")
    mp.set_property("file-local-options/vo", "gpu-next")
    mp.set_property("file-local-options/hwdec", "no")
end)
