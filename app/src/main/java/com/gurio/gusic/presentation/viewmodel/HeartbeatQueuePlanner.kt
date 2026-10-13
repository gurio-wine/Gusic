package com.gurio.gusic.presentation.viewmodel

import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.data.model.Song
import kotlin.random.Random

/**
 * 心动模式队列规划：种子池构建与离开复原的纯函数，独立出来便于单测。
 */
internal object HeartbeatQueuePlanner {

    /** 生成批次的队列总数上限，达到后不再生成。 */
    const val QUEUE_LIMIT = 150

    /** 种子池（歌单 B）的补足目标数量。 */
    const val SEED_POOL_TARGET = 5

    /**
     * 歌单 B = 快照 A 的网易子集；不足 [SEED_POOL_TARGET] 时从"我喜欢"歌单的候选里
     * 随机补足（按 songmid 去重，排除当前播放的歌）。喜欢歌单也凑不够时有多少取多少。
     */
    fun buildSeedPool(
        snapshot: List<Song>,
        currentSongMid: String?,
        likedCandidates: List<Song>,
        random: Random = Random.Default,
    ): List<Song> {
        val base = snapshot.filter { it.lxMusicSource == LxSources.NETEASE }
        if (base.size >= SEED_POOL_TARGET) return base
        val existingMids = base.mapNotNull { it.lxMusicSongMid }.toHashSet()
        if (!currentSongMid.isNullOrBlank()) existingMids += currentSongMid
        val extras = likedCandidates
            .filter { it.lxMusicSource == LxSources.NETEASE }
            .filter { !it.lxMusicSongMid.isNullOrBlank() && it.lxMusicSongMid !in existingMids }
            .shuffled(random)
            .take(SEED_POOL_TARGET - base.size)
        return base + extras
    }

    /**
     * 离开心动模式时的复原布局，返回(目标队列, 当前歌 s2 在目标队列中的位置)：
     * - s2 ∈ C（原歌单网易子集）：按 A 原顺序恢复，s2 回到原位。
     * - s2 ∉ C：以 s3（当前队列中 s2 上方最近的 C 中歌曲）为锚，s2 紧插在 A 中 s3 之后，
     *   其余歌按原序分布在 (s3, s2) 整体前后。队列头部必有进入时的 s1（∈C），s3 必存在。
     * 返回 null 表示无法规划（s2 不在 A、s2 不在当前队列等异常），调用方走兜底恢复。
     */
    fun planRestoreLayout(
        snapshot: List<Song>,
        originalNeteaseIds: Set<String>,
        currentQueue: List<Song>,
        s2: Song,
    ): Pair<List<Song>, Int>? {
        if (s2.id in originalNeteaseIds) {
            val index = snapshot.indexOfFirst { it.id == s2.id }
            if (index < 0) return null
            return snapshot to index
        }
        val s2Index = currentQueue.indexOfFirst { it.id == s2.id }
        if (s2Index < 0) return null
        val s3Id = (s2Index - 1 downTo 0)
            .firstNotNullOfOrNull { j -> currentQueue[j].id.takeIf { it in originalNeteaseIds } }
        val anchorIndexInA = snapshot.indexOfFirst { it.id == s3Id }
        if (anchorIndexInA < 0) return null
        val layout = snapshot.toMutableList()
        layout.add(anchorIndexInA + 1, s2)
        return layout to (anchorIndexInA + 1)
    }
}
