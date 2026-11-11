import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { ROLE_LABELS } from '../../shared/labels';
import { AuthService } from '../auth/auth.service';
import { RealtimeService } from '../realtime/realtime.service';

/** Layout of the signed-in area: top bar, navigation and the routed page. */
@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule, MatMenuModule, MatTooltipModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly realtime = inject(RealtimeService);
  protected readonly roleLabels = ROLE_LABELS;

  /** Narrow screens: the navigation becomes a drawer opened from the menu button. */
  protected readonly compact = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected readonly links = [
    { path: '/board', icon: 'view_kanban', label: 'Ordres de travail' },
    { path: '/equipment', icon: 'precision_manufacturing', label: 'Parc machines' },
    { path: '/dashboard', icon: 'monitoring', label: 'Tableau de bord' },
  ];
}
