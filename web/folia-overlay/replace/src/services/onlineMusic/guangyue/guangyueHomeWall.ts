/**
 * 广乐的首页「歌曲墙」：把插件源的一次搜索变成一个 Folia 能打开的集合。
 *
 * Folia 的首页原本靠平台账号的歌单 / 电台 / 每日推荐撑起整面墙。广乐不做账号、不做推荐
 * 后端，所以新装时墙面是空的——结构在、留白也在，但没有内容。这里用插件源补上唯一的
 * "无名歌单"：搜索插件库（默认空关键词 = 全部），把结果当成一个集合。
 *
 * 点开之后走的是 Folia 自己的集合页（`omni.getCollectionTracks` → 我们 provider 的
 * `catalog.getPlaylistTracks`），播放、封面、加入队列、批量选择全是原生逻辑。
 */
import type { UnifiedSong } from '@/types';
import type { ProviderCollection } from '@/types/onlineMusic';
import { omni } from '@/services/onlineMusic/omni';
import { GUANGYUE_PROVIDER_ID } from '@/services/onlineMusic/guangyue/guangyueProvider';
import { hasNativeBridge } from '@/services/native/bridge';
import { useOnlineProviderAccountStore } from '@/stores/useOnlineProviderAccountStore';

/** 首页墙的默认内容：插件源里搜这个关键词。空字符串 = 让插件返回它的全部（demo 插件就是这样）。 */
export const DEFAULT_HOME_QUERY = '';

export const GUANGYUE_HOME_PROVIDER_ID = GUANGYUE_PROVIDER_ID;

/**
 * 当前数据源是不是广乐自己（插件源）。
 *
 * Folia 对「无账号平台」（`requiresAccount: false`）的规则是不显示任何在线列表，只给一个
 * "去搜索吧"的空面板——那是为 Folium mod 源准备的措辞（"等 Folium v2 的完整来源接口"）。
 * 广乐就是这个平台，首页要照常渲染「我的歌曲」卡，所以这个判定要露出来给 suite 用。
 */
export const isGuangYueSource = (providerId?: string): boolean => {
    if (providerId) return providerId === GUANGYUE_PROVIDER_ID;
    try {
        return useOnlineProviderAccountStore.getState().activeProviderId === GUANGYUE_PROVIDER_ID;
    } catch {
        return false;
    }
};

/** 这个集合的身份：插件源 + 关键词。用于 Folia 的集合 key 与缓存。 */
export const homeWallCollectionId = (query = DEFAULT_HOME_QUERY) => `guangyue:home:${query || 'all'}`;

/** 插件源首页集合的固定身份（形状与 Folia 内部的 ProviderCollection 一致）。 */
export const BUILD_HOME_WALL_COLLECTION = (): ProviderCollection => ({
    id: homeWallCollectionId(DEFAULT_HOME_QUERY),
    providerId: GUANGYUE_HOME_PROVIDER_ID,
    type: 'playlist',
    name: '我的歌曲',
    coverUrl: undefined,
    creator: '广乐插件',
    trackCount: 0,
    subscribed: false,
});

/**
 * 读出首页要展示的曲目。
 *
 * 单页上限 60：Bravais 的一面墙在这个数量级上最像"一堆专辑"，再多翻页成本就压过了浏览本身。
 * 拿不到原生桥或搜索失败时返回空数组，界面按 Folia 原本的空态处理（不是错误）。
 */
export const loadHomeWallSongs = async (query = DEFAULT_HOME_QUERY): Promise<UnifiedSong[]> => {
    if (!hasNativeBridge()) return [];
    try {
        const page = await omni.searchSongs(query, { limit: 60, offset: 0 });
        return page.items ?? [];
    } catch (error) {
        console.warn('[GuangYue] 首页搜索失败', error);
        return [];
    }
};

/**
 * 带缓存的版本。
 *
 * Folia 的首页每翻一次 tab 都会重建卡片；插件搜索有成本（每个插件一次 JS 执行），所以结果按
 * 关键词缓存一个会话，TTL 两分钟，刷新按钮可以不经过缓存（force）。
 */
let wallCache: { at: number; key: string; songs: UnifiedSong[] } | null = null;
const WALL_TTL_MS = 120_000;

export const loadHomeWallSongsCached = async (query = DEFAULT_HOME_QUERY, force = false): Promise<UnifiedSong[]> => {
    const key = query || 'all';
    if (!force && wallCache && wallCache.key === key && Date.now() - wallCache.at < WALL_TTL_MS) return wallCache.songs;
    const songs = await loadHomeWallSongs(query);
    wallCache = { at: Date.now(), key, songs };
    return songs;
};

export const invalidateHomeWallCache = (): void => {
    wallCache = null;
};
