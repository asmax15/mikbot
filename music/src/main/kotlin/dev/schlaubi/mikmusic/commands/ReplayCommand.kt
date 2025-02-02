package dev.schlaubi.mikmusic.commands

import com.kotlindiscord.kord.extensions.types.respond
import dev.schlaubi.mikmusic.core.MusicModule

suspend fun MusicModule.replayCommand() = ephemeralControlSlashCommand {
    name = "replay"
    description = "Replays the current song"

    action {
        player.seekTo(0)

        respond {
            content = translate("commands.replay.success")
        }
    }
}
