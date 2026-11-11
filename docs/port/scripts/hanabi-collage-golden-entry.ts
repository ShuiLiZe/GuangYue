import { planShotCollage, pickCollageKeyword, chooseCollageRecipe, CANDY } from '../../folia/src/components/visualizer/hanabi/collage/collagePlan';
import { createShotCollage } from '../../folia/src/components/visualizer/hanabi/collage/collageViews';
import { createRng } from '../../folia/src/components/visualizer/hanabi/hanabiRandom';
(globalThis as any).HB = { planShotCollage, pickCollageKeyword, chooseCollageRecipe, createShotCollage, createRng, CANDY };
