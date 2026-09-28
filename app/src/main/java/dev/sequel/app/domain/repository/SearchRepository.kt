package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.ShowEntity

interface SearchRepository {
    suspend fun discover(genreId: Int, filter: String): List<ShowEntity>
    suspend fun search(query: String, filter: String): List<ShowEntity>
}
