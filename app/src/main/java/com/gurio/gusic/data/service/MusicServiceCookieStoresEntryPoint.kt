package com.gurio.gusic.data.service

import com.gurio.gusic.data.lxmusic.kugou.KugouCookieStore
import com.gurio.gusic.data.lxmusic.kuwo.KuwoCookieStore
import com.gurio.gusic.data.lxmusic.netease.NeteaseCookieStore
import com.gurio.gusic.data.lxmusic.tencent.TencentCookieStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicServiceCookieStoresEntryPoint {
    fun neteaseCookieStore(): NeteaseCookieStore
    fun tencentCookieStore(): TencentCookieStore
    fun kugouCookieStore(): KugouCookieStore
    fun kuwoCookieStore(): KuwoCookieStore
}
