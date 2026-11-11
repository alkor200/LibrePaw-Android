package org.librepaw.app.data.pets

import kotlinx.serialization.Serializable

/**
 * Only closed excursions are ever cached (an ongoing one's distance/end are still growing).
 * [coveredFrom] is how far back this cache's own live fetches have ever reached;
 * [cachedThroughEndTime] is the watermark up to which a live fetch is no longer needed.
 */
@Serializable
data class CachedExcursions(val excursions: List<Excursion>, val coveredFrom: Long?, val cachedThroughEndTime: Long?)
