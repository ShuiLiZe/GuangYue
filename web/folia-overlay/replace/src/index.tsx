import { Buffer } from 'buffer';
import { installGlobalVisualizerFrameRateLimiter } from './utils/frameRateLimiter';
import { installConsoleLogCapture } from './utils/consoleLogBuffer';
import { installDebugModule } from './services/debug/debugModule';
import { installMemorySampleFeed } from './services/debug/memorySamples';
import { hasNativeBridge } from './services/native/bridge';
import { installMobileControls } from './services/native/installMobileControls';
import { installNativePlaybackShim } from './services/native/installNativePlaybackShim';
import './styles/mobile.css';

// @ts-ignore
globalThis.Buffer = Buffer;

// 广乐：Android WebView 里的启动序列。
//
// 顺序很要紧：影子播放器必须在 React 第一次渲染出 <audio> 之前装好（否则 automix 的第一副牌
// 会以插件直链直接 play()，在 WebView 里必然被防盗链挡掉，频谱与“已播放”判定就都断了）；
// 触摸手势则必须在任何面板挂载之前登记，才能抢到捕获阶段。
if (hasNativeBridge()) {
  installNativePlaybackShim();
  installMobileControls();
  console.info('[GuangYue] 原生桥就绪，播放交给 Media3，界面为 Folia 原版');
}

// First, so the debug overlay's console tab has the startup lines too - they are where a failure
// to reach a library or restore a session shows up.
installConsoleLogCapture();
// Right after it, so the startup lines reach the runtime log file too and not only the in-memory
// buffer. Both no-op off Electron. See services/debug/debugModule.ts.
installDebugModule();
installMemorySampleFeed();
installGlobalVisualizerFrameRateLimiter();

void import('./bootstrap');
