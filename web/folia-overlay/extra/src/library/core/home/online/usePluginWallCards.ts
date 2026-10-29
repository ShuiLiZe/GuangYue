/**
 * 广乐的「我的歌曲」卡：插件源搜索结果的入口。
 *
 * Folia 首页的每张卡都只是一个 `LibraryHomeCard`，点开时交给 `openOnlineCard` 走集合页。
 * 这里构造这张卡：id / providerId 指向 `guangyue`，`raw` 里放一个 Folia 认识的
 * `ProviderCollection`（类型 playlist），所以点击后的行为与"打开一个歌单"完全一致。
 *
 * 只有当前 provider 是广乐、且当前页签是「歌曲」时才出现——用户在别处（比如 Folium mod
 * 注册的第三方源）时，这里什么都不加。
 */
import { useEffect, useMemo, useState } from 'react';
import type { LibraryHomeCard } from '@/library/core/home/homeModel.contract';
import {
    BUILD_HOME_WALL_COLLECTION,
    DEFAULT_HOME_QUERY,
    GUANGYUE_HOME_PROVIDER_ID,
    loadHomeWallSongsCached,
} from '@/services/onlineMusic/guangyue/guangyueHomeWall';

const PLUGIN_WALL_ID = 'guangyue:wall';

const usePluginWallCards = (providerId: string, tab: string): LibraryHomeCard[] => {
    const isGuangYue = providerId === GUANGYUE_HOME_PROVIDER_ID && tab === 'playlist';
    const [trackCount, setTrackCount] = useState<number | null>(null);

    useEffect(() => {
        if (!isGuangYue) return;
        let cancelled = false;
        // 只用来给卡片写一个可信的曲目数；真正拉曲目在打开集合时发生（Folia 自己那次搜索）。
        void loadHomeWallSongsCached(DEFAULT_HOME_QUERY)
            .then((songs) => {
                if (!cancelled) setTrackCount(songs.length);
            })
            .catch(() => {
                if (!cancelled) setTrackCount(0);
            });
        return () => {
            cancelled = true;
        };
    }, [isGuangYue]);

    return useMemo(() => {
        if (!isGuangYue) return [];
        const collection = { ...BUILD_HOME_WALL_COLLECTION(), trackCount: trackCount ?? 0 };
        const card: LibraryHomeCard = {
            id: PLUGIN_WALL_ID,
            name: '我的歌曲',
            type: 'playlist',
            description: '广乐插件',
            summary: trackCount === null ? '正在整理插件曲库…' : `${trackCount} 首来自插件的歌`,
            trackCount: trackCount ?? 0,
            raw: collection,
        };
        return [card];
    }, [isGuangYue, trackCount]);
};

export { usePluginWallCards, PLUGIN_WALL_ID };
