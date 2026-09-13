export const enUS = {
  common: {
    retry: 'Retry',
    backHome: 'Back to home',
    language: 'Language',
    close: 'Close',
    prevPage: 'Previous page',
    nextPage: 'Next page',
    pageStatus: 'Page {{current}} of {{total}}',
    required: 'Required',
  },
  nav: {
    home: 'Home',
    models: 'Models',
    docs: 'Docs',
    login: 'Sign in',
    register: 'Sign up',
    dashboard: 'Dashboard',
    logout: 'Sign out',
    menu: 'Menu',
    closeMenu: 'Close menu',
  },
  pages: {
    home: {
      title: 'Self-hosted language model gateway',
      subtitle: 'Welcome to {{siteName}}',
      statusNote: 'Model and auth capabilities land in stages; only base entry points exist for now.',
    },
    models: {
      title: 'Models',
      empty: 'Model data is not connected yet; real models will appear here once delivered.',
    },
    docs: {
      title: 'Documentation',
      empty: 'Guides will land in a later stage; only the entry frame exists here.',
    },
    login: {
      title: 'Sign in',
      submit: 'Sign in',
    },
    register: {
      title: 'Sign up',
      submit: 'Sign up',
      closed: 'Registration is closed in this environment. Ask an administrator to create an account.',
    },
    auth: {
      username: 'Username',
      password: 'Password',
      confirmPassword: 'Confirm password',
      submitting: 'Submitting…',
      invalidCredentials: 'Invalid username or password.',
      passwordMismatch: 'The passwords do not match.',
      requestFailed: 'The request failed. Please try again.',
    },
    dashboard: {
      title: 'Dashboard',
      empty: 'Console data will be connected in a later stage.',
    },
    notFound: {
      title: 'Page not found',
      description: 'No page matches this address. Please go back home.',
    },
  },
  states: {
    loading: 'Loading…',
    empty: 'No data yet',
    startup: {
      loading: 'Starting up, reading runtime config…',
      error: 'Runtime config failed to load. Check the backend and retry.',
    },
    error: {
      title: 'Request failed',
    },
    forbidden: {
      title: 'Forbidden',
      description: 'Your current identity cannot access this page.',
    },
  },
  errors: {
    network: 'Network request failed. Please retry.',
    invalidResponse: 'Server response could not be parsed.',
    requestId: 'Request ID: {{requestId}}',
  },
};
