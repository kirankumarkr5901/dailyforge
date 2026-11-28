import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { LucideAngularModule, MessageCircle, Send } from 'lucide-angular';

import { AiStatusService } from '../../../core/ai/ai-status.service';
import { AssistantAction, AssistantApi, ChatMessage } from '../../../core/assistant/assistant.api';
import { SessionStore } from '../../../core/auth/session.store';
import { SyncStore } from '../../../core/sync/sync.store';
import { DfButtonComponent } from '../../ui/df-button/df-button.component';
import { DfSheetComponent } from '../../ui/df-sheet/df-sheet.component';

/** One line of the chat: a message, and for the assistant, the actions it proposed. */
interface Turn {
  role: 'user' | 'assistant';
  text: string;
  actions?: {
    action: AssistantAction;
    state: 'open' | 'busy' | 'done' | 'skipped';
    result?: string;
  }[];
}

/**
 * "Ask DailyForge": a button on every screen opening a chat about the user's own data.
 * When asked to do something (log food or water, mark a habit, log sets, a run or a
 * weigh-in, extend a goal), it proposes actions; each one happens only on Confirm, and
 * the screens refresh afterwards.
 */
@Component({
  selector: 'df-assistant',
  imports: [FormsModule, LucideAngularModule, DfButtonComponent, DfSheetComponent],
  templateUrl: './assistant.component.html',
  styleUrl: './assistant.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantComponent {
  private readonly api = inject(AssistantApi);
  private readonly sync = inject(SyncStore);
  protected readonly session = inject(SessionStore);
  protected readonly ai = inject(AiStatusService);

  protected readonly chatIcon = MessageCircle;
  protected readonly sendIcon = Send;

  protected readonly open = signal(false);
  protected readonly turns = signal<Turn[]>([]);
  protected readonly text = signal('');
  protected readonly thinking = signal(false);

  private readonly log = viewChild<ElementRef<HTMLElement>>('log');

  protected readonly examples = [
    'How many sets of chest did I do this week?',
    'Log 2 roti and dal for lunch',
    'Add 500 ml water',
    'When did I last hit my protein target?',
  ];

  constructor() {
    effect(() => {
      if (this.session.isAuthenticated()) void this.ai.load();
    });
  }

  protected async send(text = this.text()): Promise<void> {
    const question = text.trim();
    if (!question || this.thinking()) return;
    this.text.set('');
    this.turns.update((t) => [...t, { role: 'user', text: question }]);
    this.thinking.set(true);
    this.scroll();
    try {
      const history: ChatMessage[] = this.turns().map((t) => ({ role: t.role, text: t.text }));
      const reply = await firstValueFrom(this.api.ask(history));
      this.turns.update((t) => [
        ...t,
        {
          role: 'assistant',
          text: reply.answer,
          actions: reply.actions.map((action) => ({ action, state: 'open' as const })),
        },
      ]);
    } catch (e) {
      this.turns.update((t) => [
        ...t,
        { role: 'assistant', text: errorMessage(e) ?? 'I could not answer just now. Try again.' },
      ]);
    } finally {
      this.thinking.set(false);
      this.scroll();
    }
  }

  protected async confirm(turnIndex: number, actionIndex: number): Promise<void> {
    const item = this.turns()[turnIndex]?.actions?.[actionIndex];
    if (!item || item.state !== 'open') return;
    this.setAction(turnIndex, actionIndex, { state: 'busy' });
    try {
      const { message } = await firstValueFrom(this.api.act(item.action));
      this.setAction(turnIndex, actionIndex, { state: 'done', result: message });
      this.sync.request('manual');
    } catch (e) {
      this.setAction(turnIndex, actionIndex, {
        state: 'open',
        result: errorMessage(e) ?? 'That did not work. Try again.',
      });
    }
  }

  protected skip(turnIndex: number, actionIndex: number): void {
    this.setAction(turnIndex, actionIndex, { state: 'skipped' });
  }

  private setAction(
    turnIndex: number,
    actionIndex: number,
    change: Partial<NonNullable<Turn['actions']>[number]>,
  ): void {
    this.turns.update((turns) =>
      turns.map((t, i) =>
        i === turnIndex && t.actions
          ? {
              ...t,
              actions: t.actions.map((a, j) => (j === actionIndex ? { ...a, ...change } : a)),
            }
          : t,
      ),
    );
  }

  protected clear(): void {
    this.turns.set([]);
  }

  private scroll(): void {
    setTimeout(() => {
      const el = this.log()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }
}

function errorMessage(e: unknown): string | null {
  return e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
    ? e.error.message
    : null;
}
