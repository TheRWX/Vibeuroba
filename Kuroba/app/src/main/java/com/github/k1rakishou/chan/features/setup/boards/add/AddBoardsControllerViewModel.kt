package com.github.k1rakishou.chan.features.setup.boards.add

import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.chan.core.base.viewmodel.KurobaViewModel
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.concurrency.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.loader.ClientException
import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.chan.utils.InputWithQuerySorter
import com.github.k1rakishou.chan.utils.requireParams
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

class AddBoardsControllerViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val siteManager: SiteManager,
  private val boardManager: BoardManager,
) : KurobaViewModel() {
  private val _allNoneActiveBoards = mutableListWithCap<ChanBoard>(initialCapacity = 1024)

  private val _checkedBoards = mutableStateSetOf<BoardDescriptor>()
  val checkedBoards: SnapshotStateSet<BoardDescriptor>
    get() = _checkedBoards

  private val _uiState = mutableStateOf<AsyncUiData<Unit>>(AsyncUiData.NotInitialized)
  val uiState: State<AsyncUiData<Unit>>
    get() = _uiState

  private val _processing = mutableStateOf(false)
  val processing: State<Boolean>
    get() = _processing

  private val _boardsForSelection = mutableStateListOf<BoardForSelection>()
  val boardsForSelection: SnapshotStateList<BoardForSelection>
    get() = _boardsForSelection

  private val _currentSearchQuery = mutableStateOf("")
  val currentSearchQuery: State<String>
    get() = _currentSearchQuery

  private val _totalBoardsCount = mutableIntStateOf(-1)
  val totalBoardsCount: IntState
    get() = _totalBoardsCount

  private val _totalMatchedBySearchQueryCount = mutableIntStateOf(-1)
  val totalMatchedBySearchQueryCount: IntState
    get() = _totalMatchedBySearchQueryCount

  private val _resetScrollEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = Channel.RENDEZVOUS)
  val resetScrollEventFlow: SharedFlow<Unit>
    get() = _resetScrollEventFlow.asSharedFlow()

  private val _params = savedStateHandle.requireParams<AddBoardsControllerParams>()
  private val _siteDescriptor: SiteDescriptor
    get() = _params.siteDescriptor

  private val _searchQueryUpdateExecutor = DebouncingCoroutineExecutor(viewModelScope)

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    _uiState.value = AsyncUiData.NotInitialized

    viewModelScope.launch(Dispatchers.Default) {
      boardManager.awaitUntilInitialized()
      siteManager.awaitUntilInitialized()

      val site = siteManager.bySiteDescriptorAndActive(_siteDescriptor)
      if (site == null) {
        _uiState.value = AsyncUiData.Error(Exception("No site found by descriptor: ${_siteDescriptor}"))
        return@launch
      }

      val isSiteActive = siteManager.isSiteActive(_siteDescriptor)
      if (!isSiteActive) {
        _uiState.value = AsyncUiData.Error(Exception("Site with descriptor ${_siteDescriptor} is not active!"))
        return@launch
      }

      _uiState.value = AsyncUiData.Loading

      val refreshError = refreshBoardsFromServerIfNeeded(site as? SiteBase)
      if (refreshError != null && boardManager.boardsCount(_siteDescriptor) <= 0) {
        _uiState.value = AsyncUiData.Error(refreshError)
        return@launch
      }

      loadInactiveBoards(_siteDescriptor)
      findBoardsForSelection()
    }
  }

  fun onSearchQueryUpdated(query: String) {
    if (query == _currentSearchQuery.value) {
      return
    }

    _searchQueryUpdateExecutor.post(timeout = 200L) { findBoardsForSelection(query) }
  }

  fun onBoardCheckStateChanged(boardDescriptor: BoardDescriptor, check: Boolean) {
    if (check) {
      _checkedBoards.add(boardDescriptor)
    } else {
      _checkedBoards.remove(boardDescriptor)
    }
  }

  /**
   * Checks all boards matching the current search query, or unchecks them if they are all checked already. Boards
   * hidden by the search are left as they are.
   * */
  fun toggleAll() {
    val query = _currentSearchQuery.value
    val matchedBoardDescriptors = _allNoneActiveBoards
      .filter { chanBoard -> boardMatchesQuery(chanBoard, query) }
      .map { chanBoard -> chanBoard.boardDescriptor }

    if (_checkedBoards.containsAll(matchedBoardDescriptors)) {
      _checkedBoards.removeAll(matchedBoardDescriptors.toSet())
      return
    }

    _checkedBoards.addAll(matchedBoardDescriptors)
  }

  fun activateCheckedBoards(onDone: () -> Unit) {
    viewModelScope.launch {
      try {
        boardManager.activateDeactivateBoards(
          siteDescriptor = _siteDescriptor,
          boardDescriptors = checkedBoards.toList(),
          activate = true
        )
      } finally {
        onDone()
      }
    }
  }

  /**
   * Same rule as the site's board settings (BoardsReorderControllerViewModel): load the board list from the server when
   * it has never been loaded (a newly enabled site) or is older than [SiteBase.BoardRefreshIntervalDays], otherwise
   * there may be nothing to select here. Returns the error if loading failed.
   * */
  private suspend fun refreshBoardsFromServerIfNeeded(site: SiteBase?): Throwable? {
    if (site == null || site.hasSiteFeature(SiteConfiguration.SiteFeature.CatalogComposition)) {
      return null
    }

    val refreshPeriodMs = TimeUnit.DAYS.toMillis(SiteBase.BoardRefreshIntervalDays.toLong())
    val lastRefreshTime = site.commonSettings.lastSiteBoardsRefreshTime.read()
    val needRefresh = boardManager.boardsCount(_siteDescriptor) <= 0
      || lastRefreshTime + refreshPeriodMs < System.currentTimeMillis()

    if (!needRefresh) {
      return null
    }

    val siteBoardsResult = site.actions.loadBoardInfo()
      .filterIsInstance<SiteBoards.Result>()
      .first()

    return when (siteBoardsResult) {
      is SiteBoards.Result.Error -> {
        Logger.error(TAG, siteBoardsResult.error) { "Error loading boards for site ${_siteDescriptor}" }
        siteBoardsResult.error
      }
      is SiteBoards.Result.Success -> {
        site.commonSettings.lastSiteBoardsRefreshTime.write(System.currentTimeMillis())
        null
      }
    }
  }

  private fun loadInactiveBoards(siteDescriptor: SiteDescriptor) {
    _allNoneActiveBoards.clear()
    val totalBoardsCount = AtomicInt(0)

    boardManager.viewBoardsWhile(
      boardViewMode = BoardManager.BoardViewMode.All,
      siteDescriptor = siteDescriptor
    ) { chanBoard ->
      if (!chanBoard.active) {
        totalBoardsCount.incrementAndFetch()
        _allNoneActiveBoards.add(chanBoard)
      }

      return@viewBoardsWhile true
    }

    _totalBoardsCount.intValue = totalBoardsCount.load()
  }

  private suspend fun findBoardsForSelection(query: String = "") {
    try {
      _processing.value = true

      return withContext(Dispatchers.Default) {
        val matchedBoards = mutableListWithCap<BoardForSelection>(MAX_DISPLAYED_BOARDS)
        var totalMatched = 0

        for (chanBoard in _allNoneActiveBoards) {
          if (boardMatchesQuery(chanBoard, query)) {
            ++totalMatched

            if (matchedBoards.size < MAX_DISPLAYED_BOARDS) {
              matchedBoards += BoardForSelection(
                boardDescriptor = chanBoard.boardDescriptor,
                boardName = BoardHelper.formatName(chanBoard.boardDescriptor.boardCode, chanBoard.boardName()),
                description = BoardHelper.formatDescription(chanBoard),
                workSafe = chanBoard.workSafe
              )
            }
          }
        }

        val sortedBoards = if (query.isEmpty()) {
          matchedBoards.sortedBy { matchedBoard ->
            matchedBoard.boardDescriptor.boardCode
          }
        } else {
          InputWithQuerySorter.sort(
            input = matchedBoards,
            query = query,
            textSelector = { boardForSelection -> boardForSelection.boardName }
          )
        }

        Snapshot.withMutableSnapshot {
          _uiState.value = AsyncUiData.UiData(Unit)
          _currentSearchQuery.value = query
          _totalMatchedBySearchQueryCount.intValue = totalMatched
          _boardsForSelection.clear()
          _boardsForSelection.addAll(sortedBoards)
        }

        awaitFrame()
        _resetScrollEventFlow.emit(Unit)
      }
    } finally {
      _processing.value = false
    }
  }

  private fun boardMatchesQuery(chanBoard: ChanBoard, query: String): Boolean {
    val boardDescription = chanBoard.description

    return query.isEmpty()
      || chanBoard.formattedBoardCode().contains(query, ignoreCase = true)
      || chanBoard.boardName().contains(query, ignoreCase = true)
      || (boardDescription.isNotEmpty() && boardDescription.contains(query, ignoreCase = true))
  }

  data class BoardForSelection(
    val boardDescriptor: BoardDescriptor,
    val boardName: String,
    val description: String,
    val workSafe: Boolean?
  ) {
    fun composeKey(): BoardDescriptor = boardDescriptor
  }

  class Exception(message: String) : ClientException(message)

  class ViewModelFactory @Inject constructor(
    private val siteManager: SiteManager,
    private val boardManager: BoardManager,
  ) : ViewModelAssistedFactory<AddBoardsControllerViewModel> {
    override fun create(handle: SavedStateHandle): AddBoardsControllerViewModel {
      return AddBoardsControllerViewModel(
        savedStateHandle = handle,
        siteManager = siteManager,
        boardManager = boardManager,
      )
    }
  }

  companion object {
    private const val TAG = "AddBoardsControllerViewModel"

    const val MAX_DISPLAYED_BOARDS = 256
  }
}