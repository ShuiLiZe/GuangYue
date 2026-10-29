/**
 * Folia `<audio>` 元素 ↔ 原生 ExoPlayer 的接缝。
 *
 * 背景：Folia 的播放页本来由两副 `<audio>` 牌（automix A/B）出声音，同一副牌还负责
 * WebAudio 频谱、视频层的音频取样、以及“这首放完了没”的判断。广乐这边声音必须由原生
 * Media3 出（后台、锁屏、通知栏、耳机键），但**不能因此把 `<audio>` 拆掉**——拆掉之后
 * automix 的牌位、`getAttribute('src') === audioSrc` 的自启动判定、`ended` 事件全都会失效。
 *
 * 所以这里做的是“同一首歌，两个播放入口，一份权威时钟”：
 *
 * - `<audio>` 保留，但 `src` 由本模块改写成原生中转地址 `/media/<token>`（见 MediaRelay），
 *   它照常缓冲、照常出 PCM 给频谱，但**被静音**：`muted` 恒为 true，且音量恒为 0。
 *   浏览器只看得到这一副“影子播放器”，用户听到的是 ExoPlayer。
 * - 所有**意图**（play / pause / seek / 换歌）都转发给原生；`<audio>` 上的同名动作是结果，
 *   不是原因——因此在转发之后不再等待影子播放器。
 * - 所有**时间**以原生推送为准（`subscribePlayback`，60ms 一次）。影子播放器自己的
 *   `currentTime` 只在没有原生时作为兜底。
 *
 * 这样做还有一个副作用值得记下来：影子播放器与原生几乎同步（同一 URL、同一缓冲策略），
 * 因此频谱与歌词的相位基本一致；即便有几十毫秒漂移，歌词动画读的也是原生时钟，不受影响。
 */
import { call, hasNativeBridge, subscribePlayback, type NativePlaybackState } from './bridge';

const MUTED = true;

type Shadow = HTMLAudioElement & {
    /** 原始地址 → 中转地址的缓存 key。 */
    __gyRelayKey?: string;
    /** 已经转发过 play 的地址，避免同一首重复下发。 */
    __gyForwardedSrc?: string;
};

const relays = new Map<string, string>();
const shadows = new Set<Shadow>();

export const isNativePlaybackActive = (): boolean => hasNativeBridge();

/**
 * 把一个（插件的、本地的）真实播放地址换成 WebView 可访问的中转地址。
 *
 * 插件给的直链往往带防盗链头、需要 Referer 或者只对特定 UA 有效，WebView 里直接播必然 403；
 * 原生中转会按原样把请求头带过去，顺便把 Range 支持补齐。
 */
export const relayUrl = async (url: string, headers?: Record<string, string>, mimeType?: string): Promise<string> => {
    if (!hasNativeBridge() || !url) return url;
    // 已经是中转地址、或者是 blob/data 这类本地地址，不用再过一遍。
    if (url.startsWith('https://appassets.androidplatform.net/') || url.startsWith('blob:') || url.startsWith('data:')) {
        return url;
    }
    const key = `${url}|${JSON.stringify(headers ?? {})}`;
    const cached = relays.get(key);
    if (cached) return cached;
    try {
        const answer = await call<{ url: string }>('media.token', { url, headers: headers ?? {}, mimeType });
        if (answer?.url) {
            relays.set(key, answer.url);
            return answer.url;
        }
    } catch (error) {
        console.warn('[GuangYue native] 中转地址失败，退回原始地址', error);
    }
    return url;
};

/**
 * 让一副 `<audio>` 牌成为“影子播放器”。
 *
 * 幂等：React 每次 commit 都会重新走一遍，只有第一次会真的挂属性。用 setter 拦截
 * `play`/`pause`，让 Folia 内部任何 `element.play()` 调用都变成“请求原生播放”。
 */
export const attachShadow = (element: HTMLAudioElement): void => {
    if (!element || !hasNativeBridge()) return;
    const shadow = element as Shadow;
    if (shadows.has(shadow)) return;

    shadow.muted = MUTED;
    shadow.volume = 0;
    // 影子牌不参与音量的任何计算，避免均衡器/淡出在它身上白算一遍。
    Object.defineProperty(shadow, 'volume', {
        get: () => 0,
        set: () => { },
        configurable: true,
    });

    const originalPlay = shadow.play.bind(shadow);
    const originalPause = shadow.pause.bind(shadow);

    shadow.play = () => {
        void import('./bridge').then(({ player }) => {
            player.play().catch((error: unknown) => {
                console.warn('[GuangYue native] 原生 play 失败', error);
            });
        });
        return originalPlay().catch((error: DOMException) => {
            // 影子牌播放失败（例如中转 404）不应该影响真实播放：原生那边已经放起来了。
            if (error?.name !== 'AbortError') {
                console.warn('[GuangYue native] 影子播放器未能启动，原生不受影响', error?.name, error?.message);
            }
            return undefined;
        });
    };

    shadow.pause = () => {
        if (shadow.__gyForwardedSrc) {
            void import('./bridge').then(({ player }) => player.pause().catch(() => { }));
        }
        originalPause();
    };

    // 原生把时钟推过来：影子牌对齐（seek 之外只做“必要时校正”，避免每 60ms 抖一次）。
    const detach = subscribePlayback((state: NativePlaybackState) => {
        if (!state.track) return;
        const target = state.positionMs / 1000;
        const drift = Math.abs(shadow.currentTime - target);
        if (drift > 0.6) {
            // seek 之后立刻追平
            try {
                shadow.currentTime = target;
            } catch {
                // 元数据还没到，Ignored。
            }
        }
        if (state.isPlaying && shadow.paused && shadow.__gyForwardedSrc) {
            // 原生在放，影子却停了（后台切回、缓冲结束）：重新拉起，保证频谱不断。
            originalPlay().catch(() => { });
        }
    });

    Object.defineProperty(shadow, '__gyDetachShadow', { value: detach, configurable: true });
    shadows.add(shadow);
};

export const isShadow = (element: HTMLAudioElement | null | undefined): boolean =>
    Boolean(element && shadows.has(element as Shadow));

/** 影子牌是否已经拿到了源（原生模式下 audioSrc 与元素的 src 字符串不再相等，用它来判断）。 */
export const hasShadowSource = (element: HTMLAudioElement | null | undefined): boolean => (
    Boolean(element && (element.currentSrc || element.getAttribute('src')))
);

/** 把一次 Folia 侧的 seek 变成原生 seek。 */
export const seekNative = (seconds: number): void => {
    if (!hasNativeBridge()) return;
    void import('./bridge').then(({ player }) => player.seek(seconds * 1000).catch(() => { }));
};

/**
 * 修好影子牌的一个常见陷阱：`new Audio()` 会在 Folia 内部被直接创建（当地音乐、整曲校验），
 * 这些临时元素不需要影子化——它们只读字节，不出声。这里只处理会被交给 `audioRef` 的牌。
 */
export const describeShadowState = (): { count: number; urls: string[] } => ({
    count: shadows.size,
    urls: Array.from(shadows).map((s) => s.currentSrc || s.src).filter(Boolean).slice(0, 4),
});
