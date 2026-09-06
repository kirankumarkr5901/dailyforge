/**
 * The string table.
 *
 * English-only for now, but nothing in a template is a literal. Adding a second language
 * later is then a data problem rather than a rewrite of every component.
 *
 * Copy rules (spec §9.6): sentence case, plain language, no scolding. Controls name
 * their action. Errors name the problem and the recovery. Empty states propose a next
 * step rather than shrugging.
 */
export const STRINGS = {
  'app.name': 'DailyForge',

  'action.log': 'Log',
  'action.undo': 'Undo',
  'action.save': 'Save',
  'action.cancel': 'Cancel',
  'action.close': 'Close',
  'action.retry': 'Try again',
  'action.previous': 'Previous day',
  'action.next': 'Next day',
  'action.today': 'Today',
  'action.increase': 'Increase',
  'action.decrease': 'Decrease',

  'score.label': 'Score',
  'score.points': '{count} points',
  'score.delta.gain': '+{amount}',
  'score.delta.loss': '{amount}',

  'state.loading': 'Loading',
  'state.pending': 'Pending',
  'state.saving': 'Saving',

  'theme.label': 'Theme',
  'theme.system': 'System',
  'theme.light': 'Light',
  'theme.dark': 'Dark',

  'motion.label': 'Motion',
  'motion.system': 'System',
  'motion.full': 'Full',
  'motion.reduced': 'Reduced',

  'error.generic.title': 'That did not save',
  'error.generic.body': 'Your input is still here. Check your connection and try again.',
  'error.offline.title': 'You are offline',
  'error.offline.body': 'Logs are queued and will sync when you have signal.',
  'error.required': 'This one is required.',
  'error.outOfRange': 'That looks out of range. Check the value.',

  'empty.generic.title': 'Nothing here yet',
  'empty.generic.body': 'Once you log something it shows up here.',

  'nav.home': 'Home',
  'nav.workout': 'Workout',
  'nav.habits': 'Habits',
  'nav.run': 'Run',
  'nav.more': 'More',
  'nav.goals': 'Goals',
  'nav.jobs': 'Jobs',
  'nav.body': 'Body',
  'nav.rewards': 'Rewards',
  'nav.settings': 'Settings',
  'nav.menu': 'Menu',
  'nav.close': 'Close menu',

  'auth.signIn': 'Sign in',
  'auth.signOut': 'Sign out',
  'auth.createAccount': 'Create an account',
  'auth.email': 'Email',
  'auth.password': 'Password',
  'auth.displayName': 'Name',
  'auth.google': 'Continue with Google',
  'auth.or': 'or',
  'auth.haveAccount': 'Already have an account? Sign in',
  'auth.noAccount': 'New here? Create an account',
  'auth.passwordHint': 'At least 10 characters.',
  'auth.reason.manual': 'Sign in to start your own log.',
  'auth.reason.write': 'Sign in to save that. Nothing you typed is lost.',
  'auth.reason.expired': 'Your session ended. Sign in to carry on.',
  'auth.anonymous.title': 'You are browsing as a guest',
  'auth.anonymous.body': 'Sign in to start logging. Everything you log becomes points.',

  'settings.title': 'Settings',
  'settings.appearance': 'Appearance',
  'settings.theme': 'Theme',
  'settings.motion': 'Motion',
  'settings.units': 'Units',
  'settings.metric': 'Metric (kg, km)',
  'settings.imperial': 'Imperial (lb, mi)',
  'settings.timeZone': 'Time zone',
  'settings.timeZoneHint': 'Streaks and daily bonuses use this to decide when your day ends.',
  'settings.account': 'Account',
  'settings.saved': 'Saved.',

  'coming.title': 'Not built yet',
  'coming.body': 'This screen arrives in a later milestone. The shell around it is real.',

  'dev.ui.title': 'Design system',
  'dev.ui.subtitle':
    'Every primitive, every state, both themes. Almost nothing here is warm, because almost nothing here was earned.',
} as const;

export type StringKey = keyof typeof STRINGS;
