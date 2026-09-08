import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DfToastHostComponent } from './shared/ui/df-toast/df-toast-host.component';

/**
 * The app shell. At M0 it is deliberately thin — a routed outlet and the toast host.
 * The header, hamburger and bottom navigation arrive with identity at M1.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, DfToastHostComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
