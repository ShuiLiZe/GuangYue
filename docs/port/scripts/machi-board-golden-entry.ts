// 町景金标入口：../../folia 指向 Folia v0.7.25 源码；rolldown machi-board-golden-entry.ts -f cjs -o mc.js，再 node machi-board-golden-gen.js > machi_board_golden.txt。
import * as D from '../../folia/src/components/visualizer/machi/engine/director';
import * as B from '../../folia/src/components/visualizer/machi/engine/boardLayout';
(globalThis as any).MC = { ...D, ...B };
