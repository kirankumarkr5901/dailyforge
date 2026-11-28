import { MealPhotoDish } from '../../../core/nutrition/protein.types';
import { scaleDish } from './scale-dish';

const biryani: MealPhotoDish = {
  name: 'Chicken biryani',
  portionG: 300,
  proteinG: 24,
  kcal: 540,
  fatG: 18,
  carbsG: 66,
  fiberG: 3,
  sugarG: 4,
};

describe('scaling a dish from a meal photo', () => {
  it('keeps the estimate at the estimated portion', () => {
    expect(scaleDish(biryani, '  Chicken biryani ', 300)).toEqual({
      foodName: 'Chicken biryani',
      grams: 24,
      kcal: 540,
      fatG: 18,
      carbsG: 66,
      fiberG: 3,
      sugarG: 4,
    });
  });

  it('scales every value with the portion, grams to one decimal and calories whole', () => {
    const half = scaleDish(biryani, 'Biryani', 150);
    expect(half.grams).toBe(12);
    expect(half.kcal).toBe(270);
    expect(half.fiberG).toBe(1.5); // kept, not rounded to 2
    expect(scaleDish(biryani, 'Biryani', 100).grams).toBe(8);
    expect(scaleDish(biryani, 'Biryani', 110).grams).toBe(8.8);
  });

  it('stays inside the per-entry limits', () => {
    expect(scaleDish(biryani, 'Biryani', 3000).kcal).toBe(3000);
    expect(scaleDish(biryani, 'Biryani', 3000).grams).toBe(200);
  });

  it('leaves values alone when the photo gave no portion', () => {
    expect(scaleDish({ ...biryani, portionG: 0 }, 'Biryani', 100).kcal).toBe(540);
  });
});
