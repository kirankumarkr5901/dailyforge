import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionStore } from '../auth/session.store';
import { FeatureCode } from './features';

/** A screen whose feature is switched off redirects to Home instead of opening. */
export function featureGuard(code: FeatureCode): CanActivateFn {
  return () => (inject(SessionStore).featureEnabled(code) ? true : inject(Router).parseUrl('/home'));
}
