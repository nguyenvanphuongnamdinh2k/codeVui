package com.example.codevui.ui.common

import com.example.codevui.data.FileRepository

/**
 * Sortable — interface cho ViewModel có sort functionality
 * Implemented by: FileListViewModel, BrowseViewModel
 * Eliminates duplicate onSortChanged/toggleSortDirection logic
 */
interface Sortable {
    fun onSortChanged(sortBy: FileRepository.SortBy)
    fun toggleSortDirection()
}

/**
 * Navigable — interface cho ViewModel có breadcrumb navigation
 * Implemented by: BrowseViewModel, ArchiveViewModel
 * Eliminates duplicate goBack/navigateToSegment pattern
 */
interface Navigable {
    fun goBack(): Boolean
    fun navigateToSegment(index: Int)
}

/**
 * Reloadable — interface cho ViewModel cần reload sau khi file operation
 * Implemented by: all list ViewModels
 */
interface Reloadable {
    fun reload()
}
