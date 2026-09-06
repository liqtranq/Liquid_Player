package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.paging.PagingData
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistVisibilityTest {
    @Test
    fun `toggling the filter updates the active artists pager without a rescan`() = runTest {
        val enabled = MutableStateFlow(true)
        val minimumRequests = Channel<Int>(Channel.UNLIMITED)
        val preferences = mockk<UserPreferencesRepository>()
        val repository = mockk<MusicRepository>()
        every { preferences.hideArtistsWithFewTracksFlow } returns enabled
        every { preferences.hideLocalMediaFlow } returns flowOf(false)
        every { preferences.minTracksPerAlbumFlow } returns flowOf(1)
        every { repository.getGenres() } returns flowOf(emptyList())
        every { repository.getPaginatedArtists(any(), any(), any()) } answers {
            minimumRequests.trySend(thirdArg())
            flowOf(PagingData.empty())
        }
        val holder = LibraryStateHolder(repository, preferences)
        backgroundScope.launch { holder.artistsPagingFlow.collect() }

        assertEquals(5, minimumRequests.receive())
        enabled.value = false
        assertEquals(1, minimumRequests.receive())
        enabled.value = true
        assertEquals(5, minimumRequests.receive())
    }
}
