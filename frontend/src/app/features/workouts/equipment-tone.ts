import { DfEquipment } from '../../shared/ui/df-chip/df-chip.component';
import { Equipment } from '../../core/workouts/workouts.types';

/** Maps the backend's equipment enum onto the chip's colour-coded tone (spec §9.2). */
export function equipmentTone(equipment: Equipment): DfEquipment {
  switch (equipment) {
    case 'BARBELL':
      return 'barbell';
    case 'DUMBBELL':
      return 'dumbbell';
    case 'MACHINE':
      return 'machine';
    case 'CABLE':
      return 'cable';
    case 'BODYWEIGHT':
      return 'bodyweight';
    default:
      return 'cardio';
  }
}

export function equipmentLabel(equipment: Equipment): string {
  switch (equipment) {
    case 'BARBELL':
      return 'Barbell';
    case 'DUMBBELL':
      return 'Dumbbell';
    case 'MACHINE':
      return 'Machine';
    case 'CABLE':
      return 'Cable';
    case 'BODYWEIGHT':
      return 'Bodyweight';
    default:
      return 'Cardio';
  }
}
