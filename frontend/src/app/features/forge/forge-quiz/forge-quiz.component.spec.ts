import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ForgeApi } from '../../../core/forge/forge.api';
import { ForgeOverview, ForgeQuestionnaire, ForgeStartPayload } from '../../../core/forge/forge.types';
import { ForgeQuizComponent } from './forge-quiz.component';

const QUESTIONNAIRE: ForgeQuestionnaire = {
  lengthDays: 66,
  questions: [
    {
      code: 'MOVEMENT',
      prompt: 'How often do you exercise?',
      hint: null,
      maxPicks: 1,
      options: [
        { code: 'OFTEN', label: 'Often' },
        { code: 'RARELY', label: 'Rarely' },
      ],
    },
    {
      code: 'PRIORITY',
      prompt: 'Which areas matter most?',
      hint: 'Choose up to two.',
      maxPicks: 2,
      options: [
        { code: 'FITNESS', label: 'Fitness' },
        { code: 'FOCUS', label: 'Focus' },
        { code: 'SOCIAL', label: 'Social' },
      ],
    },
  ],
};

const OVERVIEW = { today: '2026-03-01' } as ForgeOverview;

function click(root: HTMLElement, text: string): void {
  const button = Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim().startsWith(text));
  if (!button) {
    throw new Error(`No button "${text}"`);
  }
  button.click();
}

describe('ForgeQuizComponent', () => {
  let sent: ForgeStartPayload | null;

  beforeEach(() => {
    sent = null;
    TestBed.configureTestingModule({
      imports: [ForgeQuizComponent],
      providers: [
        {
          provide: ForgeApi,
          useValue: {
            questionnaire: () => of(QUESTIONNAIRE),
            start: (payload: ForgeStartPayload) => {
              sent = payload;
              return of(OVERVIEW);
            },
          },
        },
      ],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(ForgeQuizComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('walks the questions, caps multi-picks, and sends the answers with the mode', async () => {
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;
    let started: ForgeOverview | null = null;
    fixture.componentInstance.started.subscribe((o) => (started = o));

    expect(root.textContent).toContain('Question 1 of 2');
    click(root, 'Rarely');
    fixture.detectChanges();

    // Three taps on a pick-two question keeps the two most recent.
    expect(root.textContent).toContain('Which areas matter most?');
    click(root, 'Fitness');
    click(root, 'Focus');
    click(root, 'Social');
    fixture.detectChanges();
    click(root, 'Continue');
    fixture.detectChanges();

    expect(root.textContent).toContain('How hard should it push you?');
    click(root, 'Hard');
    fixture.detectChanges();
    click(root, 'Begin day 1');
    await fixture.whenStable();

    expect(sent).toEqual({
      answers: { MOVEMENT: ['RARELY'], PRIORITY: ['FOCUS', 'SOCIAL'] },
      mode: 'HARD',
    });
    expect(started).toBe(OVERVIEW);
  });

  it('will not begin until a mode is chosen', async () => {
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;
    click(root, 'Often');
    fixture.detectChanges();
    click(root, 'Fitness');
    fixture.detectChanges();
    click(root, 'Continue');
    fixture.detectChanges();

    const begin = Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Begin day 1'));
    expect(begin?.disabled).toBe(true);
  });
});
