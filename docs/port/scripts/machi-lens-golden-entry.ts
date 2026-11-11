import * as L from '/workspace/work/folia-src/src/components/visualizer/machi/engine/lens';
import * as C from '/workspace/work/folia-src/src/components/visualizer/machi/engine/clock';
import { resolveMachiDrift } from '/workspace/work/folia-src/src/components/visualizer/machi/engine/camera';
(globalThis as any).MCL = { ...L, ...C, resolveMachiDrift };
