package me.ash.reader.ui.page.home.feeds.subscribe

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.ui.ext.formatUrl
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class SubscribeViewModel @Inject constructor(
    private val opmlService: OpmlService,
    val rssService: RssService,
    private val rssHelper: RssHelper,
    private val androidStringsHelper: AndroidStringsHelper,
) : ViewModel() {

    private val _subscribeUiState = MutableStateFlow(SubscribeUiState())
    val subscribeUiState: StateFlow<SubscribeUiState> = _subscribeUiState.asStateFlow()
    private var searchJob: Job? = null

    fun init() {
        _subscribeUiState.update {
            it.copy(
                title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),
            )
        }
    }

    fun reset() {
        searchJob?.cancel()
        searchJob = null
        _subscribeUiState.update {
            SubscribeUiState().copy(title = androidStringsHelper.getString(R.string.subscribe))
        }
    }

    fun importFromInputStream(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                opmlService.saveToDatabase(inputStream)
                rssService.get().doSync()
            } catch (e: Exception) {
                Log.e("FeedsViewModel", "importFromInputStream: ", e)
            }
        }
    }

    fun selectedGroup(groupId: String) {
        _subscribeUiState.update { it.copy(selectedGroupId = groupId) }
    }

    fun addNewGroup() {
        if (_subscribeUiState.value.newGroupContent.isNotBlank()) {
            viewModelScope.launch {
                selectedGroup(rssService.get().addGroup(_subscribeUiState.value.newGroupContent))
                hideNewGroupDialog()
                _subscribeUiState.update { it.copy(newGroupContent = "") }
            }
        }
    }

    fun changeParseFullContentPreset() {
        _subscribeUiState.update {
            it.copy(parseFullContentPreset = !_subscribeUiState.value.parseFullContentPreset)
        }
    }

    fun changeAllowNotificationPreset() {
        _subscribeUiState.update {
            it.copy(allowNotificationPreset = !_subscribeUiState.value.allowNotificationPreset)
        }
    }

    fun search() {
        searchJob?.cancel()
        viewModelScope.launch {
            try {
                _subscribeUiState.update {
                    it.copy(
                        errorMessage = "",
                    )
                }
                _subscribeUiState.value.linkContent.formatUrl().let { str ->
                    if (str != _subscribeUiState.value.linkContent) {
                        _subscribeUiState.update {
                            it.copy(
                                linkContent = str
                            )
                        }
                    }
                }
                _subscribeUiState.update {
                    it.copy(
                        title = androidStringsHelper.getString(R.string.searching),
                        lockLinkInput = true,
                    )
                }
                if (rssService.get().isFeedExist(_subscribeUiState.value.linkContent)) {
                    _subscribeUiState.update {
                        it.copy(
                            title = androidStringsHelper.getString(R.string.subscribe),
                            errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                            lockLinkInput = false,
                        )
                    }
                    return@launch
                }
                val feedWithArticle = rssHelper.searchFeed(_subscribeUiState.value.linkContent)
                _subscribeUiState.update {
                    it.copy(
                        feed = feedWithArticle.feed,
                        articles = feedWithArticle.articles,
                    )
                }
                switchPage(false)
            } catch (e: Exception) {
                e.printStackTrace()
                _subscribeUiState.update {
                    it.copy(
                        title = androidStringsHelper.getString(R.string.subscribe),
                        errorMessage = e.message ?: androidStringsHelper.getString(R.string.unknown),
                        lockLinkInput = false,
                    )
                }
            }
        }.also {
            searchJob = it
        }
    }

    fun subscribe() {
        val feed = _subscribeUiState.value.feed ?: return
        val articles = _subscribeUiState.value.articles
        viewModelScope.launch {
            rssService.get().subscribe(
                feed.copy(
                    groupId = _subscribeUiState.value.selectedGroupId,
                    isNotification = _subscribeUiState.value.allowNotificationPreset,
                    isFullContent = _subscribeUiState.value.parseFullContentPreset,
                ), articles
            )
            hideDrawer()
        }
    }

    fun inputLink(content: String) {
        _subscribeUiState.update {
            it.copy(
                linkContent = content,
                errorMessage = "",
            )
        }
    }

    fun inputNewGroup(content: String) {
        _subscribeUiState.update { it.copy(newGroupContent = content) }
    }

    fun showDrawer() {
        _subscribeUiState.update { it.copy(visible = true) }
    }

    fun hideDrawer() {
        _subscribeUiState.update { it.copy(visible = false) }
    }

    fun showNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = true) }
    }

    fun hideNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = false) }
    }

    fun switchPage(isSearchPage: Boolean) {
        _subscribeUiState.update { it.copy(isSearchPage = isSearchPage) }
    }

    fun showRenameDialog() {
        _subscribeUiState.update {
            it.copy(
                renameDialogVisible = true,
                newName = _subscribeUiState.value.feed?.name ?: "",
            )
        }
    }

    fun hideRenameDialog() {
        _subscribeUiState.update {
            it.copy(
                renameDialogVisible = false,
                newName = "",
            )
        }
    }

    fun inputNewName(content: String) {
        _subscribeUiState.update { it.copy(newName = content) }
    }

    fun renameFeed() {
        _subscribeUiState.value.feed?.let {
            _subscribeUiState.update {
                it.copy(
                    feed = it.feed?.copy(name = _subscribeUiState.value.newName),
                )
            }
        }
    }
}

data class SubscribeUiState(
    val visible: Boolean = false,
    val title: String = "",
    val errorMessage: String = "",
    val linkContent: String = "",
    val lockLinkInput: Boolean = false,
    val feed: Feed? = null,
    val articles: List<Article> = emptyList(),
    val allowNotificationPreset: Boolean = false,
    val parseFullContentPreset: Boolean = false,
    val selectedGroupId: String = "",
    val newGroupDialogVisible: Boolean = false,
    val newGroupContent: String = "",
    val groups: Flow<List<Group>> = emptyFlow(),
    val isSearchPage: Boolean = true,
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
)
