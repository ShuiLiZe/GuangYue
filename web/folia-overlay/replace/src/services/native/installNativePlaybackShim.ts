/**
 * 影子播放器的启动接线 —— Folia 启动时最先加载的一段代码。
 *
 * Folia 里所有出声音的 `<audio>` 都来自 automix 的两副牌（`data-playback-deck="A"/"B"`）：
 * 它们由 React 渲染、由 autoplay 逻辑 `play()`、由 automix 用来做淡入淡出。广乐这边不能改
 * 这套结构（改了播放页会散），所以用一个 MutationObserver 抓住它们，一旦出现在 DOM 里就
 * 交给 `attachShadow` 变成“只供频谱的静音影子牌”，声音交给原生 ExoPlayer。
 *
 * 另一件必须在这里做的事：Folia 的 `audioSrc` 是插件直链，WebView 里直接播会因为防盗链
 * （Referer/UA 绑定、需要 Range）失败。这里在 `<audio>` 被赋 src 之前把它换成原生中转地址，
 * 让影子牌能真的缓冲起来——**频谱是真的，只是不出声**。
 */
import { hasNativeBridge } from './bridge';
import { attachShadow, relayUrl } from './shadowPlayer';

const DECK_SELECTOR = 'audio[data-playback-deck]';

const relayFor = new WeakMap<HTMLAudioElement, string>();

const fixSource = async (element: HTMLAudioElement) => {
    const src = element.getAttribute('src');
    if (!src || src.startsWith('https://appassets.androidplatform.net/')) return;
    if (relayFor.get(element) === src) return;
    relayFor.set(element, src);
    const relayed = await relayUrl(src);
    if (relayed === src) return;
    // 只在元素还没真正开始播放时改写；已经在播的那一帧动它会打断缓冲。
    if (element.getAttribute('src') !== src) return;
    element.setAttribute('src', relayed);
    try {
        element.load();
    } catch {
        // 元数据还没就绪时 load() 抛错可以忽略，浏览器下一帧会自己重试。
    }
};

const adopt = (element: HTMLAudioElement) => {
    attachShadow(element);
    void fixSource(element);
    const observer = new MutationObserver(() => {
        void fixSource(element);
    });
    observer.observe(element, { attributes: true, attributeFilter: ['src'] });
};

const scan = (root: ParentNode) => {
    root.querySelectorAll<HTMLAudioElement>(DECK_SELECTOR).forEach((element) => {
        if (!(element as unknown as { __gyAdopted?: boolean }).__gyAdopted) {
            (element as unknown as { __gyAdopted?: boolean }).__gyAdopted = true;
            adopt(element);
        }
    });
};

/**
 * 安装。幂等，重复调用没有副作用。
 */
export const installNativePlaybackShim = (): void => {
    if (typeof window === 'undefined' || !hasNativeBridge()) return;
    if ((window as unknown as { __gyPlaybackShim?: boolean }).__gyPlaybackShim) return;
    (window as unknown as { __gyPlaybackShim?: boolean }).__gyPlaybackShim = true;

    const start = () => {
        scan(document);
        const observer = new MutationObserver((records) => {
            for (const record of records) {
                record.addedNodes.forEach((node) => {
                    if (node instanceof HTMLElement) {
                        if (node instanceof HTMLAudioElement && node.dataset.playbackDeck) {
                            (node as unknown as { __gyAdopted?: boolean }).__gyAdopted = true;
                            adopt(node);
                        }
                        scan(node);
                    }
                });
            }
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start, { once: true });
    } else {
        start();
    }
};
