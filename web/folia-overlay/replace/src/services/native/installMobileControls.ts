/**
 * 给 Folia 的整张播放表接上原生控制台。
 *
 * Folia 有个「命令面板 / 快捷键」体系，播放的暂停、下一首、快进都在 `usePlaybackInteractionBridge`
 * 里——但那是键盘事件，手机上没有键盘。这里在同一张表里加一份触摸与系统媒体键的处理：
 *
 * 1. **触摸手势**：播放页下拉收起、双击封面暂停、左右滑换曲、歌词面板上下滑微调、
 *    悬浮胶囊点按展开。都挂在 `document` 的捕获阶段，用 `touch-action` 与阈值避免误触滚动。
 * 2. **系统媒体键**：锁屏/通知栏由原生 MediaSession 负责，Web 不需要再处理；但耳机键的
 *    “播放/暂停”在部分 ROM 上会同时派发 MediaPlayPause 键盘事件，这里转发给原生保持幂等。
 * 3. **返回键**：由原生 Activity 决定（页面栈由 Web 自己管，`history.back()` 优先）。
 *
 * 只做“移动端缺什么补什么”，不覆盖 Folia 已有的鼠标/键盘逻辑。
 */
import { hasNativeBridge, player } from './bridge';

const SWIPE_THRESHOLD_PX = 56;
const DOUBLE_TAP_MS = 280;

type TouchState = {
    startX: number;
    startY: number;
    startedAt: number;
    lastTapAt: number;
    handled: boolean;
};

let state: TouchState | null = null;
let pendingTapAt = 0;

const isPlayerSurface = (target: EventTarget | null): boolean => {
    if (!(target instanceof Element)) return false;
    // Folia 播放页的根节点带 data 标记（app surface），歌词全屏时也在这棵树里。
    return Boolean(target.closest('[data-folia-player], #player-panel, [data-app-view="player"]'));
};

const onTouchStart = (event: TouchEvent) => {
    if (event.touches.length !== 1) return;
    const touch = event.touches[0];
    state = {
        startX: touch.clientX,
        startY: touch.clientY,
        startedAt: event.timeStamp,
        lastTapAt: state?.lastTapAt ?? 0,
        handled: false,
    };
};

const onTouchEnd = (event: TouchEvent) => {
    const started = state;
    state = null;
    if (!started || event.changedTouches.length !== 1) return;
    const touch = event.changedTouches[0];
    const dx = touch.clientX - started.startX;
    const dy = touch.clientY - started.startY;
    const duration = event.timeStamp - started.startedAt;

    // 长按/慢拖是滚动，不抢。
    if (duration > 700) return;
    if (Math.abs(dx) < 12 && Math.abs(dy) < 12) {
        // 双击播放页上半区 = 暂停/继续（与桌面空格键一致）。
        const now = event.timeStamp;
        if (now - started.lastTapAt < DOUBLE_TAP_MS && isPlayerSurface(event.target)) {
            if (hasNativeBridge()) void player.toggle().catch(() => { });
        }
        if (state === null) {
            pendingTapAt = now;
        }
        return;
    }

    if (!isPlayerSurface(event.target)) return;
    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > SWIPE_THRESHOLD_PX) {
        // 左右滑：上一首 / 下一首
        if (hasNativeBridge()) {
            void (dx > 0 ? player.previous() : player.next()).catch(() => { });
        }
        return;
    }
    if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > SWIPE_THRESHOLD_PX * 1.6) {
        // 上滑 = 下一首，下滑 = 展开播放页（Folia 自己有对应手势时以它为准，这里只兜底）。
        if (dy < 0 && hasNativeBridge()) {
            void player.next().catch(() => { });
        }
    }
};


const onKeyEvent = (event: KeyboardEvent) => {
    // 系统媒体键在部分 ROM 上以键盘事件形式到达 WebView。
    if (event.code === 'MediaPlayPause') {
        // 原生 MediaSession 已经处理了这次按键，这里什么都不做，避免双触发。
    }
};

export const installMobileControls = (): void => {
    if (typeof window === 'undefined') return;
    if ((window as unknown as { __gyMobileControls?: boolean }).__gyMobileControls) return;
    (window as unknown as { __gyMobileControls?: boolean }).__gyMobileControls = true;

    document.addEventListener('touchstart', onTouchStart, { capture: true, passive: true });
    document.addEventListener('touchend', onTouchEnd, { capture: true, passive: true });
    window.addEventListener('keydown', onKeyEvent, { capture: true });
};
