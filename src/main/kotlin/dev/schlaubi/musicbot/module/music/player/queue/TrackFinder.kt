package dev.schlaubi.musicbot.module.music.player.queue

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.interactions.editingPaginator
import com.kotlindiscord.kord.extensions.interactions.respond
import dev.kord.rest.builder.message.create.embed
import dev.schlaubi.lavakord.rest.TrackResponse
import dev.schlaubi.lavakord.rest.loadItem
import dev.schlaubi.lavakord.rest.mapToTrack
import dev.schlaubi.musicbot.module.music.player.MusicPlayer
import dev.schlaubi.musicbot.utils.MessageSender
import mu.KotlinLogging
import kotlin.time.Duration

private val LOG = KotlinLogging.logger { }

private val urlProtocol = "^https?://".toRegex()

interface QueueOptions {
    val query: String
    val force: Boolean
    val top: Boolean
    val soundcloud: Boolean
}

abstract class QueueArguments : Arguments(), QueueOptions {
    override val query by string("query", "The query to play")
    override val force by defaultingBoolean("force", "Makes this item skip the queueTracks", false)
    override val top by defaultingBoolean("top", "Adds this item to the top of the queueTracks", false)
    override val soundcloud by defaultingBoolean(
        "soundcloud",
        "Searches for this item on SoundCloud instead of YouTube",
        false
    )
}

suspend fun <T : QueueArguments> EphemeralSlashCommandContext<T>.queueTracks(
    musicPlayer: MusicPlayer,
    search: Boolean
) {
    return queueTracks(musicPlayer, search, arguments, {
        respond {
            it()
        }
    }) {
        editingPaginator {
            it()
        }
    }
}

suspend fun <T> EphemeralSlashCommandContext<T>.findTracks(
    musicPlayer: MusicPlayer,
    search: Boolean
): QueueSearchResult?
        where T : Arguments, T : QueueOptions {
    return findTracks(musicPlayer, search, arguments, {
        respond {
            it()
        }
    }) {
        editingPaginator {
            it()
        }
    }
}

internal suspend fun CommandContext.findTracks(
    musicPlayer: MusicPlayer,
    search: Boolean,
    arguments: QueueOptions,
    respond: MessageSender,
    editingPaginator: EditingPaginatorSender
): QueueSearchResult? {
    val rawQuery = arguments.query
    val isUrl = urlProtocol.find(rawQuery) != null

    val query = if (!isUrl) {
        val searchPrefix = if (arguments.soundcloud) "scsearch: " else "ytsearch:"

        searchPrefix + rawQuery
    } else rawQuery

    val result = musicPlayer.loadItem(query)

    val searchResult: QueueSearchResult = when (result.loadType) {
        TrackResponse.LoadType.TRACK_LOADED -> SingleTrack(result.track.toTrack())
        TrackResponse.LoadType.PLAYLIST_LOADED ->
            Playlist(result.getPlaylistInfo(), result.tracks.mapToTrack())
        TrackResponse.LoadType.SEARCH_RESULT -> {
            if (search) {
                searchSong(respond, editingPaginator, getUser()!!, musicPlayer, result) ?: return null
            } else {
                val foundTrack = result.tracks.first()
                SingleTrack(foundTrack.toTrack())
            }
        }
        TrackResponse.LoadType.NO_MATCHES -> {
            noMatches(respond)
            return null
        }
        TrackResponse.LoadType.LOAD_FAILED -> {
            handleError(respond, result)
            return null
        }
    }

    return searchResult
}

suspend fun CommandContext.queueTracks(
    musicPlayer: MusicPlayer,
    search: Boolean,
    arguments: QueueOptions,
    respond: MessageSender,
    editingPaginator: EditingPaginatorSender
) {
    val searchResult = findTracks(musicPlayer, search, arguments, respond, editingPaginator) ?: return

    val title = if (musicPlayer.nextSongIsFirst) translate("music.queue.now_playing", "music") else translate(
        "music.queue.queued",
        "music",
        arrayOf(with(searchResult) { type() })
    )

    respond {
        embed {
            this.title = title
            with(searchResult) { addInfo(musicPlayer.link, this@queueTracks) }

            footer {
                val estimatedIn = musicPlayer.remainingQueueDuration
                val item = if (estimatedIn == Duration.milliseconds(0)) {
                    translate("music.general.now")
                } else {
                    musicPlayer.remainingQueueDuration.toString()
                }
                text = translate(
                    "music.plays_in.estimated",
                    "music",
                    arrayOf(item)
                )
            }
        }

        musicPlayer.queueTrack(arguments.force, arguments.top, searchResult.tracks)
    }
}

private suspend fun CommandContext.noMatches(respond: MessageSender) {
    respond {
        content = translate("music.queue.no_matches")
    }
}

private suspend fun CommandContext.handleError(
    respond: MessageSender,
    result: TrackResponse
) {
    val error = result.getException()
    when (error.severity) {
        TrackResponse.Error.Severity.COMMON -> {
            respond {
                content = translate("music.queue.load_failed.common", error.message)
            }
        }
        else -> {
            LOG.error(FriendlyException(error.severity, error.message)) { "An error occurred whilst queueing a song" }

            respond {
                content = translate("music.queue.load_failed.uncommon")
            }
        }
    }
}
