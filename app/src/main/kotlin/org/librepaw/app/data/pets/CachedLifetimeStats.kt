package org.librepaw.app.data.pets

import kotlinx.serialization.Serializable

@Serializable
data class CachedLifetimeStats(val totalDistanceMeters: Double, val totalDurationMillis: Long, val cachedThroughEndTime: Long?)
