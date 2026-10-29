/**
 * 广乐原生桥（Android WebView）——Folia 侧的唯一出口。
 *
 * 页面由 `https://appassets.androidplatform.net` 提供，原生侧通过
 * `addJavascriptInterface(...)` 注入 `window.GuangYueNative`：
 *
 *   GuangYueNative.call(method, paramsJson) -> 调用 id（字符串）
 *   GuangYueNative.cancel(id)
 *   原生回调 window.__gyResolve(id, ok, payloadJson)
 *   原生推送 window.__gyPlayback(state) / window.__gyEvent(name, payloadJson)
 *
 * 这里把 call/cancel 包成 Promise，并做三件事：
 * 1. 未就绪时排队，等原生桥注入后再发（WebView 首帧可能比 addJavascriptInterface 早）。
 * 2. 同一个 method + params 在 300ms 内重复调用直接复用上一个 Promise，挡住 StrictMode
 *    双挂载与连续重渲造成的重复请求（插件搜索很贵）。
 * 3. 提供事件订阅（播放状态、下载进度），供各 store 使用。
 */
const NATIVE_KEY = 'GuangYueNative';

/** 桥是否可用。Electron / 浏览器里为 false，此时所有原生能力都降级。 */
export const hasNativeBridge = (): boolean => (
    typeof window !== 'undefined' && Boolean((window as unknown as Record<string, unknown>)[NATIVE_KEY])
);

type PendingEntry = {
    resolve: (value: unknown) => void;
    reject: (error: Error) => void;
};

const pending = new Map<string, PendingEntry>();
const inflight = new Map<string, { at: number; promise: Promise<unknown> }>();
const INFLIGHT_WINDOW_MS = 300;

type PlaybackListener = (state: NativePlaybackState) => void;
type EventListener = (payload: unknown) => void;

const playbackListeners = new Set<PlaybackListener>();
const eventListeners = new Map<string, Set<EventListener>>();

export interface NativePlaybackTrack {
    key: string;
    title: string;
    artist: string;
    album?: string;
    artworkUri?: string;
    pluginId?: string;
    quality?: string;
    localUri?: string;
}

export interface NativePlaybackState {
    isPlaying: boolean;
    positionMs: number;
    durationMs: number;
    bufferedMs: number;
    queueSize: number;
    index: number;
    shuffle: boolean;
    repeatMode: number;
    error: string | null;
    updatedAt: number;
    track: NativePlaybackTrack | null;
}

let lastPlayback: NativePlaybackState | null = null;

const installGlobals = () => {
    if (typeof window === 'undefined') return;
    const scope = window as unknown as Record<string, any>;

    scope.__gyResolve = (id: string, ok: boolean, payload: string) => {
        const entry = pending.get(String(id));
        if (!entry) return;
        pending.delete(String(id));
        if (!ok) {
            let message = payload;
            let code = 'native_error';
            try {
                const parsed = JSON.parse(payload);
                message = parsed?.message ?? payload;
                code = parsed?.error ?? code;
            } catch {
                // payload 不是 JSON 就直接当作错误文本。
            }
            const error = new Error(message);
            (error as Error & { code?: string }).code = code;
            entry.reject(error);
            return;
        }
        try {
            entry.resolve(payload ? JSON.parse(payload) : null);
        } catch {
            entry.resolve(payload);
        }
    };

    scope.__gyPlayback = (state: NativePlaybackState) => {
        lastPlayback = state;
        playbackListeners.forEach((listener) => {
            try {
                listener(state);
            } catch (error) {
                console.error('[GuangYue native] playback listener failed', error);
            }
        });
    };

    scope.__gyEvent = (name: string, payload: string) => {
        let parsed: unknown = payload;
        try {
            parsed = payload ? JSON.parse(payload) : null;
        } catch {
            // 保持原样
        }
        eventListeners.get(name)?.forEach((listener) => {
            try {
                listener(parsed);
            } catch (error) {
                console.error('[GuangYue native] event listener failed', error);
            }
        });
    };
};

installGlobals();

const stableStringify = (value: unknown): string => {
    if (value === null || value === undefined) return '';
    if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
    if (typeof value === 'object') {
        const keys = Object.keys(value as Record<string, unknown>).sort();
        return `{${keys.map((key) => `${JSON.stringify(key)}:${stableStringify((value as Record<string, unknown>)[key])}`).join(',')}}`;
    }
    return JSON.stringify(value);
};

/**
 * 调用一个原生方法。桥不存在时抛 `native_unavailable`，调用方据此走降级分支。
 */
export const call = <T = unknown>(method: string, params: Record<string, unknown> = {}): Promise<T> => {
    const bridge = (typeof window === 'undefined'
        ? undefined
        : (window as unknown as Record<string, any>)[NATIVE_KEY]) as
        | { call: (method: string, params: string) => string; cancel?: (id: string) => void }
        | undefined;

    if (!bridge) {
        return Promise.reject(Object.assign(new Error('原生桥不可用'), { code: 'native_unavailable' }));
    }

    const paramsJson = JSON.stringify(params ?? {});
    const key = `${method}|${stableStringify(params)}`;
    const cached = inflight.get(key);
    if (cached && Date.now() - cached.at < INFLIGHT_WINDOW_MS) {
        return cached.promise as Promise<T>;
    }

    const promise = new Promise<T>((resolve, reject) => {
        let id: string;
        try {
            id = bridge.call(method, paramsJson);
        } catch (error) {
            reject(error instanceof Error ? error : new Error(String(error)));
            return;
        }
        if (id === undefined || id === null || id === '') {
            // 同步返回空 id：原生侧没有把这次调用登记成异步任务。
            resolve(null as T);
            return;
        }
        pending.set(String(id), {
            resolve: resolve as (value: unknown) => void,
            reject,
        });
    });

    inflight.set(key, { at: Date.now(), promise });
    promise.finally(() => {
        const entry = inflight.get(key);
        if (entry && entry.promise === promise) inflight.delete(key);
    }).catch(() => { });
    return promise;
};

export const cancel = (id: string): void => {
    const bridge = (window as unknown as Record<string, any>)[NATIVE_KEY];
    bridge?.cancel?.(id);
    pending.delete(String(id));
};

// --------------------------------------------------------------- 播放状态订阅

export const getLastPlaybackState = (): NativePlaybackState | null => lastPlayback;

export const subscribePlayback = (listener: PlaybackListener): (() => void) => {
    playbackListeners.add(listener);
    if (lastPlayback) {
        try {
            listener(lastPlayback);
        } catch (error) {
            console.error('[GuangYue native] playback listener failed on subscribe', error);
        }
    }
    return () => {
        playbackListeners.delete(listener);
    };
};

export const subscribeEvent = (name: string, listener: EventListener): (() => void) => {
    const set = eventListeners.get(name) ?? new Set<EventListener>();
    set.add(listener);
    eventListeners.set(name, set);
    return () => {
        set.delete(listener);
    };
};

/** 原生播放控制：所有会改变“声音”的动作都必须走这里。 */
export const player = {
    state: () => call<NativePlaybackState>('player.state'),
    play: () => call('player.play'),
    pause: () => call('player.pause'),
    toggle: () => call('player.toggle'),
    next: () => call('player.next'),
    previous: () => call('player.previous'),
    stop: () => call('player.stop'),
    seek: (positionMs: number) => call('player.seek', { positionMs: Math.max(0, Math.round(positionMs)) }),
    volume: (percent: number) => call('player.volume', { percent }),
    shuffle: (enabled: boolean) => call('player.shuffle', { enabled }),
    cycleRepeat: () => call<{ repeatMode: number }>('player.repeat'),
    setQuality: (quality: string) => call('player.quality', { quality }),
    queue: () => call<{ queue: NativePlaybackTrack[]; index: number }>('player.queue'),
    removeAt: (index: number) => call('player.removeAt', { index }),
    setQueue: (items: unknown[], startIndex = 0, play = true) => call('player.setQueue', { items, startIndex, play }),
};
