/**
 * 把 Folia 的“在线平台”改造成广乐的“插件数据源”。
 *
 * Folia 的在线平台是一等公民：账号 store 里存着每个平台的登录态，首页在没有登录时会显示
 * 「连接在线平台」并把推荐位藏起来。广乐没有平台账号——数据源是用户导入的 MusicFree 插件，
 * 一部分插件根本不需要登录。所以这里在启动时：
 *
 * 1. 把 `activeProviderId` 固定为 `guangyue`。上游用 localStorage 记住上次选的平台，
 *    换了 providerid 之后旧值会指向一个不存在的平台，回落成默认值又可能指向另一个。
 * 2. 给这个 provider 写一条“已连接”的账号快照：对插件源来说“能用”就等于“已连接”。
 * 3. 不伪造用户头像、昵称与收藏列表——这些页面对插件源本来就是空的，留空比假数据诚实。
 */
import { GUANGYUE_PROVIDER_ID } from '../onlineMusic/guangyue/guangyueProvider';
import { useOnlineProviderAccountStore } from '../../stores/useOnlineProviderAccountStore';
import { hasNativeBridge } from './bridge';

let installed = false;

export const installGuangYueSource = (): void => {
    if (installed) return;
    installed = true;
    if (typeof window === 'undefined') return;
    // 浏览器里（对照官方 Web 版、调样式）保持上游行为，不改任何东西。
    if (!hasNativeBridge()) return;

    try {
        const store = useOnlineProviderAccountStore.getState();
        store.setActiveProviderId(GUANGYUE_PROVIDER_ID);
        store.updateAccount(GUANGYUE_PROVIDER_ID, {
            status: 'authenticated',
            hydration: 'ready',
            freshness: 'fresh',
            lastUpdatedAt: Date.now(),
            user: null,
            collections: [],
        });
        console.info('[GuangYue] 在线平台已切换为插件源');
    } catch (error) {
        console.warn('[GuangYue] 切换数据源失败（不影响播放）', error);
    }
};
