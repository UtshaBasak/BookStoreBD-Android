package io.github.utshabasak.bookstorebd.feature.catalogue

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.Book
import io.github.utshabasak.bookstorebd.core.model.CatalogueQuery
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogueScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a search that finds nothing offers to ask for the book on the Wanted board`() {
        var asked: String? = null
        val done = LoadState.NotLoading(endOfPaginationReached = true)
        val nothing = flowOf(PagingData.empty<Book>(LoadStates(refresh = done, prepend = done, append = done)))
        compose.setContent {
            BookStoreTheme {
                CatalogueScreen(
                    query = CatalogueQuery(search = "Aparajito"),
                    searchText = "Aparajito",
                    suggestions = null,
                    recentSearches = emptyList(),
                    popularSearches = emptyList(),
                    categoryGroups = emptyList(),
                    wishlist = emptySet(),
                    results = nothing.collectAsLazyPagingItems(),
                    actions =
                        CatalogueActions(
                            onSearchTextChange = {},
                            onSearch = {},
                            onAuthor = {},
                            onCategory = {},
                            onSeller = {},
                            onOpenBook = {},
                            onForgetSearch = {},
                            onApplyFilters = {},
                            onClearFilters = {},
                            onSort = {},
                            onToggleWishlist = {},
                            onAskWanted = { asked = it },
                        ),
                )
            }
        }

        // The empty page arrives a moment after the first frame, as from the network.
        compose.waitUntil { compose.onAllNodesWithText(ASK).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(ASK).performClick()
        assertThat(asked).isEqualTo("Aparajito")
    }
}

private const val ASK = "Ask for it on the Wanted board"
