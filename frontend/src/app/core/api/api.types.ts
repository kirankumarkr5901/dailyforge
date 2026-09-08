/** Shapes returned by the API. Mirrors the backend's identity DTOs. */

export interface ApiError {
  code: ApiErrorCode;
  message: string;
  field: string | null;
  details: Record<string, unknown>;
}

/**
 * The stable enum the backend promises (spec §4.7). The frontend maps a code to copy;
 * it never shows the server's message directly, so a wording change on either side
 * cannot break the other.
 */
export type ApiErrorCode =
  | 'AUTH_REQUIRED'
  | 'AUTH_INVALID_CREDENTIALS'
  | 'AUTH_TOKEN_EXPIRED'
  | 'AUTH_EMAIL_TAKEN'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'VALIDATION_FAILED'
  | 'HABIT_LOCKED'
  | 'ENTRY_LOCKED'
  | 'OUT_OF_RANGE'
  | 'DAILY_CAP_REACHED'
  | 'IDEMPOTENCY_CONFLICT'
  | 'CONFLICT'
  | 'INTERNAL_ERROR'
  | 'NETWORK';

export interface UserSettings {
  timeZone: string;
  unitSystem: 'METRIC' | 'IMPERIAL';
  theme: 'SYSTEM' | 'LIGHT' | 'DARK';
  weekStart: string;
  commitmentBonus: number;
  heightCm: number | null;
  reminderTime: string | null;
  onboardingCompletedAt: string | null;
}

export interface CurrentUser {
  id: string;
  email: string;
  displayName: string;
  hasPassword: boolean;
  googleLinked: boolean;
  settings: UserSettings;
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  refreshToken: string;
  user: CurrentUser;
}

/** Which sign-in methods this deployment actually offers. */
export interface AuthCapabilities {
  passwordEnabled: boolean;
  googleEnabled: boolean;
}

export interface SignupPayload {
  email: string;
  password: string;
  displayName: string;
  timeZone: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface UpdateSettingsPayload {
  timeZone?: string;
  unitSystem?: string;
  theme?: string;
  commitmentBonus?: number;
  heightCm?: number;
  reminderTime?: string;
  onboardingCompleted?: boolean;
}
