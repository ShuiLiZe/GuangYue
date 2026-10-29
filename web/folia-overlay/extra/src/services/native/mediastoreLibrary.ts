/**
 * 广乐 Android 的「本地音乐」：用 MediaStore 喂饱 Folia 自己的本地库。
 *
 * Folia 的本地库是自持的（Dexie `local_music` 表 + FileSystem Access API 的目录/文件句柄）。
 * Android WebView 里没有 `showDirectoryPicker`，也拿不到用户选中的目录，所以要换掉的只是
 * **两处获得句柄的地方**，不是整套本地库逻辑：
 *
 *   1. `localMusicService.getImportDirectoryHandle()` —— 选目录。这里改成返回一个"虚拟根目录
 *      句柄"，它的 `values()` 异步遍历原生给的 MediaStore 目录树。
 *   2. `localMusicService.getAccessibleFileHandle()` 的恢复路径 —— 重新拿到文件句柄。这里改成
 *      返回一个虚拟文件句柄，`getFile()` 从 `/local/audio/…` 中转读字节。
 *
 * 只要这两个函数的产出满足 Folia 内部使用的 FileSystem Access API 子集
 * （`name` / `kind` / `values()` / `getDirectoryHandle()` / `queryPermission()` / `getFile()`），
 * 上传的导入流程、增量比对、封面提取、歌词侧车、拖拽排序全部按原逻辑跑，我们一行都不用改。
 */
const MEDIASTORE_ROOT = 'guangyue-mediastore';
import { call, hasNativeBridge } from './bridge';

interface NativeSong {
    id: string;
    title: string;
    artist: string;
    album: string;
    duration: number;
    artwork: string;
    contentUri: string;
    sizeBytes: number;
    mimeType: string;
    fileName: string;
    folder: string;
}

interface NativeNode {
    name: string;
    relativePath: string;
    isFolder: boolean;
    song?: NativeSong;
    children?: NativeNode[];
}

const RELAY_ROOT = 'https://appassets.androidplatform.net';

/** 目录树缓存：一次扫描给整个导入流程用，避免每个文件都重新问一遍原生。 */
let treeCache: { at: number; tree: NativeNode } | null = null;
const TREE_TTL_MS = 30_000;

const loadTree = async (): Promise<NativeNode | null> => {
    if (treeCache && Date.now() - treeCache.at < TREE_TTL_MS) return treeCache.tree;
    if (!hasNativeBridge()) return null;
    const answer = await call<{ tree: NativeNode }>('local.tree', { root: MEDIASTORE_ROOT });
    if (!answer?.tree) return null;
    treeCache = { at: Date.now(), tree: answer.tree };
    return answer.tree;
};

const findByPath = (node: NativeNode, relativePath: string[]): NativeNode | null => {
    if (relativePath.length === 0) return node;
    const [head, ...rest] = relativePath;
    const next = (node.children ?? []).find((child) => child.name === head);
    return next ? findByPath(next, rest) : null;
};

/**
 * 一个最小的 FileSystemFileHandle：`getFile()` 从中转地址取字节。
 *
 * Folia 拿到它之后会调用 `getFile()` → `.arrayBuffer()` / `.slice()` / 读元数据，所以这里
 * 直接把中转地址 fetch 成一个真正的 `File` 对象返回，行为与浏览器原生的句柄一致。
 */
class MediastoreFileHandle {
    readonly kind = 'file' as const;

    constructor(readonly name: string, private readonly song: NativeSong) { }

    async queryPermission(): Promise<PermissionState> {
        return 'granted';
    }

    async requestPermission(): Promise<PermissionState> {
        return 'granted';
    }

    async isSameEntry(other: unknown): Promise<boolean> {
        return other instanceof MediastoreFileHandle && other.song.id === this.song.id;
    }

    async getFile(): Promise<File> {
        const url = `${RELAY_ROOT}/local/audio/${encodeURIComponent(this.song.id)}`;
        const response = await fetch(url);
        if (!response.ok) throw new DOMException(`读取本地音频失败：${this.song.fileName}`, 'NotReadableError');
        const blob = await response.blob();
        return new File([blob], this.song.fileName, {
            type: this.song.mimeType || blob.type || 'audio/mpeg',
            lastModified: 0,
        });
    }
}

/** 一个最小的 FileSystemDirectoryHandle：`values()` 是导入流程唯一需要的遍历入口。 */
class MediastoreDirectoryHandle {
    readonly kind = 'directory' as const;

    constructor(readonly name: string, private readonly node: NativeNode) { }

    async queryPermission(): Promise<PermissionState> {
        return 'granted';
    }

    async requestPermission(): Promise<PermissionState> {
        return 'granted';
    }

    async isSameEntry(other: unknown): Promise<boolean> {
        return other instanceof MediastoreDirectoryHandle && other.node.relativePath === this.node.relativePath;
    }

    async getDirectoryHandle(name: string): Promise<MediastoreDirectoryHandle> {
        const child = (this.node.children ?? []).find((node) => node.isFolder && node.name === name);
        if (!child) throw new DOMException(`目录不存在：${name}`, 'NotFoundError');
        return new MediastoreDirectoryHandle(child.name, child);
    }

    async getFileHandle(name: string): Promise<MediastoreFileHandle> {
        const child = (this.node.children ?? []).find((node) => !node.isFolder && node.name === name);
        if (!child?.song) throw new DOMException(`文件不存在：${name}`, 'NotFoundError');
        return new MediastoreFileHandle(child.name, child.song);
    }

    async *values(): AsyncGenerator<MediastoreDirectoryHandle | MediastoreFileHandle> {
        for (const child of this.node.children ?? []) {
            if (child.isFolder) {
                yield new MediastoreDirectoryHandle(child.name, child);
            } else if (child.song) {
                yield new MediastoreFileHandle(child.name, child.song);
            }
        }
    }

    async *entries(): AsyncGenerator<[string, MediastoreDirectoryHandle | MediastoreFileHandle]> {
        for await (const handle of this.values()) yield [handle.name, handle];
    }
}

export const isMediastoreLibrary = (): boolean => hasNativeBridge();

/**
 * 给 `getImportDirectoryHandle()` 的替代品：返回 MediaStore 的虚拟根目录句柄。
 * 名字固定为 `MEDIASTORE_ROOT`，这样 Folia 的 rootFolderName 与重复导入判定都稳定。
 */
export const openMediastoreRoot = async (): Promise<MediastoreDirectoryHandle | null> => {
    const tree = await loadTree();
    return tree ? new MediastoreDirectoryHandle(MEDIASTORE_ROOT, tree) : null;
};

/**
 * 给恢复路径用：按 Folia 记下的相对路径重新拿到文件句柄。
 * `relativePath` 形如 `手机存储/专辑名/歌.mp3`（第一段是 rootFolderName）。
 */
export const recoverMediastoreFile = async (relativePath: string): Promise<MediastoreFileHandle | null> => {
    const tree = await loadTree();
    if (!tree) return null;
    const segments = relativePath.split('/').filter(Boolean);
    // 第一段是根目录名，可能被 Folia 加过后缀，这里按目录名匹配而不是按字符串相等。
    const withoutRoot = segments[0] === tree.name ? segments.slice(1) : segments.slice(1);
    const node = findByPath(tree, withoutRoot);
    return node?.song ? new MediastoreFileHandle(node.name, node.song) : null;
};

export type { MediastoreDirectoryHandle, MediastoreFileHandle };
