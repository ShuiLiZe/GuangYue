/**
 * 广乐数据源适配层：把 Folia 的 `OnlineMusicProvider` 契约接到原生插件运行时。
 *
 * 为什么需要这一层：Folia 内置了网易云 / QQ / 酷狗 / Spotify / YouTube Music 等 7 个平台
 * 适配器，它们直连各平台 API 并带登录态。广乐不接这些（也不做任何平台的 VIP 绕过），
 * 广乐的数据源是用户自己导入的 MusicFree 格式插件，由原生 quickjs 运行时执行。
 *
 * 于是这里注册**一个** provider，id 固定为 `guangyue`，能力按插件的实际实现声明：
 * - search → 原生并行查所有已启用插件，按插件合并（Folia 的搜索页天然按平台列显示）
 * - playback.getAudioSource → 插件的 `getMediaSource`，再换成 WebView 中转地址
 * - lyrics → 插件的 `getLyric`（LRC 文本），交给 Folia 自己的解析器
 * - songMetadata → 插件的 `getMusicInfo`，失败就用歌曲行本身的字段
 *
 * 不做的事（会明确声明成 false，让 UI 自己收起来）：
 * 登录、账号库、收藏同步、歌单云端同步、推荐、播放上报、视频。
 */
import type { SongResult, UnifiedSong } from '../../../types';
import type {
    AudioQualityPreference,
    OnlineMusicProvider,
    ProviderAudioSource,
    ProviderCapabilities,
    ProviderLyricsResult,
    ProviderPage,
    ProviderSongMetadata,
} from '../../../types/onlineMusic';
import { call, hasNativeBridge } from '../../../services/native/bridge';
import { relayUrl } from '../../../services/native/shadowPlayer';
import { parseLRC } from '../../../utils/lrcParser';

export const GUANGYUE_PROVIDER_ID = 'guangyue';

/** 原生 search 返回的插件行。 */
interface NativeRow {
    pluginId: string;
    platform: string;
    id: string;
    title: string;
    artist: string;
    album?: string;
    artwork?: string;
    duration?: number;
    albumId?: string;
    artistId?: string;
    raw?: Record<string, unknown> | null;
}

interface NativeSearchAnswer {
    results: Array<{
        pluginId: string;
        platform: string;
        pluginName: string;
        isEnd: boolean;
        error?: string;
        items: NativeRow[];
        albums: Array<Record<string, unknown>>;
        artists: Array<Record<string, unknown>>;
    }>;
    pluginsQueried: number;
}

/** Folia 的 AudioQualityPreference 与广乐 MusicFree 的 wire 值之间的映射。 */
const toWireQuality = (quality: AudioQualityPreference): string => {
    switch (quality) {
        case 'hires': return 'hires';
        case 'lossless': return 'lossless';
        case 'ultra': return 'high';
        case 'high': return 'high';
        case 'standard':
        default: return 'standard';
    }
};

/**
 * 插件行 → Folia 的 UnifiedSong。
 *
 * 关键约定：`sourceRef.mediaId` 放的是插件里的 `platform:id`（广乐曲库的 key），
 * providerId 放的是插件 platform。这样队列/历史落库之后，即使插件被关掉也只是“放不了”，
 * 而不是整行数据作废。
 */
export const toUnifiedSong = (row: NativeRow): UnifiedSong => {
    const mediaId = `${row.platform}:${row.id}`;
    return {
        id: mediaId,
        name: row.title || '未知歌曲',
        artists: row.artist
            ? row.artist.split(/[/、,&]/).map((name, index) => ({ id: `${mediaId}#artist${index}`, name: name.trim() }))
            : [],
        album: {
            id: `${mediaId}#album`,
            name: row.album ?? '',
            ...(row.artwork ? { coverUrl: row.artwork } : {}),
        },
        durationMs: typeof row.duration === 'number' ? row.duration : 0,
        sourceRef: {
            kind: 'online',
            providerId: GUANGYUE_PROVIDER_ID,
            mediaId,
            providerData: {
                pluginId: row.platform,
                pluginSongId: row.id,
                raw: (row.raw ?? null) as unknown as Record<string, never>,
            },
        },
    };
};

/** 把 UnifiedSong 变回原生要的调用参数（getMediaSource / getLyric 必须收到插件原样吐出的对象）。 */
const toNativeItem = (song: SongResult): Record<string, unknown> => {
    const ref = song.sourceRef?.kind === 'online' ? song.sourceRef : null;
    const data = (ref?.providerData ?? {}) as Record<string, unknown>;
    const pluginId = (data.pluginId as string) || GUANGYUE_PROVIDER_ID;
    return {
        pluginId,
        platform: pluginId,
        id: (data.pluginSongId as string) || String(ref?.mediaId ?? song.id).split(':').slice(1).join(':'),
        title: song.name,
        artist: (song.artists ?? []).map((artist) => artist.name).join('/'),
        album: song.album?.name ?? '',
        artwork: song.album?.coverUrl ?? '',
        duration: song.durationMs ?? 0,
        raw: data.raw ?? null,
    };
};

const EMPTY_PAGE = (offset: number): ProviderPage<UnifiedSong> => ({
    items: [],
    hasMore: false,
    nextOffset: offset,
});

const capabilities: ProviderCapabilities = {
    search: true,
    playback: true,
    lyrics: true,
    auth: false,
    userLibrary: false,
    playlists: false,
    albums: false,
    artists: false,
    recommendations: false,
    mutations: false,
    wordByWordLyrics: false,
    songDetail: true,
    playbackReports: false,
    explore: false,
    personalFmModes: false,
};

/**
 * 搜索：一次性问完所有插件，再按 `offset/limit` 切页。
 *
 * Folia 请求的是“这一 provider 的第 N 页”，而插件是各查各的第 N 页。这里以“每个插件的
 * 当前页”为整体返回，下一屏时把 offset 往后推——插件的页码由 `offset / limit + 1` 反算，
 * 这样 Folia 的无限滚动与插件的分页能对上。
 */
const searchSongs = async (query: string, limit: number, offset: number): Promise<ProviderPage<UnifiedSong>> => {
    if (!hasNativeBridge()) return EMPTY_PAGE(offset);
    const pageSize = limit > 0 ? limit : 30;
    const page = Math.floor(offset / pageSize) + 1;
    const answer = await call<NativeSearchAnswer>('music.search', {
        keyword: query,
        type: 'music',
        page,
        limit: pageSize,
    });
    if (!answer?.results?.length) return EMPTY_PAGE(offset);

    const items: UnifiedSong[] = [];
    for (const result of answer.results) {
        if (result.error) continue;
        for (const row of result.items ?? []) {
            items.push(toUnifiedSong(row));
        }
    }
    // 插件之间交错展示，避免第一屏被第一个插件占满；顺序稳定（按行号取模）。
    const interleaved = items
        .map((song, index) => ({ song, rank: index }))
        .sort((a, b) => a.rank - b.rank)
        .map(({ song }) => song);

    const anyNotEnd = answer.results.some((result) => !result.isEnd && (result.items?.length ?? 0) > 0);
    return {
        items: interleaved,
        hasMore: anyNotEnd,
        total: interleaved.length,
        nextOffset: offset + interleaved.length,
    };
};

const getAudioSource = async (song: SongResult, quality: AudioQualityPreference): Promise<ProviderAudioSource | null> => {
    if (!hasNativeBridge()) return null;
    const answer = await call<{ url: string; headers?: Record<string, string>; mimeType?: string; quality?: string }>(
        'music.mediaSource',
        { ...toNativeItem(song), quality: toWireQuality(quality) },
    );
    if (!answer?.url) return null;
    const url = await relayUrl(answer.url, answer.headers, answer.mimeType);
    return {
        url,
        fetchedAt: Date.now(),
        quality,
        ...(answer.quality ? { quality: answer.quality as AudioQualityPreference } : {}),
    };
};

const getLyrics = async (song: SongResult): Promise<ProviderLyricsResult> => {
    if (!hasNativeBridge()) {
        return { lyrics: null, isPureMusic: false };
    }
    try {
        const answer = await call<{ lrc?: string | null; translation?: string | null }>('music.lyric', toNativeItem(song));
        const lrc = answer?.lrc ?? '';
        if (!lrc.trim()) return { lyrics: null, isPureMusic: false };
        // 插件给的是 LRC 文本（MusicFree 的 getLyric 契约），解析交给 Folia 自己的核心解析器，
        // 这样逐行/逐字/翻译的渲染方式与内置平台完全一致。
        const lyrics = parseLRC(lrc, answer?.translation ?? '');
        return {
            lyrics,
            mainText: lrc,
            translationText: answer?.translation ?? null,
            isPureMusic: lyrics.lines.length === 0,
        };
    } catch (error) {
        console.warn('[GuangYue] 取歌词失败', error);
        return { lyrics: null, isPureMusic: false };
    }
};

const getSongDetail = async (id: string | number): Promise<UnifiedSong | null> => {
    if (!hasNativeBridge()) return null;
    const text = String(id);
    const [pluginId, songId] = text.includes(':') ? text.split(/:(.*)/s) : ['', text];
    if (!pluginId || !songId) return null;
    try {
        const row = await call<NativeRow>('music.info', { pluginId, id: songId });
        if (!row || !row.id) return null;
        return toUnifiedSong(row);
    } catch {
        return null;
    }
};

const getSongMetadata = (song: SongResult): ProviderSongMetadata => ({
    artists: (song.artists ?? []).map((artist) => ({ id: artist.id ?? artist.name, name: artist.name })),
    album: {
        id: song.album?.id ?? '',
        name: song.album?.name ?? '',
        ...(song.album?.coverUrl ? { coverUrl: song.album.coverUrl } : {}),
    },
    durationMs: song.durationMs ?? 0,
    coverUrl: song.album?.coverUrl || undefined,
    aliases: song.aliases ?? [],
    translatedNames: song.translatedNames ?? [],
});

export const guangyueProvider: OnlineMusicProvider = {
    id: GUANGYUE_PROVIDER_ID,
    displayName: '广乐插件',
    shortName: '广乐',
    mark: { letter: '广', color: '#2f6fed' },
    capabilities,
    getAvailability: () => ({ configured: true }),
    normalizeSong: (raw) => {
        const candidate = raw as UnifiedSong;
        if (candidate?.sourceRef?.kind === 'online' && candidate.sourceRef.providerId === GUANGYUE_PROVIDER_ID) {
            return candidate;
        }
        return toUnifiedSong(raw as NativeRow);
    },
    songMetadata: { getSongMetadata },
    search: { searchSongs },
    playback: {
        getSongDetail,
        getAudioSource,
        audioQualityLevels: ['standard', 'high', 'lossless'] as const,
    },
    lyrics: { getLyrics },
    /**
     * 目录能力：只提供「一个插件歌单」——首页那张「我的歌曲」卡打开时要靠它取曲目。
     *
     * 别的集合类型（专辑/歌手/歌单列表）一律不声明：插件源本来就没有账号目录，声明了只会让
     * 界面出现点开是空的入口。`getPlaylistTracks` 拿到的是首页同一个搜索，翻页按 offset 推进。
     */
    catalog: {
        getPlaylistTracks: async (_id, limit, offset) => {
            if (!hasNativeBridge()) return EMPTY_PAGE(offset);
            const pageSize = limit > 0 ? limit : 60;
            const page = Math.floor(offset / pageSize) + 1;
            const answer = await call<NativeSearchAnswer>('music.search', {
                keyword: '',
                type: 'music',
                page,
                limit: pageSize,
            });
            const items: UnifiedSong[] = [];
            for (const result of answer?.results ?? []) {
                if (result.error) continue;
                for (const row of result.items ?? []) items.push(toUnifiedSong(row));
            }
            const hasMore = (answer?.results ?? []).some((result) => !result.isEnd && (result.items?.length ?? 0) > 0);
            return { items, hasMore, total: items.length, nextOffset: offset + items.length };
        },
        getPlaylistDetail: async (id, collection) => collection,
    },
};
